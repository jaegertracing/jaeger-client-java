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

import java.util.Map;

public class StatsFactoryImpl implements StatsFactory {
  private final StatsReporter reporter;

  public StatsFactoryImpl(StatsReporter reporter) {
    this.reporter = reporter;
  }

  @Override
  public Counter createCounter(final String name, final Map<String, String> tags) {
    return new Counter() {
      @Override
      public void inc(long delta) {
        reporter.incCounter(name, delta, tags);
      }
    };
  }

  @Override
  public Timer createTimer(final String name, final Map<String, String> tags) {
    return new Timer() {
      @Override
      public void durationMicros(long time) {
        reporter.recordTimer(name, time, tags);
      }
    };
  }

  @Override
  public Gauge createGauge(final String name, final Map<String, String> tags) {
    return new Gauge() {

      @Override
      public void update(long amount) {
        reporter.updateGauge(name, amount, tags);
      }
    };
  }
}
