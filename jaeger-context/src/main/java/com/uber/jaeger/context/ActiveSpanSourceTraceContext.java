/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

import io.opentracing.ActiveSpan;
import io.opentracing.ActiveSpanSource;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalActiveSpan;

import java.lang.reflect.Field;

/**
 * This is a {@link TraceContext} that relies on the {@link ActiveSpanSource} registered with {@link
 * GlobalTracer}.
 */
public class ActiveSpanSourceTraceContext implements TraceContext {

  private final ActiveSpanSource activeSpanSource;
  /**
   * This is a hack to retrieve the span wrapped by the {@link ThreadLocalActiveSpan} implementation
   * to shoehorn into the {@link TraceContext} implementation. This is being done so that
   * instrumentation relying on {@link Tracer} is consistent with instrumentation using {@link
   * TraceContext}. We expect to remove this when opentracing-api version 0.31 is released.
   */
  private static final Field wrappedSpan;

  static {
    try {
      wrappedSpan = ThreadLocalActiveSpan.class.getDeclaredField("wrapped");
      wrappedSpan.setAccessible(true);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException("Unable to access ThreadLocalActiveSpan.wrapped reflectively.", e);
    }
  }

  public ActiveSpanSourceTraceContext(ActiveSpanSource activeSpanSource) {
    this.activeSpanSource = activeSpanSource;
  }

  /** Makes the span active. */
  @Override
  public void push(Span span) {
    activeSpanSource.makeActive(span);
  }

  /** Deactivates the current active span. */
  @Override
  public Span pop() {
    ActiveSpan activeSpan = activeSpanSource.activeSpan();
    Span span = getSpan(activeSpan);
    activeSpan.deactivate();
    return span;
  }

  /** Retrieves the current active span. */
  @Override
  public Span getCurrentSpan() {
    ActiveSpan activeSpan = activeSpanSource.activeSpan();
    return getSpan(activeSpan);
  }

  @Override
  public boolean isEmpty() {
    return activeSpanSource.activeSpan() == null;
  }

  private Span getSpan(ActiveSpan activeSpan) {
    Span span;
    try {
      span = (Span) wrappedSpan.get(activeSpan);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    return span;
  }
}
