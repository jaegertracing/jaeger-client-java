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

import static io.micrometer.core.instrument.Metrics.counter;
import static io.micrometer.core.instrument.Metrics.gauge;
import static io.micrometer.core.instrument.Metrics.timer;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MicrometerStatsReporter implements StatsReporter {

  @Override
  public void incCounter(String name, long delta, Map<String, String> tags) {
    counter(name, translateTags(tags)).increment(delta);
  }

  @Override
  public void recordTimer(String name, long time, Map<String, String> tags) {
    timer(name, translateTags(tags)).record(time, TimeUnit.MICROSECONDS);
  }

  @Override
  public void updateGauge(String name, long amount, Map<String, String> tags) {
    gauge(name, translateTags(tags), amount);
  }

  private Iterable<Tag> translateTags(Map<String, String> tags) {
    List<Tag> tagList = new ArrayList<Tag>(tags.size());
    for (Map.Entry<String, String> tag : tags.entrySet()) {
      tagList.add(new ImmutableTag(tag.getKey(), tag.getValue()));
    }
    return tagList;
  }
}
