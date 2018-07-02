/*
 * Copyright (c) 2017, The Jaeger Authors
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

package io.jaegertracing.internal.metrics;

import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * This test is to ensure we have a NOOP implementation that won't crash when used for real.
 *
 * @see InMemoryMetricsFactoryTest
 */
public class NoopMetricsFactoryTest {

  @Test
  public void metricNameIsUsedForCounter() {
    Map<String, String> tags = new HashMap<>(1);
    tags.put("foo", "bar");

    NoopMetricsFactory metricsFactory = new NoopMetricsFactory();
    metricsFactory.createCounter("thecounter", tags);
  }

  @Test
  public void counterValueIsIncreased() {
    Map<String, String> tags = new HashMap<>(1);
    tags.put("foo", "bar");

    NoopMetricsFactory metricsFactory = new NoopMetricsFactory();
    Counter counter = metricsFactory.createCounter("thecounter", tags);
    counter.inc(1);
  }

  @Test
  public void metricNameIsUsedForTimer() {
    Map<String, String> tags = new HashMap<>(1);
    tags.put("foo", "bar");

    NoopMetricsFactory metricsFactory = new NoopMetricsFactory();
    metricsFactory.createTimer("thetimer", tags);
  }

  @Test
  public void timerValueIsIncreased() {
    Map<String, String> tags = new HashMap<>(1);
    tags.put("foo", "bar");

    NoopMetricsFactory metricsFactory = new NoopMetricsFactory();
    Timer timer = metricsFactory.createTimer("thetimer", tags);
    timer.durationMicros(1);
  }

  @Test
  public void metricNameIsUsedForGauge() {
    Map<String, String> tags = new HashMap<>(1);
    tags.put("foo", "bar");

    NoopMetricsFactory metricsFactory = new NoopMetricsFactory();
    metricsFactory.createGauge("thegauge", tags);
  }

  @Test
  public void gaugeValueIsIncreased() {
    Map<String, String> tags = new HashMap<>(1);
    tags.put("foo", "bar");

    NoopMetricsFactory metricsFactory = new NoopMetricsFactory();
    Gauge gauge = metricsFactory.createGauge("thegauge", tags);
    gauge.update(1);
  }

  @Test
  public void canBeUsedWithMetrics() {
    NoopMetricsFactory metricsFactory = new NoopMetricsFactory();
    JaegerTracer tracer = new JaegerTracer.Builder("metricsFactoryTest")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .withMetrics(new Metrics(metricsFactory))
            .build();

    tracer.buildSpan("theoperation").start();
  }
}
