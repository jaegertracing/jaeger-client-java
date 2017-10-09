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

package com.uber.jaeger.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class GaugeImpl implements com.uber.jaeger.metrics.Gauge {
  private final AtomicLong gaugeValue = new AtomicLong(0);

  GaugeImpl(String name, Map<String, String> tags, MetricRegistry registry) {
    String metricName = com.uber.jaeger.metrics.Metrics.addTagsToMetricName(name, tags);
    registry.register(
        metricName,
        new Gauge<Number>() {

          @Override
          public Number getValue() {
            return gaugeValue.get();
          }
        });
  }

  @Override
  public void update(long amount) {
    gaugeValue.set(amount);
  }
}
