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

public class RunnableTest {
    TraceContext traceContext;
    Span span;

    @Before
    public void setUp() {
        span = mock(Span.class);
        traceContext = mock(TraceContext.class);
        when(traceContext.getCurrentSpan()).thenReturn(span);
        when(traceContext.pop()).thenReturn(span);
    }

    @Test
    public void testIntrumentedRunnable() {
        Runnable wrappedRunnable = mock(Runnable.class);
        Runnable runnable = new Runnable(wrappedRunnable, traceContext);

        runnable.run();

        verify(traceContext, times(1)).push(span);
        verify(traceContext, times(1)).pop();
        verify(traceContext, times(1)).getCurrentSpan();
        verify(wrappedRunnable, times(1)).run();
        verifyNoMoreInteractions(traceContext, wrappedRunnable);
    }
}
