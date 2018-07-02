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
import java.util.Map;

/**
 * A metrics factory that implements NOOP counters, timers and gauges.
 */
public class NoopMetricsFactory implements MetricsFactory {
  @Override
  public Counter createCounter(String name, Map<String, String> tags) {
    return new Counter() {
      @Override
      public void inc(long delta) {
      }
    };
  }

  @Override
  public Timer createTimer(final String name, final Map<String, String> tags) {
    return new Timer() {
      @Override
      public void durationMicros(long time) {
      }
    };
  }

  @Override
  public Gauge createGauge(final String name, final Map<String, String> tags) {
    return new Gauge() {
      @Override
      public void update(long amount) {
      }
    };
  }
}
