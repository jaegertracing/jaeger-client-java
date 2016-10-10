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
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class CallableTest {
  TraceContext traceContext;
  Span span;

  @Before
  public void setUp() {
    span = mock(Span.class);
    traceContext = mock(TraceContext.class);
    when(traceContext.getCurrentSpan()).thenReturn(span);
    when(traceContext.pop()).thenReturn(span);
    when(traceContext.isEmpty()).thenReturn(false);
  }

  @Test
  public void testInstrumentedCallable() throws Exception {
    Callable wrappedCallable = mock(Callable.class);
    when(wrappedCallable.call()).thenReturn(span);

    Callable<Span> jaegerCallable = new Callable<>(wrappedCallable, traceContext);

    jaegerCallable.call();

    verify(traceContext, times(1)).isEmpty();
    verify(traceContext, times(1)).push(span);
    verify(traceContext, times(1)).getCurrentSpan();
    verify(traceContext, times(1)).pop();
    verify(wrappedCallable, times(1)).call();
    verifyNoMoreInteractions(traceContext, wrappedCallable);
  }

  @Test
  public void testInstrumentedCallableNoCurrentSpan() throws Exception {
    Callable wrappedCallable = mock(Callable.class);
    when(wrappedCallable.call()).thenReturn(span);
    when(traceContext.isEmpty()).thenReturn(true);

    Callable<Span> jaegerCallable = new Callable<>(wrappedCallable, traceContext);

    jaegerCallable.call();

    verify(traceContext, times(1)).isEmpty();
    verify(wrappedCallable, times(1)).call();
    verifyNoMoreInteractions(traceContext, wrappedCallable);
  }
}
