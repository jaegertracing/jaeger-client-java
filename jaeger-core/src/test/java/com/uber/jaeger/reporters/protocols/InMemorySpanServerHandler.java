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
package com.uber.jaeger.reporters.protocols;

import com.twitter.zipkin.thriftjava.Span;
import com.uber.jaeger.agent.thrift.Agent;
import com.uber.jaeger.thriftjava.Batch;
import org.apache.thrift.TException;

import java.util.ArrayList;
import java.util.List;

class InMemorySpanServerHandler implements Agent.Iface {
  private List<Span> spans;

  @Override
  public void emitZipkinBatch(List<Span> spans) throws TException {
    synchronized (this) {
      this.spans = spans;
    }
  }

  @Override
  public void emitBatch(Batch batch) throws TException {
    synchronized (this) {
      // TODO convert spans
      //this.spans = batch.spans;
    }
  }

  public List<Span> getSpans() {
    synchronized (this) {
      if (spans != null) {
        return new ArrayList<>(spans);
      }
      return new ArrayList<>();
    }
  }
}
