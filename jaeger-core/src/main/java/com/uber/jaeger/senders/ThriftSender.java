/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.uber.jaeger.senders;

import com.uber.jaeger.Span;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.reporters.protocols.JaegerThriftSpanConverter;
import com.uber.jaeger.reporters.protocols.ThriftUdpTransport;
import com.uber.jaeger.thriftjava.Process;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.AutoExpandingBufferWriteTransport;

@ToString(exclude = {"spanBuffer", "memoryTransport"})
public abstract class ThriftSender implements Sender {
  static final int EMIT_BATCH_OVERHEAD = 33;

  private Process process;
  private int processBytesSize;
  private List<com.uber.jaeger.thriftjava.Span> spanBuffer;
  private int byteBufferSize;
  private AutoExpandingBufferWriteTransport memoryTransport;

  protected final TProtocolFactory protocolFactory;
  private final int maxSpanBytes;

  public ThriftSender(TProtocolFactory protocolFactory, int maxPacketSize) {
    this.protocolFactory = protocolFactory;

    if (maxPacketSize == 0) {
      maxPacketSize = ThriftUdpTransport.MAX_PACKET_SIZE;
    }

    memoryTransport = new AutoExpandingBufferWriteTransport(maxPacketSize, 2);
    maxSpanBytes = maxPacketSize - EMIT_BATCH_OVERHEAD;
    spanBuffer = new ArrayList<com.uber.jaeger.thriftjava.Span>();
  }

  public abstract void send(Process process, List<com.uber.jaeger.thriftjava.Span> spans) throws TException;

  @Override
  public int append(Span span) throws SenderException {
    if (process == null) {
      process = new Process(span.getTracer().getServiceName());
      process.setTags(JaegerThriftSpanConverter.buildTags(span.getTracer().tags()));
      processBytesSize = getSizeOfSerializedThrift(process);
      byteBufferSize += processBytesSize;
    }

    com.uber.jaeger.thriftjava.Span thriftSpan = JaegerThriftSpanConverter.convertSpan(span);
    int spanSize = getSizeOfSerializedThrift(thriftSpan);
    if (spanSize > maxSpanBytes) {
      throw new SenderException(String.format("ThriftSender received a span that was too large, size = %d, max = %d",
          spanSize, maxSpanBytes), null, 1);
    }

    byteBufferSize += spanSize;
    if (byteBufferSize <= maxSpanBytes) {
      spanBuffer.add(thriftSpan);
      if (byteBufferSize < maxSpanBytes) {
        return 0;
      }
      return flush();
    }

    int n;
    try {
      n = flush();
    } catch (SenderException e) {
      // +1 for the span not submitted in the buffer above
      throw new SenderException(e.getMessage(), e.getCause(), e.getDroppedSpanCount() + 1);
    }

    spanBuffer.add(thriftSpan);
    byteBufferSize = processBytesSize + spanSize;
    return n;
  }

  @Override
  public int flush() throws SenderException {
    if (spanBuffer.isEmpty()) {
      return 0;
    }

    int n = spanBuffer.size();
    try {
      send(process, spanBuffer);
    } catch (TException e) {
      throw new SenderException("Failed to flush spans.", e, n);
    } finally {
      spanBuffer.clear();
      byteBufferSize = processBytesSize;
    }
    return n;
  }

  @Override
  public int close() throws SenderException {
    return flush();
  }

  int getSizeOfSerializedThrift(TBase thriftBase) throws SenderException {
    memoryTransport.reset();
    try {
      thriftBase.write(protocolFactory.getProtocol(memoryTransport));
    } catch (TException e) {
      throw new SenderException("ThriftSender failed writing to memory buffer.", e, 1);
    }
    return memoryTransport.getPos();
  }
}
