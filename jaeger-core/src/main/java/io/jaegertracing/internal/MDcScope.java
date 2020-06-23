/*
 * Copyright (c) 2018, The Jaeger Authors
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

package io.jaegertracing.internal;

import io.opentracing.Scope;
import io.opentracing.Span;
import org.slf4j.MDC;

public class MDcScope implements Scope {
  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";
  private static final String SAMPLED = "sampled";

  private final Scope wrapped;
  private final String originalTraceId;
  private final String originalSpanId;
  private final String originalSampled;

  /**
   * mdcScope.
   */
  public MDcScope(Scope scope, Span span) {
    this.wrapped = scope;
    this.originalTraceId = MDC.get(TRACE_ID);
    this.originalSpanId = MDC.get(SPAN_ID);
    this.originalSampled = MDC.get(SAMPLED);

    if (span.context() instanceof JaegerSpanContext) {
      putContext((JaegerSpanContext) span.context());
    }
  }

  @Override
  public void close() {
    wrapped.close();

    MDC.remove(TRACE_ID);
    MDC.remove(SPAN_ID);
    MDC.remove(SAMPLED);

    if (originalTraceId != null) {
      MDC.put(TRACE_ID, originalTraceId);
    }
    if (originalSpanId != null) {
      MDC.put(SPAN_ID, originalSpanId);
    }
    if (originalSampled != null) {
      MDC.put(SAMPLED, originalSampled);
    }
  }

  protected void putContext(JaegerSpanContext spanContext) {
    MDC.put(TRACE_ID, spanContext.getTraceId());
    MDC.put(SPAN_ID, String.format("%16x", spanContext.getSpanId()));
    MDC.put(SAMPLED, Boolean.toString(spanContext.isSampled()));
  }
}
