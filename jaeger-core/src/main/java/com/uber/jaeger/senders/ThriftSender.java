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

package com.uber.jaeger.senders;

import com.uber.jaeger.Span;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.reporters.protocols.JaegerThriftSpanConverter;
import com.uber.jaeger.thrift.reporters.protocols.ThriftUdpTransport;
import com.uber.jaeger.thrift.senders.ThriftSenderBase;
import com.uber.jaeger.thriftjava.Process;
import java.util.ArrayList;
import java.util.List;

import lombok.ToString;

@ToString(exclude = {"spanBuffer"})
public abstract class ThriftSender extends ThriftSenderBase implements Sender {

  private Process process;
  private int processBytesSize;
  private List<com.uber.jaeger.thriftjava.Span> spanBuffer;
  private int byteBufferSize;

  /**
   * @param protocolType protocol type (compact or binary)
   * @param maxPacketSize if 0 it will use default value {@value ThriftUdpTransport#MAX_PACKET_SIZE}
   */
  public ThriftSender(ProtocolType protocolType, int maxPacketSize) {
    super(protocolType, maxPacketSize);

    spanBuffer = new ArrayList<com.uber.jaeger.thriftjava.Span>();
  }

  @Override
  public int append(Span span) throws SenderException {
    if (process == null) {
      try {
        process = new Process(span.getTracer().getServiceName());
        process.setTags(JaegerThriftSpanConverter.buildTags(span.getTracer().tags()));
        processBytesSize = getSize(process);
        byteBufferSize += processBytesSize;
      } catch (Exception e) {
        throw new SenderException("ThriftSender failed writing Process to memory buffer.", e, 1);
      }
    }

    com.uber.jaeger.thriftjava.Span thriftSpan = JaegerThriftSpanConverter.convertSpan(span);
    int spanSize = 0;
    try {
      spanSize = getSize(thriftSpan);
    } catch (Exception e) {
      throw new SenderException("ThriftSender failed writing Span to memory buffer.", e, 1);
    }
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

  public abstract void send(Process process, List<com.uber.jaeger.thriftjava.Span> spans) throws Exception;

  @Override
  public int flush() throws SenderException {
    if (spanBuffer.isEmpty()) {
      return 0;
    }

    int n = spanBuffer.size();
    try {
      send(process, spanBuffer);
    } catch (Exception e) {
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
