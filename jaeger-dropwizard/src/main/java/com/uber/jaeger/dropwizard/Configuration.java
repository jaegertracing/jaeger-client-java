/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.uber.jaeger.dropwizard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.opentracing.NoopTracer;
import io.opentracing.Tracer;

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
    this.disable = disable;
  }

  @Override
  synchronized public Tracer getTracer() {
    if (disable) {
      return NoopTracer.INSTANCE;
    }
    return super.getTracer();
  }
}
