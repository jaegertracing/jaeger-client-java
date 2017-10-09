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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentracing.Span;
import org.junit.Before;
import org.junit.Test;

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
