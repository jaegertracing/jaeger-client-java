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

import io.jaegertracing.internal.JaegerBaseTracer;
import io.jaegertracing.internal.PropagationRegistry;
import io.jaegertracing.internal.baggage.DefaultBaggageRestrictionManager;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.metrics.NoopMetricsFactory;
import io.jaegertracing.internal.propagation.TextMapCodec;
import io.jaegertracing.internal.reporters.RemoteReporter;
import io.jaegertracing.internal.samplers.RemoteControlledSampler;
import io.jaegertracing.internal.utils.Clock;
import io.jaegertracing.internal.utils.SystemClock;
import io.jaegertracing.spi.BaggageRestrictionManager;
import io.jaegertracing.spi.Extractor;
import io.jaegertracing.spi.Injector;
import io.jaegertracing.spi.Reporter;
import io.jaegertracing.spi.Sampler;
import io.jaegertracing.spi.metrics.MetricsFactory;
import io.opentracing.ScopeManager;
import io.opentracing.propagation.Format;
import io.opentracing.util.ThreadLocalScopeManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds Jaeger Tracer with options.
 */
public final class JaegerTracerBuilder {
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

  public JaegerTracerBuilder(String serviceName) {
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
  public JaegerTracerBuilder withReporter(Reporter reporter) {
    this.reporter = reporter;
    return this;
  }

  /**
   * @param sampler sampler.
   */
  public JaegerTracerBuilder withSampler(Sampler sampler) {
    this.sampler = sampler;
    return this;
  }

  public <T> JaegerTracerBuilder registerInjector(Format<T> format, Injector<T> injector) {
    this.registry.register(format, injector);
    return this;
  }

  public <T> JaegerTracerBuilder registerExtractor(Format<T> format, Extractor<T> extractor) {
    this.registry.register(format, extractor);
    return this;
  }

  /**
   * Creates a new {@link Metrics} to be used with the tracer, backed by the given {@link MetricsFactory}
   *
   * @param metricsFactory the metrics factory to use
   * @return this instance of the builder
   */
  public JaegerTracerBuilder withMetricsFactory(MetricsFactory metricsFactory) {
    this.metrics = new Metrics(metricsFactory);
    return this;
  }

  public JaegerTracerBuilder withScopeManager(ScopeManager scopeManager) {
    this.scopeManager = scopeManager;
    return this;
  }

  public JaegerTracerBuilder withClock(Clock clock) {
    this.clock = clock;
    return this;
  }

  public JaegerTracerBuilder withZipkinSharedRpcSpan() {
    zipkinSharedRpcSpan = true;
    return this;
  }

  public JaegerTracerBuilder withExpandExceptionLogs() {
    this.expandExceptionLogs = true;
    return this;
  }

  public JaegerTracerBuilder withMetrics(Metrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public JaegerTracerBuilder withTag(String key, String value) {
    tags.put(key, value);
    return this;
  }

  public JaegerTracerBuilder withTag(String key, boolean value) {
    tags.put(key, value);
    return this;
  }

  public JaegerTracerBuilder withTag(String key, Number value) {
    tags.put(key, value);
    return this;
  }

  public JaegerTracerBuilder withTags(Map<String, String> tags) {
    if (tags != null) {
      this.tags.putAll(tags);
    }
    return this;
  }

  public JaegerTracerBuilder withBaggageRestrictionManager(BaggageRestrictionManager baggageRestrictionManager) {
    this.baggageRestrictionManager = baggageRestrictionManager;
    return this;
  }

  public JaegerBaseTracer build() {
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

