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

@SuppressWarnings("JavaLangClash") // this class will be deprecated in the future
public class Runnable implements java.lang.Runnable {
  private final java.lang.Runnable wrappedRunnable;
  private final TraceContext traceContext;
  private final Span currentSpan;

  public Runnable(java.lang.Runnable wrappedRunnable, TraceContext traceContext) {
    this.wrappedRunnable = wrappedRunnable;
    this.traceContext = traceContext;
    if (!traceContext.isEmpty()) {
      this.currentSpan = traceContext.getCurrentSpan();
    } else {
      this.currentSpan = null;
    }
  }

  @Override
  public void run() {
    if (currentSpan != null) {
      traceContext.push(currentSpan);
    }

    try {
      wrappedRunnable.run();
    } finally {
      if (currentSpan != null) {
        traceContext.pop();
      }
    }
  }
}
