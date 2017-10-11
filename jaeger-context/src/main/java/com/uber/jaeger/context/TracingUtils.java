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

import io.opentracing.util.GlobalTracer;

import java.util.concurrent.ExecutorService;

public class TracingUtils {
  private static TraceContext traceContext;

  public static TraceContext getTraceContext() {
    initializeTraceContext();
    return traceContext;
  }

  public static ExecutorService tracedExecutor(ExecutorService wrappedExecutorService) {
    initializeTraceContext();
    return new TracedExecutorService(wrappedExecutorService, traceContext);
  }

  private static synchronized void initializeTraceContext() {
    if (traceContext == null) {
      if (!GlobalTracer.isRegistered()) {
        throw new IllegalStateException("Please register a io.opentracing.util.GlobalTracer.");
      }
      traceContext = new ActiveSpanSourceTraceContext(GlobalTracer.get());
    }
  }

  private TracingUtils() {}
}
