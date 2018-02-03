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

package com.uber.jaeger.metrics;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MicrometerTest {
  PrometheusMeterRegistry prometheusRegistry;
  MicrometerStatsReporter metricsReporter;
  Metrics metrics;

  @Before
  public void setUp() {
    prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    io.micrometer.core.instrument.Metrics.addRegistry(prometheusRegistry);
    metricsReporter = new MicrometerStatsReporter();
    metrics = Metrics.fromStatsReporter(metricsReporter);
  }

  @After
  public void tearDown() {
    io.micrometer.core.instrument.Metrics.removeRegistry(prometheusRegistry);
  }

  @Test
  public void testCounterWithoutExplicitTags() {
    metrics.decodingErrors.inc(1);
    assertThat(metricsReporter.registry().get("jaeger.decoding-errors").counter().count(), IsEqual.equalTo(1D));
    assertTrue(prometheusRegistry.scrape().contains("jaeger_decoding_errors"));
  }

  @Test
  public void testCounterWithExplicitTags() {
    metrics.tracesJoinedSampled.inc(1);
    assertThat(
        metricsReporter.registry().get("jaeger.traces").tags("sampled", "y", "state", "joined").counter().count(),
        IsEqual.equalTo(1D)
    );
    String output = prometheusRegistry.scrape();
    assertTrue(output.contains("jaeger_traces"));
    assertTrue(output.contains("sampled=\"y\""));
    assertTrue(output.contains("state=\"joined\""));
  }

  @Test
  public void testGaugeWithoutExplicitTags() {
    metrics.reporterQueueLength.update(1);
    assertThat(metricsReporter.registry().get("jaeger.reporter-queue").gauge().value(), IsEqual.equalTo(1D));
    assertTrue(prometheusRegistry.scrape().contains("jaeger_reporter_queue"));
  }
}
