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

package com.uber.jaeger.context;

import io.opentracing.Span;

public class Callable<T> implements java.util.concurrent.Callable<T> {
  private final java.util.concurrent.Callable<T> wrappedCallable;
  private final TraceContext traceContext;
  private final Span currentSpan;

  public Callable(java.util.concurrent.Callable<T> wrappedCallable, TraceContext traceContext) {
    this.wrappedCallable = wrappedCallable;
    this.traceContext = traceContext;
    if (!traceContext.isEmpty()) {
      this.currentSpan = traceContext.getCurrentSpan();
    } else {
      this.currentSpan = null;
    }
  }

  @Override
  public T call() throws Exception {
    if (currentSpan != null) {
      traceContext.push(currentSpan);
    }

    try {
      return wrappedCallable.call();
    } finally {
      if (currentSpan != null) {
        traceContext.pop();
      }
    }
  }
}
