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

package com.uber.jaeger.dropwizard;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration extends com.uber.jaeger.Configuration {
  /**
   * When set, getTracer returns a Noop tracer
   */
  private final boolean disable;

  @JsonCreator
  public Configuration(
      @JsonProperty("serviceName") String serviceName,
      @JsonProperty("disable") Boolean disable,
      @JsonProperty("sampler") SamplerConfiguration samplerConfig,
      @JsonProperty("reporter") ReporterConfiguration reporterConfig) {
    super(serviceName, samplerConfig, reporterConfig);
    this.disable = disable == null ? false : disable;
  }

  @Override
  public synchronized Tracer getTracer() {
    if (disable) {
      return NoopTracerFactory.create();
    }
    return super.getTracer();
  }

  public void setMetricRegistry(MetricRegistry metricRegistry) {
    setStatsFactor(new StatsFactory(metricRegistry));
  }
}
