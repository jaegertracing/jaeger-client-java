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

package com.uber.jaeger.micrometer;

import static io.micrometer.core.instrument.Metrics.counter;
import static io.micrometer.core.instrument.Metrics.gauge;
import static io.micrometer.core.instrument.Metrics.timer;

import com.uber.jaeger.metrics.Counter;
import com.uber.jaeger.metrics.Gauge;
import com.uber.jaeger.metrics.MetricsFactory;
import com.uber.jaeger.metrics.Timer;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MicrometerMetricsFactory implements MetricsFactory {
  @Override
  public Counter createCounter(final String name, final Map<String, String> tags) {
    return new Counter() {
      private final io.micrometer.core.instrument.Counter counter = counter(name, translateTags(tags));

      @Override
      public void inc(long delta) {
        counter.increment(delta);
      }
    };
  }

  @Override
  public Timer createTimer(final String name, final Map<String, String> tags) {
    return new Timer() {
      private final io.micrometer.core.instrument.Timer timer = timer(name, translateTags(tags));

      @Override
      public void durationMicros(long time) {
        timer.record(time, TimeUnit.MICROSECONDS);
      }
    };
  }

  @Override
  public Gauge createGauge(final String name, final Map<String, String> tags) {
    return new Gauge() {
      private final Iterable<Tag> translatedTags = translateTags(tags);

      @Override
      public void update(long amount) {
        gauge(name, translatedTags, amount);
      }
    };
  }

  private Iterable<Tag> translateTags(Map<String, String> tags) {
    final List<Tag> tagList = new ArrayList<Tag>(tags.size());
    for (Map.Entry<String, String> tag : tags.entrySet()) {
      tagList.add(new ImmutableTag(tag.getKey(), tag.getValue()));
    }
    return tagList;
  }
}
