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

import io.jaegertracing.spi.MetricsFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An ephemeral metrics factory, storing data in memory. This metrics factory is not meant to be used for production
 * purposes.
 */
public class InMemoryMetricsFactory implements MetricsFactory {
  private Map<String, AtomicLong> counters = new ConcurrentHashMap<String, AtomicLong>();
  private Map<String, AtomicLong> timers = new ConcurrentHashMap<String, AtomicLong>();
  private Map<String, AtomicLong> gauges = new ConcurrentHashMap<String, AtomicLong>();

  @Override
  public Counter createCounter(String name, Map<String, String> tags) {
    final AtomicLong value = new AtomicLong(0);
    counters.put(Metrics.addTagsToMetricName(name, tags), value);

    return new Counter() {
      @Override
      public void inc(long delta) {
        value.addAndGet(delta);
      }
    };
  }

  @Override
  public Timer createTimer(final String name, final Map<String, String> tags) {
    final AtomicLong value = new AtomicLong(0);
    timers.put(Metrics.addTagsToMetricName(name, tags), value);

    return new Timer() {
      @Override
      public void durationMicros(long time) {
        value.addAndGet(time);
      }
    };
  }

  @Override
  public Gauge createGauge(final String name, final Map<String, String> tags) {
    final AtomicLong value = new AtomicLong(0);
    gauges.put(Metrics.addTagsToMetricName(name, tags), value);

    return new Gauge() {
      @Override
      public void update(long amount) {
        value.getAndSet(amount);
      }
    };
  }

  /**
   * Returns the counter value information for the counter with the given metric name.
   * Note that the metric name is not the counter name, as a metric name usually includes the tags.
   *
   * @param name the metric name, which includes the tags
   * @param tags the metric tags as comma separated list of entries, like "foo=bar,baz=qux"
   * @return the counter value or -1, if no counter exists for the given metric name
   */
  public long getCounter(String name, String tags) {
    return getValue(counters, name, tags);
  }

  /**
   * Returns the counter value information for the counter with the given metric name.
   * Note that the metric name is not the counter name, as a metric name usually includes the tags.
   *
   * @param name the metric name, which includes the tags
   * @param tags the metric tags
   * @return the counter value or -1, if no counter exists for the given metric name
   */
  public long getCounter(String name, Map<String, String> tags) {
    return getValue(counters, name, tags);
  }

  /**
   * Returns the current value for the gauge with the given metric name. Note that the metric name is not the gauge
   * name, as a metric name usually includes the tags.
   *
   * @param name the metric name, which includes the tags
   * @param tags the metric tags as comma separated list of entries, like "foo=bar,baz=qux"
   * @return the gauge value or -1, if no gauge exists for the given metric name
   */
  public long getGauge(String name, String tags) {
    return getValue(gauges, name, tags);
  }

  /**
   * Returns the current value for the gauge with the given metric name. Note that the metric name is not the gauge
   * name, as a metric name usually includes the tags.
   *
   * @param name the metric name, which includes the tags
   * @param tags the metric tags
   * @return the gauge value or -1, if no gauge exists for the given metric name
   */
  public long getGauge(String name, Map<String, String> tags) {
    return getValue(gauges, name, tags);
  }

  /**
   * Returns the current accumulated timing information for the timer with the given metric name.
   * Note that the metric name is not the timer name, as a metric name usually includes the tags.
   *
   * @param name the metric name, which includes the tags
   * @param tags the metric tags as comma separated list of entries, like "foo=bar,baz=qux"
   * @return the timer value or -1, if no timer exists for the given metric name
   */
  public long getTimer(String name, String tags) {
    return getValue(timers, name, tags);
  }

  /**
   * Returns the current accumulated timing information for the timer with the given metric name.
   * Note that the metric name is not the timer name, as a metric name usually includes the tags.
   *
   * @param name the metric name, which includes the tags
   * @param tags the metric tags
   * @return the timer value or -1, if no timer exists for the given metric name
   */
  public long getTimer(String name, Map<String, String> tags) {
    return getValue(timers, name, tags);
  }

  private long getValue(Map<String, AtomicLong> collection, String name, String tags) {
    if (null == tags || tags.isEmpty()) {
      return getValue(collection, name);
    }

    String[] entries = tags.split(",");
    Map<String, String> tagsAsMap = new HashMap<String, String>(entries.length);
    for (String entry : entries) {
      String[] keyValue = entry.split("=");
      if (keyValue.length == 2) {
        tagsAsMap.put(keyValue[0], keyValue[1]);
      } else {
        tagsAsMap.put(keyValue[0], "");
      }
    }

    return getValue(collection, Metrics.addTagsToMetricName(name, tagsAsMap));
  }

  private long getValue(Map<String, AtomicLong> collection, String name, Map<String, String> tags) {
    return getValue(collection, Metrics.addTagsToMetricName(name, tags));
  }

  private long getValue(Map<String, AtomicLong> collection, String name) {
    AtomicLong value = collection.get(name);
    if (null == value) {
      return -1;
    } else {
      return value.get();
    }
  }
}
