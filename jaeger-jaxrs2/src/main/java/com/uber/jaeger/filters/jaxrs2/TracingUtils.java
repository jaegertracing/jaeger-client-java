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

package com.uber.jaeger.filters.jaxrs2;

import com.uber.jaeger.context.TraceContext;
import io.opentracing.Tracer;
import java.util.concurrent.ExecutorService;

/**
 * TracingUtils is going to be deprecated. To use its method please provide a tracer via
 * {@link com.uber.jaeger.filters.jaxrs2.TracingUtils#setTracer(io.opentracing.Tracer)}.
 */
@Deprecated
public class TracingUtils {

  public static void setTracer(io.opentracing.Tracer tracer) {
    com.uber.jaeger.context.TracingUtils.setTracer(tracer);
  }

  @Deprecated
  public static TraceContext getTraceContext() {
    return com.uber.jaeger.context.TracingUtils.getTraceContext();
  }

  @Deprecated
  public static ExecutorService tracedExecutor(ExecutorService wrappedExecutorService) {
    return com.uber.jaeger.context.TracingUtils.tracedExecutor(wrappedExecutorService);
  }

  /**
   * Returns a new client filter with the passed ``io.opentracing.Tracer``.
   * @param tracer Tracer
   * @return ClientFilter
   * @deprecated Use {@link ClientFilter#ClientFilter(Tracer)}.
   */
  @Deprecated
  public static ClientFilter clientFilter(Tracer tracer) {
    return new ClientFilter(tracer);
  }

  /**
   * @deprecated Use {@link ServerFilter(Tracer)}.
   */
  @Deprecated
  public static ServerFilter serverFilter(Tracer tracer) {
    return new ServerFilter(tracer);
  }

  private TracingUtils() {}
}
