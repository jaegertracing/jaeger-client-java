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

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class MetricsTest {
  InMemoryMetricsFactory metricsFactory;
  Metrics metrics;

  @Before
  public void setUp() {
    metricsFactory = new InMemoryMetricsFactory();
    metrics = new Metrics(metricsFactory);
  }

  @Test
  public void testCounterWithoutExplicitTags() {
    metrics.tracesJoinedSampled.inc(1);
    assertEquals(1, metricsFactory.getCounter("jaeger:traces", "sampled=y,state=joined"));
  }

  @Test
  public void testGaugeWithoutExplicitTags() {
    metrics.reporterQueueLength.update(1);
    assertEquals(1, metricsFactory.getGauge("jaeger:reporter_queue_length", ""));
  }

  @Test
  public void testAddTagsToMetricName() {
    Map<String, String> tags = new HashMap<>();
    tags.put("foo", "bar");
    assertEquals("thecounter.foo=bar", Metrics.addTagsToMetricName("thecounter", tags));
    assertEquals("jaeger:thecounter.foo=bar", Metrics.addTagsToMetricName("jaeger:thecounter", tags));
  }
}
