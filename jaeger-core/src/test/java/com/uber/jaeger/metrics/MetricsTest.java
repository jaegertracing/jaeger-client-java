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
package com.uber.jaeger.metrics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
