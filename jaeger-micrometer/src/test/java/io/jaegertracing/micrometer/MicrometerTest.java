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

package io.jaegertracing.micrometer;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.metrics.Timer;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.spi.Sampler;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)  //We need testExposedMetrics to run before testTimer
public class MicrometerTest {
  PrometheusMeterRegistry prometheusRegistry;
  Metrics metrics;
  MeterRegistry registry;
  private static Map<String, Long> expectedMetricCounts = new HashMap<>();
  private final double assertDelta = 0.00001;

  @BeforeClass
  public static void initial() {
    expectedMetricCounts.put("jaeger:sampler_updates", 2L);
    expectedMetricCounts.put("jaeger:finished_spans", 1L);
    expectedMetricCounts.put("jaeger:baggage_restrictions_updates", 2L);
    expectedMetricCounts.put("jaeger:started_spans", 2L);
    expectedMetricCounts.put("jaeger:baggage_updates", 2L);
    expectedMetricCounts.put("jaeger:sampler_queries", 2L);
    expectedMetricCounts.put("jaeger:baggage_truncations", 1L);
    expectedMetricCounts.put("jaeger:reporter_spans", 3L);
    expectedMetricCounts.put("jaeger:traces", 4L);
    expectedMetricCounts.put("jaeger:span_context_decoding_errors", 1L);
    expectedMetricCounts.put("jaeger:reporter_queue_length", 1L);
  }


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


  @Test
  public void testExposedMetrics() {
    Configuration configuration = new Configuration("exposedmetrics");
    final JaegerTracer tracer = configuration
            .getTracerBuilder()
            .withMetrics(metrics)
            .build();

    // This is a gauge, so it needs to be non-zero to come back from prometheus
    metrics.reporterQueueLength.update(1);

    List<Meter> meters = new ArrayList<>(prometheusRegistry.getMeters());
    Map<String, Long> metricCounts = meters
            .stream()
            .collect(groupingBy(m -> m.getId().getName(), counting()));

    assertEquals("Wrong number of metrics collected", expectedMetricCounts.size(), metricCounts.keySet().size());

    for (String name : metricCounts.keySet()) {
      assertTrue("Unexpected metric " +  name, expectedMetricCounts.containsKey(name));
    }

    for (String metricName : expectedMetricCounts.keySet()) {
      assertTrue("Did not find metric " + metricName, metricCounts.containsKey(metricName));
      assertEquals("Wrong count for " +  metricName, expectedMetricCounts.get(metricName),
              metricCounts.get(metricName));
    }

    tracer.close();
  }

  @Test
  public void validateMetricCounts() throws InterruptedException {
    Sampler constantSampler = new ConstSampler(true);
    Configuration configuration = new Configuration("validateMetricCounts");
    JaegerTracer tracer = configuration
            .getTracerBuilder()
            .withSampler(constantSampler)
            .withMetrics(metrics)
            .build();

    createSomeSpans(tracer);
    tracer.close();

    double finishedSpans = registry.get("jaeger:finished_spans")
            .counter()
            .count();

    double startedSpans = registry.get("jaeger:started_spans")
            .tag("sampled", "y")
            .counter()
            .count();

    double traces = registry.get("jaeger:traces")
            .tag("sampled", "y")
            .tag("state", "started")
            .counter()
            .count();

    assertEquals("Wrong number of finishedSpans", 4.0, finishedSpans, assertDelta);
    assertEquals("Wrong number of startedSpans", 10.0, startedSpans, assertDelta);
    assertEquals("Wrong number of traces", 10.0, traces, assertDelta);
  }

  @Test
  public void testServiceLoader() {
    System.setProperty(Configuration.JAEGER_SAMPLER_TYPE, ConstSampler.TYPE);
    System.setProperty(Configuration.JAEGER_SAMPLER_PARAM, "1");
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");

    // the fact that there's a service on the classpath is enough to get it loaded, unless we have an env var
    // saying to skip it
    final Configuration configuration = Configuration.fromEnv();

    System.clearProperty(Configuration.JAEGER_SERVICE_NAME);
    System.clearProperty(Configuration.JAEGER_SAMPLER_TYPE);
    System.clearProperty(Configuration.JAEGER_SAMPLER_PARAM);

    PrometheusMeterRegistry registry = (PrometheusMeterRegistry) io.micrometer.core.instrument.Metrics.globalRegistry
        .getRegistries()
        .iterator()
        .next();

    configuration.getTracer().buildSpan("theoperation").start().finish(100);
    assertEquals(1, registry.find("jaeger:started_spans").counter().count(), 0);
  }

  private void createSomeSpans(JaegerTracer tracer) {
    for (int i = 0; i < 10; i++) {
      JaegerSpan span = tracer.buildSpan("metricstest")
              .withTag("foo", "bar" + i)
              .start();
      // Only finish every 3rd span so jaeger:started_spans and finished_spans counts are different
      if (i % 3 == 0) {
        span.finish();
      }
    }
  }


}
