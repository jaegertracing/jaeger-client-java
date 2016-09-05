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
package com.uber.jaeger.senders.zipkin;

import com.twitter.zipkin.thriftjava.Span;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import zipkin.reporter.Encoder;
import zipkin.reporter.Encoding;

import java.io.ByteArrayOutputStream;

final class ThriftSpanEncoder implements Encoder<Span> {

  @Override public Encoding encoding() {
    return Encoding.THRIFT;
  }

  @Override public byte[] encode(Span thriftSpan) {
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

  final ThreadLocal<ReusableTBinaryProtocol> protocol = new ThreadLocal<ReusableTBinaryProtocol>() {
    protected ReusableTBinaryProtocol initialValue() {
      return new ReusableTBinaryProtocol(new ByteArrayOutputStream());
    }
  };
}
