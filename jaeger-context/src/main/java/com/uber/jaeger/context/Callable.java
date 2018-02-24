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

import io.opentracing.Span;
import io.opentracing.Tracer;

public class Callable<T> implements java.util.concurrent.Callable<T> {
  private final java.util.concurrent.Callable<T> wrappedCallable;
  private final Tracer tracer;
  private final Span currentSpan;

  public Callable(java.util.concurrent.Callable<T> wrappedCallable, Tracer tracer) {
    this.wrappedCallable = wrappedCallable;
    this.tracer = tracer;
    this.currentSpan = tracer.activeSpan();
  }

  @Override
  public T call() throws Exception {
    if (currentSpan != null) {
      tracer.scopeManager().activate(currentSpan,false);
    }

    try {
      return wrappedCallable.call();
    } finally {
      if (currentSpan != null) {
        tracer.scopeManager().active().close();
      }
    }
  }
}
