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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentracing.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class TracedExecutorServiceTest {

  TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;
  TracedExecutorService tracedExecutorService;
  ExecutorService wrappedExecutorService;
  Span span;
  TraceContext traceContext;
  List<java.util.concurrent.Callable<Span>> callableList;

  @Before
  public void setUp() {
    wrappedExecutorService = mock(ExecutorService.class);
    span = mock(Span.class);
    traceContext = mock(TraceContext.class);
    when(traceContext.pop()).thenReturn(span);
    when(traceContext.getCurrentSpan()).thenReturn(span);
    when(traceContext.isEmpty()).thenReturn(false);
    tracedExecutorService = new TracedExecutorService(wrappedExecutorService, traceContext);
    callableList =
        new ArrayList<java.util.concurrent.Callable<Span>>() {
          {
            add(mock(java.util.concurrent.Callable.class));
            add(mock(java.util.concurrent.Callable.class));
          }
        };
  }

  @Test
  public void testShutdown() {
    tracedExecutorService.shutdown();
    verify(wrappedExecutorService).shutdown();
    verifyNoMoreInteractions(wrappedExecutorService);
  }

  @Test
  public void testShutdownNow() {
    List<java.lang.Runnable> expectedRunnableList = new ArrayList<>();
    when(wrappedExecutorService.shutdownNow()).thenReturn(expectedRunnableList);
    assertSame(expectedRunnableList, tracedExecutorService.shutdownNow());
    verify(wrappedExecutorService).shutdownNow();
    verifyNoMoreInteractions(wrappedExecutorService);
  }

  @Test
  public void testIsShutdown() {
    when(wrappedExecutorService.isShutdown()).thenReturn(true);
    assertTrue(tracedExecutorService.isShutdown());
    verify(wrappedExecutorService).isShutdown();
    verifyNoMoreInteractions(wrappedExecutorService);
  }

  @Test
  public void testIsTerminated() {
    when(wrappedExecutorService.isTerminated()).thenReturn(false);
    assertFalse(tracedExecutorService.isTerminated());
    verify(wrappedExecutorService).isTerminated();
    verifyNoMoreInteractions(wrappedExecutorService);
  }

  @Test
  public void testAwaitTermination() throws Exception {
    when(wrappedExecutorService.awaitTermination(3, TIME_UNIT)).thenReturn(true);
    assertTrue(tracedExecutorService.awaitTermination(3, TIME_UNIT));
    verify(wrappedExecutorService).awaitTermination(3, TIME_UNIT);
    verifyNoMoreInteractions(wrappedExecutorService);
  }

  @Test
  public void testSubmitCallableOfT() {
    java.util.concurrent.Callable<Span> wrappedCallable = mock(java.util.concurrent.Callable.class);

    tracedExecutorService.submit(wrappedCallable);

    verify(traceContext, times(1)).isEmpty();
    verify(traceContext, times(1)).getCurrentSpan();
    verify(wrappedExecutorService, times(1)).submit(any(Callable.class));
    verifyNoMoreInteractions(wrappedExecutorService, wrappedCallable, traceContext);
  }

  @Test
  public void testSubmitRunnable() {
    java.lang.Runnable wrappedRunnable = mock(java.lang.Runnable.class);

    tracedExecutorService.submit(wrappedRunnable);

    verify(traceContext, times(1)).isEmpty();
    verify(traceContext, times(1)).getCurrentSpan();
    verify(wrappedExecutorService).submit(any(Runnable.class));
    verifyNoMoreInteractions(wrappedExecutorService, wrappedRunnable, traceContext);
  }

  @Test
  public void testInvokeAll() throws Exception {
    tracedExecutorService.invokeAll(callableList);

    verify(wrappedExecutorService).invokeAll(any(List.class));
    verify(traceContext, times(callableList.size())).isEmpty();
    verify(traceContext, times(callableList.size())).getCurrentSpan();
    verifyNoMoreInteractions(wrappedExecutorService, traceContext);
  }

  @Test
  public void testInvokeAny() throws Exception {
    tracedExecutorService.invokeAny(callableList);

    verify(wrappedExecutorService).invokeAny(any(List.class));
    verify(traceContext, times(callableList.size())).isEmpty();
    verify(traceContext, times(callableList.size())).getCurrentSpan();
    verifyNoMoreInteractions(wrappedExecutorService, traceContext);
  }

  @Test
  public void testExecute() {
    java.lang.Runnable wrappedRunnable = mock(java.lang.Runnable.class);

    tracedExecutorService.execute(wrappedRunnable);

    verify(traceContext, times(1)).isEmpty();
    verify(traceContext, times(1)).getCurrentSpan();
    verify(wrappedExecutorService).execute(any(Runnable.class));
    verifyNoMoreInteractions(wrappedExecutorService, traceContext);
  }
}
