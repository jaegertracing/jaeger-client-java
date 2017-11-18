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

package com.uber.jaeger.context;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentracing.Span;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TracedExecutorServiceTest {

  private static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

  List<java.util.concurrent.Callable<Span>> callableList;

  @Mock
  ExecutorService wrappedExecutorService;
  @Mock
  Span span;
  @Mock
  TraceContext traceContext;
  @Mock
  Callable<Span> callable1;
  @Mock
  Callable<Span> callable2;
  @Mock
  Callable wrappedCallable;
  @Mock
  Runnable wrappedRunnable;

  TracedExecutorService tracedExecutorService;

  @Before
  public void setUp() {
    when(traceContext.getCurrentSpan()).thenReturn(span);
    when(traceContext.isEmpty()).thenReturn(false);
    tracedExecutorService = new TracedExecutorService(wrappedExecutorService, traceContext);
    callableList = Arrays.asList(new Callable[] {callable1, callable2});
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
    tracedExecutorService.submit(wrappedCallable);

    verify(traceContext, times(1)).isEmpty();
    verify(traceContext, times(1)).getCurrentSpan();
    verify(wrappedExecutorService, times(1)).submit(any(Callable.class));
    verifyNoMoreInteractions(wrappedExecutorService, wrappedCallable, traceContext);
  }

  @Test
  public void testSubmitRunnable() {
    tracedExecutorService.submit(wrappedRunnable);

    verify(traceContext, times(1)).isEmpty();
    verify(traceContext, times(1)).getCurrentSpan();
    verify(wrappedExecutorService).submit(any(Runnable.class));
    verifyNoMoreInteractions(wrappedExecutorService, wrappedRunnable, traceContext);
  }

  @Test
  public void testInvokeAll() throws Exception {
    tracedExecutorService.invokeAll(callableList);

    verify(wrappedExecutorService).invokeAll(anyList());
    verify(traceContext, times(callableList.size())).isEmpty();
    verify(traceContext, times(callableList.size())).getCurrentSpan();
    verifyNoMoreInteractions(wrappedExecutorService, traceContext);
  }

  @Test
  public void testInvokeAny() throws Exception {
    tracedExecutorService.invokeAny(callableList);

    verify(wrappedExecutorService).invokeAny(anyList());
    verify(traceContext, times(callableList.size())).isEmpty();
    verify(traceContext, times(callableList.size())).getCurrentSpan();
    verifyNoMoreInteractions(wrappedExecutorService, traceContext);
  }

  @Test
  public void testExecute() {
    tracedExecutorService.execute(wrappedRunnable);

    verify(traceContext, times(1)).isEmpty();
    verify(traceContext, times(1)).getCurrentSpan();
    verify(wrappedExecutorService).execute(any(Runnable.class));
    verifyNoMoreInteractions(wrappedExecutorService, traceContext);
  }
}
