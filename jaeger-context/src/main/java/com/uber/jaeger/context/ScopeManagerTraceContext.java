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

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalScopeManager;

import java.lang.reflect.Field;

/**
 * This is a {@link TraceContext} implementation that relies on the {@link ScopeManager}.
 */
public class ScopeManagerTraceContext implements TraceContext {

  private final ScopeManager scopeManager;

  public ScopeManagerTraceContext(ScopeManager scopeManager) {
    this.scopeManager = scopeManager;
  }

  /** Makes the span active. */
  @Override
  public void push(Span span) {
    scopeManager.activate(span, false);
  }

  /** Deactivates the current active span and returns it. If there is no active span returns null. */
  @Override
  public Span pop() {
    Scope scope = scopeManager.active();
    if (scope == null) {
      return null;
    }
    Span span = scope.span();
    scope.close();
    return span;
  }

  /** Retrieves the current active span or null if there is no active span. */
  @Override
  public Span getCurrentSpan() {
    Scope scope = scopeManager.active();
    if (scope == null) {
      return null;
    }
    return scope.span();
  }

  @Override
  public boolean isEmpty() {
    return scopeManager.active() == null;
  }
}
