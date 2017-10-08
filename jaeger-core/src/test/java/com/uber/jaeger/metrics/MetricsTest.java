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

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MetricsTest {
  InMemoryStatsReporter metricsReporter;
  Metrics metrics;

  @Before
  public void setUp() throws Exception {
    metricsReporter = new InMemoryStatsReporter();
    metrics = new Metrics(new StatsFactoryImpl(metricsReporter));
  }

  @After
  public void tearDown() {
    metricsReporter.reset();
  }

  @Test
  public void testCounterWithoutExplicitTags() throws Exception {
    metrics.tracesJoinedSampled.inc(1);

    Object[] metricNames = metricsReporter.counters.keySet().toArray();
    String metricName = (String) metricNames[0];
    long expectedAmount = metricsReporter.counters.get(metricName);

    assertEquals(metricNames.length, 1);
    assertEquals(expectedAmount, 1);
    assertEquals("jaeger.traces.sampled=y.state=joined", metricName);
  }

  @Test
  public void testCounterWithExplicitTags() throws Exception {
    metrics.tracesJoinedSampled.inc(1);

    Object[] metricNames = metricsReporter.counters.keySet().toArray();
    String metricName = (String) metricNames[0];
    long expectedAmount = metricsReporter.counters.get(metricName);

    assertEquals(metricNames.length, 1);
    assertEquals(expectedAmount, 1);
    assertEquals("jaeger.traces.sampled=y.state=joined", metricName);
  }

  @Test
  public void testGaugeWithoutExplicitTags() {
    metrics.reporterQueueLength.update(1);

    Object[] metricNames = metricsReporter.gauges.keySet().toArray();
    String metricName = (String) metricNames[0];
    long expectedAmount = metricsReporter.gauges.get(metricName);

    assertEquals(metricNames.length, 1);
    assertEquals(1L, expectedAmount, 0.00001);
    assertEquals("jaeger.reporter-queue", metricName);
  }
}
