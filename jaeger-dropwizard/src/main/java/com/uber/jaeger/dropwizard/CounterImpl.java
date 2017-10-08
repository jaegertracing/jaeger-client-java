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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.uber.jaeger.metrics.Counter;
import java.util.Map;

public class CounterImpl implements Counter {
  private final Meter meter;

  CounterImpl(String name, Map<String, String> tags, MetricRegistry registry) {
    String metricName = com.uber.jaeger.metrics.Metrics.addTagsToMetricName(name, tags);
    meter = registry.meter(metricName);
  }

  @Override
  public void inc(long delta) {
    meter.mark(delta);
  }
}
