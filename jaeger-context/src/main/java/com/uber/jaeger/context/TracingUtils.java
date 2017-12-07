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

import java.util.concurrent.ExecutorService;

/**
 * TracingUtils is going to be deprecated. To use its method please provide a tracer via
 * {@link com.uber.jaeger.context.TracingUtils#setTracer(io.opentracing.Tracer)}.
 */
@Deprecated
public class TracingUtils {
  private static io.opentracing.Tracer tracer = null;
  private static TraceContext traceContext;

  public static synchronized void setTracer(io.opentracing.Tracer tracer) {
    TracingUtils.tracer = tracer;
    TracingUtils.traceContext = new ActiveSpanSourceTraceContext(tracer);
  }

  public static synchronized TraceContext getTraceContext() {
    assertTracerRegistered();
    return traceContext;
  }

  public static synchronized ExecutorService tracedExecutor(ExecutorService wrappedExecutorService) {
    assertTracerRegistered();
    return new TracedExecutorService(wrappedExecutorService, traceContext);
  }

  private static void assertTracerRegistered() {
    if (tracer == null) {
      throw new IllegalStateException("Please set a tracer using `setTracer(..)` before calling any functions in this class.");
    }
  }

  private TracingUtils() {}
}
