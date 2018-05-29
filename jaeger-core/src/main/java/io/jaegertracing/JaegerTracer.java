/*
 * Copyright (c) 2018 The Jaeger Authors
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

package io.jaegertracing;

import io.jaegertracing.codec.TextMapCodec;
import io.jaegertracing.internal.JaegerBaseTracer;
import io.jaegertracing.internal.PropagationRegistry;
import io.jaegertracing.internal.Span;
import io.jaegertracing.internal.SpanContext;
import io.jaegertracing.internal.baggage.DefaultBaggageRestrictionManager;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.metrics.NoopMetricsFactory;
import io.jaegertracing.internal.utils.Clock;
import io.jaegertracing.internal.utils.SystemClock;
import io.jaegertracing.reporter.RemoteReporter;
import io.jaegertracing.sampler.RemoteControlledSampler;
import io.jaegertracing.spi.BaggageRestrictionManager;
import io.jaegertracing.spi.Extractor;
import io.jaegertracing.spi.Injector;
import io.jaegertracing.spi.Reporter;
import io.jaegertracing.spi.Sampler;
import io.jaegertracing.spi.metrics.MetricsFactory;
import io.opentracing.ScopeManager;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.util.ThreadLocalScopeManager;
import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines the Jaeger Tracer extensions to the OpenTracing API.
 */
public interface JaegerTracer extends Tracer, Closeable {
  String getVersion();

  String getServiceName();

  int getIpv4();

  Map<String, ?> tags();

  Reporter getReporter();

  SpanContext setBaggage(Span span, String key, String value);

  final class Builder {
    private Sampler sampler;
    private Reporter reporter;
    private final PropagationRegistry registry = new PropagationRegistry();
    private Metrics metrics = new Metrics(new NoopMetricsFactory());
    private final String serviceName;
    private Clock clock = new SystemClock();
    private Map<String, Object> tags = new HashMap<String, Object>();
    private boolean zipkinSharedRpcSpan;
    private ScopeManager scopeManager = new ThreadLocalScopeManager();
    private BaggageRestrictionManager baggageRestrictionManager = new DefaultBaggageRestrictionManager();
    private boolean expandExceptionLogs;

    public Builder(String serviceName) {
      this.serviceName = checkValidServiceName(serviceName);
      TextMapCodec textMapCodec = new TextMapCodec(false);
      this.registerInjector(Format.Builtin.TEXT_MAP, textMapCodec);
      this.registerExtractor(Format.Builtin.TEXT_MAP, textMapCodec);
      TextMapCodec httpCodec = new TextMapCodec(true);
      this.registerInjector(Format.Builtin.HTTP_HEADERS, httpCodec);
      this.registerExtractor(Format.Builtin.HTTP_HEADERS, httpCodec);
      // TODO binary codec not implemented
    }

    /**
     * @param reporter reporter.
     */
    public Builder withReporter(Reporter reporter) {
      this.reporter = reporter;
      return this;
    }

    /**
     * @param sampler sampler.
     */
    public Builder withSampler(Sampler sampler) {
      this.sampler = sampler;
      return this;
    }

    public <T> Builder registerInjector(Format<T> format, Injector<T> injector) {
      this.registry.register(format, injector);
      return this;
    }

    public <T> Builder registerExtractor(Format<T> format, Extractor<T> extractor) {
      this.registry.register(format, extractor);
      return this;
    }

    /**
     * Creates a new {@link Metrics} to be used with the tracer, backed by the given {@link MetricsFactory}
     *
     * @param metricsFactory the metrics factory to use
     * @return this instance of the builder
     */
    public Builder withMetricsFactory(MetricsFactory metricsFactory) {
      this.metrics = new Metrics(metricsFactory);
      return this;
    }

    public Builder withScopeManager(ScopeManager scopeManager) {
      this.scopeManager = scopeManager;
      return this;
    }

    public Builder withClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    public Builder withZipkinSharedRpcSpan() {
      zipkinSharedRpcSpan = true;
      return this;
    }

    public Builder withExpandExceptionLogs() {
      this.expandExceptionLogs = true;
      return this;
    }

    public Builder withMetrics(Metrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public Builder withTag(String key, String value) {
      tags.put(key, value);
      return this;
    }

    public Builder withTag(String key, boolean value) {
      tags.put(key, value);
      return this;
    }

    public Builder withTag(String key, Number value) {
      tags.put(key, value);
      return this;
    }

    public Builder withTags(Map<String, String> tags) {
      if (tags != null) {
        this.tags.putAll(tags);
      }
      return this;
    }

    public Builder withBaggageRestrictionManager(BaggageRestrictionManager baggageRestrictionManager) {
      this.baggageRestrictionManager = baggageRestrictionManager;
      return this;
    }

    public JaegerTracer build() {
      if (reporter == null) {
        reporter = new RemoteReporter.Builder()
            .withMetrics(metrics)
            .build();
      }
      if (sampler == null) {
        sampler = new RemoteControlledSampler.Builder(serviceName)
            .withMetrics(metrics)
            .build();
      }
      return new JaegerBaseTracer(serviceName, reporter, sampler, registry, clock, metrics, tags,
          zipkinSharedRpcSpan, scopeManager, baggageRestrictionManager, expandExceptionLogs);
    }

    public static String checkValidServiceName(String serviceName) {
      if (serviceName == null || serviceName.trim().length() == 0) {
        throw new IllegalArgumentException("Service name must not be null or empty");
      }
      return serviceName;
    }
  }
}
