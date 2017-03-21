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
