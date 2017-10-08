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

import java.util.HashMap;
import java.util.Map;

public class InMemoryStatsReporter implements StatsReporter {
  public Map<String, Long> counters = new HashMap<String, Long>();
  public Map<String, Long> gauges = new HashMap<String, Long>();
  public Map<String, Long> timers = new HashMap<String, Long>();

  void reset() {
    counters = new HashMap<String, Long>();
    gauges = new HashMap<String, Long>();
    timers = new HashMap<String, Long>();
  }

  @Override
  public void incCounter(String name, long delta, Map<String, String> tags) {
    String metricName = Metrics.addTagsToMetricName(name, tags);
    long initialValue = 0;
    if (counters.containsKey(metricName)) {
      initialValue = counters.get(metricName);
    }
    counters.put(metricName, initialValue + delta);
  }

  @Override
  public void recordTimer(String name, long time, Map<String, String> tags) {
    String metricName = Metrics.addTagsToMetricName(name, tags);
    long initialValue = 0;
    if (counters.containsKey(metricName)) {
      initialValue = counters.get(metricName);
    }
    timers.put(metricName, initialValue + time);
  }

  @Override
  public void updateGauge(String name, long amount, Map<String, String> tags) {
    String metricName = Metrics.addTagsToMetricName(name, tags);
    long initialValue = 0;
    if (counters.containsKey(metricName)) {
      initialValue = counters.get(metricName);
    }
    gauges.put(metricName, initialValue + amount);
  }
}
