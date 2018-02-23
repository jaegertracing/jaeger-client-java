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

package com.uber.jaeger.micrometer;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MicrometerTest {
  PrometheusMeterRegistry prometheusRegistry;
  Metrics metrics;
  MeterRegistry registry;

  @Before
  public void setUp() {
    registry = io.micrometer.core.instrument.Metrics.globalRegistry;
    prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    io.micrometer.core.instrument.Metrics.addRegistry(prometheusRegistry);
    metrics = new Metrics(new MicrometerMetricsFactory());
  }

  @After
  public void tearDown() {
    io.micrometer.core.instrument.Metrics.removeRegistry(prometheusRegistry);
  }

  @Test
  public void testCounterWithoutExplicitTags() {
    metrics.decodingErrors.inc(1);
    assertThat(registry.get("jaeger:span_context_decoding_errors").counter().count(), IsEqual.equalTo(1d));
    assertTrue(prometheusRegistry.scrape().contains("jaeger:span_context_decoding_errors"));
  }

  @Test
  public void testCounterWithExplicitTags() {
    metrics.tracesJoinedSampled.inc(1);
    assertThat(registry.get("jaeger:traces").tags("sampled", "y", "state", "joined").counter().count(),
          IsEqual.equalTo(1d)
    );
    String output = prometheusRegistry.scrape();
    assertTrue(output.contains("jaeger:traces"));
    assertTrue(output.contains("sampled=\"y\""));
    assertTrue(output.contains("state=\"joined\""));
  }

  @Test
  public void testGaugeWithoutExplicitTags() {
    metrics.reporterQueueLength.update(1);
    assertThat(registry.get("jaeger:reporter_queue_length").gauge().value(), IsEqual.equalTo(1d));
    assertTrue(prometheusRegistry.scrape().contains("jaeger:reporter_queue_length"));
  }

  @Test
  public void testTimer() {
    // we have no timers on the Metrics class yet, so, we simulate one
    Map<String, String> tags = new HashMap<>(1);
    tags.put("akey", "avalue");
    Timer timer = new MicrometerMetricsFactory().createTimer("jaeger:timed_operation", tags);
    timer.durationMicros(100);

    assertThat(registry.get("jaeger:timed_operation").timer().totalTime(TimeUnit.MICROSECONDS), IsEqual.equalTo(100d));
    assertTrue(prometheusRegistry.scrape().contains("jaeger:timed_operation_seconds"));
  }
}
