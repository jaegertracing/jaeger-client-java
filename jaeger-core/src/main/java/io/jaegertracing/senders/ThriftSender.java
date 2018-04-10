/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.jaegertracing.senders;

import io.jaegertracing.Span;
import io.jaegertracing.exceptions.SenderException;
import io.jaegertracing.reporters.protocols.JaegerThriftSpanConverter;
import io.jaegertracing.thrift.reporters.protocols.ThriftUdpTransport;
import io.jaegertracing.thrift.senders.ThriftSenderBase;
import io.jaegertracing.thriftjava.Process;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;

@ToString(exclude = {"spanBuffer"})
public abstract class ThriftSender extends ThriftSenderBase implements Sender {

  private Process process;
  private int processBytesSize;
  private List<io.jaegertracing.thriftjava.Span> spanBuffer;
  private int byteBufferSize;

  /**
   * @param protocolType protocol type (compact or binary)
   * @param maxPacketSize if 0 it will use default value {@value ThriftUdpTransport#MAX_PACKET_SIZE}
   */
  public ThriftSender(ProtocolType protocolType, int maxPacketSize) {
    super(protocolType, maxPacketSize);

    spanBuffer = new ArrayList<io.jaegertracing.thriftjava.Span>();
  }

  @Override
  public int append(Span span) throws SenderException {
    if (process == null) {
      process = new Process(span.getTracer().getServiceName());
      process.setTags(JaegerThriftSpanConverter.buildTags(span.getTracer().tags()));
      processBytesSize = calculateProcessSize(process);
      byteBufferSize += processBytesSize;
    }

    io.jaegertracing.thriftjava.Span thriftSpan = JaegerThriftSpanConverter.convertSpan(span);
    int spanSize = calculateSpanSize(thriftSpan);
    if (spanSize > getMaxSpanBytes()) {
      throw new SenderException(String.format("ThriftSender received a span that was too large, size = %d, max = %d",
          spanSize, getMaxSpanBytes()), null, 1);
    }

    byteBufferSize += spanSize;
    if (byteBufferSize <= getMaxSpanBytes()) {
      spanBuffer.add(thriftSpan);
      if (byteBufferSize < getMaxSpanBytes()) {
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

  protected int calculateProcessSize(Process proc) throws SenderException {
    try {
      return getSize(proc);
    } catch (Exception e) {
      throw new SenderException("ThriftSender failed writing Process to memory buffer.", e, 1);
    }
  }

  protected int calculateSpanSize(io.jaegertracing.thriftjava.Span span) throws SenderException {
    try {
      return getSize(span);
    } catch (Exception e) {
      throw new SenderException("ThriftSender failed writing Span to memory buffer.", e, 1);
    }
  }

  public abstract void send(Process process, List<io.jaegertracing.thriftjava.Span> spans) throws SenderException;

  @Override
  public int flush() throws SenderException {
    if (spanBuffer.isEmpty()) {
      return 0;
    }

    int n = spanBuffer.size();
    try {
      send(process, spanBuffer);
    } catch (SenderException e) {
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

}
