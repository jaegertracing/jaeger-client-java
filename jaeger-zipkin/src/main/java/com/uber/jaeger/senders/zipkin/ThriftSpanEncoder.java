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

package com.uber.jaeger.senders.zipkin;

import com.twitter.zipkin.thriftjava.Span;
import java.io.ByteArrayOutputStream;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import zipkin.reporter.Encoder;
import zipkin.reporter.Encoding;

final class ThriftSpanEncoder implements Encoder<Span> {

  @Override
  public Encoding encoding() {
    return Encoding.THRIFT;
  }

  @Override
  public byte[] encode(Span thriftSpan) {
    // Clear any old data.
    protocol.get().baos.reset();
    try {
      thriftSpan.write(protocol.get());
      return protocol.get().baos.toByteArray();
    } catch (TException e) {
      throw new AssertionError(e);
    }
  }

  static class ReusableTBinaryProtocol extends TBinaryProtocol {
    private final ByteArrayOutputStream baos;

    ReusableTBinaryProtocol(ByteArrayOutputStream baos) {
      super(new TIOStreamTransport(baos));
      this.baos = baos;
    }
  }

  final ThreadLocal<ReusableTBinaryProtocol> protocol =
      new ThreadLocal<ReusableTBinaryProtocol>() {
        @Override
        protected ReusableTBinaryProtocol initialValue() {
          return new ReusableTBinaryProtocol(new ByteArrayOutputStream());
        }
      };
}
