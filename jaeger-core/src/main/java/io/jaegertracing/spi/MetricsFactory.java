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

package io.jaegertracing.spi;

import io.jaegertracing.internal.metrics.Counter;
import io.jaegertracing.internal.metrics.Gauge;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.metrics.Timer;
import java.util.Map;

/**
 * Provides a standardized way to create metrics-related objects, like {@link Counter}, {@link Timer} and {@link Gauge}.
 *
 */
public interface MetricsFactory {
  /**
   * Creates a counter with the given gauge name and set of tags. The actual metric name is a combination of those two
   * values. The counter starts at 0.
   *
   * @see Metrics#addTagsToMetricName(String, Map)
   * @param name the counter name
   * @param tags the tags to add to the counter
   * @return a {@link Counter} with a metric name following the counter name and tags
   */
  Counter createCounter(String name, Map<String, String> tags);

  /**
   * Creates a timer with the given timer name and set of tags. The actual metric name is a combination of those two
   * values. The timer starts at 0.
   *
   * @see Metrics#addTagsToMetricName(String, Map)
   * @param name the timer name
   * @param tags the tags to add to the timer
   * @return a {@link Timer} with a metric name following the counter name and tags
   */
  Timer createTimer(String name, Map<String, String> tags);

  /**
   * Creates a gauge with the given gauge name and set of tags. The actual metric name is a combination of those two
   * values. The timer starts at 0.
   *
   * @see Metrics#addTagsToMetricName(String, Map)
   * @param name the gauge name
   * @param tags the tags to add to the gauge
   * @return a {@link Gauge} with a metric name following the gauge name and tags
   */
  Gauge createGauge(String name, Map<String, String> tags);
}
