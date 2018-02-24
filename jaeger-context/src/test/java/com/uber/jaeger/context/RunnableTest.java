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

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class RunnableTest {

  Scope scope;
  ScopeManager scopeManager;
  Span span;
  Tracer tracer;

  @Before
  public void setUp() {
    tracer = Mockito.mock(Tracer.class);
    scope = Mockito.mock(Scope.class);
    scopeManager = Mockito.mock(ScopeManager.class);
    span = Mockito.mock(Span.class);
    when(tracer.activeSpan()).thenReturn(span);
    when(tracer.scopeManager()).thenReturn(scopeManager);
    when(scopeManager.active()).thenReturn(scope);
    when(scope.span()).thenReturn(span);
  }

  @Test
  public void testIntrumentedRunnable() {
    Runnable wrappedRunnable = mock(Runnable.class);
    Runnable runnable = new Runnable(wrappedRunnable, tracer);

    runnable.run();

    verify(tracer, times(1)).activeSpan();
    verify(scopeManager, times(1)).activate(span, false);
    verify(scopeManager, times(1)).active();
    verify(scope, times(1)).close();
    verify(wrappedRunnable, times(1)).run();
    verify(tracer, times(2)).scopeManager();
    verifyNoMoreInteractions(tracer, wrappedRunnable);
  }

  @Test
  public void testIntrumentedRunnableNoCurrentSpan() {
    when(tracer.activeSpan()).thenReturn(null);

    Runnable wrappedRunnable = mock(Runnable.class);
    Runnable runnable = new Runnable(wrappedRunnable, tracer);

    runnable.run();

    verify(tracer, times(1)).activeSpan();
    verify(wrappedRunnable, times(1)).run();
    verifyNoMoreInteractions(tracer, wrappedRunnable);
  }
}
