/*
 * Copyright (c) 2020, The Jaeger Authors
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
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.util.ThreadLocalScopeManager;
import org.slf4j.MDC;

public class MDCScopeManager implements ScopeManager {
  private final ScopeManager wrappedScopeManager;
  private final String mdcTraceIdKey;
  private final String mdcSpanIdKey;
  private final String mdcSampledKey;

  private MDCScopeManager(Builder builder) {
    this.wrappedScopeManager = builder.scopeManager;
    this.mdcTraceIdKey = builder.mdcTraceIdKey;
    this.mdcSpanIdKey = builder.mdcSpanIdKey;
    this.mdcSampledKey = builder.mdcSampledKey;
  }

  @Override
  public Scope activate(Span span) {
    return new MDCScope(wrappedScopeManager.activate(span), span);
  }

  @Override
  public Span activeSpan() {
    return wrappedScopeManager.activeSpan();
  }

  /**
   * Builds an {@link MDCScopeManager} with options.
   * Calling {@code new MDCScopeManager.Builder().build()}
   * Builds an {@link MDCScopeManager} with configuration as follows:
   * mdcTraceIDKey set to "traceId"
   * mdcSpanIdKey set to "spanId"
   * mdcSampledKey set to "sampled"
   */
  public static class Builder {
    private ScopeManager scopeManager = new ThreadLocalScopeManager();
    private String mdcTraceIdKey = "traceId";
    private String mdcSpanIdKey = "spanId";
    private String mdcSampledKey = "sampled";

    public Builder withScopeManager(ScopeManager scopeManager) {
      this.scopeManager = scopeManager;
      return this;
    }

    public Builder withMDCTraceIdKey(String mdcTraceIdKey) {
      this.mdcTraceIdKey = mdcTraceIdKey;
      return this;
    }

    public Builder withMDCSpanIdKey(String mdcSpanIdKey) {
      this.mdcSpanIdKey = mdcSpanIdKey;
      return this;
    }

    public Builder withMDCSampledKey(String mdcSampledKey) {
      this.mdcSampledKey = mdcSampledKey;
      return this;
    }

    public MDCScopeManager build() {
      return new MDCScopeManager(this);
    }

  }

  private class MDCScope implements Scope {
    private final Scope wrappedScope;
    private final String previousTraceId;
    private final String previousSpanId;
    private final String previousSampled;

    /**
     * mdcScope.
     */
    MDCScope(Scope scope, Span span) {
      this.wrappedScope = scope;
      this.previousTraceId = MDC.get(mdcTraceIdKey);
      this.previousSpanId = MDC.get(mdcSpanIdKey);
      this.previousSampled = MDC.get(mdcSampledKey);

      if (span.context() instanceof JaegerSpanContext) {
        putContext((JaegerSpanContext) span.context());
      }
    }

    protected void putContext(JaegerSpanContext spanContext) {
      replace(mdcTraceIdKey, spanContext.toTraceId());
      replace(mdcSpanIdKey, spanContext.toSpanId());
      replace(mdcSampledKey, String.valueOf(spanContext.isSampled()));
    }

    private void replace(String key, String value) {
      if (value == null) {
        MDC.remove(key);
      } else {
        MDC.put(key, value);
      }
    }

    @Override
    public void close() {
      wrappedScope.close();
      replace(mdcTraceIdKey, previousTraceId);
      replace(mdcSpanIdKey, previousSpanId);
      replace(mdcSampledKey, previousSampled);
    }
  }
}
