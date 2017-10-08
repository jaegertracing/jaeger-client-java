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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class StatsFactoryTest {
  MetricRegistry registry;
  StatsFactory statsFactory;

  @Before
  public void setUp() {
    registry = mock(MetricRegistry.class);
    statsFactory = new StatsFactory(registry);
  }

  @Test
  public void testCounterCallsMark() {
    Meter mockCounter = mock(Meter.class);
    when(registry.meter(any(String.class))).thenReturn(mockCounter);
    String name = "traces";
    Map<String, String> tags =
        new HashMap<String, String>() {
          {
            put("state", "started");
            put("sampled", "y");
          }
        };
    long expectedValue = 1000;

    com.uber.jaeger.metrics.Counter cntr = statsFactory.createCounter(name, tags);
    cntr.inc(expectedValue);

    verify(mockCounter).mark(expectedValue);
  }

  @Test
  public void testStatsFactoryCreatesCounter() {
    String name = "traces";
    Map<String, String> tags =
        new HashMap<String, String>() {
          {
            put("state", "started");
            put("sampled", "y");
          }
        };

    statsFactory.createCounter(name, tags);

    verify(registry, times(1)).meter(any(String.class));
  }

  @Test
  public void testTimerCallsRecord() {
    Timer mockTimer = mock(Timer.class);
    when(registry.timer(any(String.class))).thenReturn(mockTimer);
    String name = "traces";
    Map<String, String> tags =
        new HashMap<String, String>() {
          {
            put("state", "started");
            put("sampled", "y");
          }
        };
    long expectedValue = 1000;

    com.uber.jaeger.metrics.Timer timer = statsFactory.createTimer(name, tags);
    timer.durationMicros(expectedValue);

    verify(mockTimer).update(expectedValue, TimeUnit.MICROSECONDS);
  }

  @Test
  public void testStatsFactoryCreatesTimer() {
    String name = "reporting";
    Map<String, String> tags =
        new HashMap<String, String>() {
          {
            put("state", "finished");
            put("unit", "seconds");
          }
        };

    statsFactory.createTimer(name, tags);

    verify(registry, times(1)).timer(any(String.class));
  }

  @Test
  public void testStatsFactoryCreatesGauge() {
    String name = "reporting";
    Map<String, String> tags =
        new HashMap<String, String>() {
          {
            put("state", "started");
            put("type", "queue");
          }
        };

    statsFactory.createGauge(name, tags);

    verify(registry, times(1)).register(any(String.class), any(Gauge.class));
  }
}
