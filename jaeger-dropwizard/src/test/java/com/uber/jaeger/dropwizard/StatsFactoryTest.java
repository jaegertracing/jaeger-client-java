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
package com.uber.jaeger.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        Map<String, String> tags = new HashMap<String, String>() {{
            put("state", "started");
            put("sampled", "y");
        }};
        long expectedValue = 1000;

        com.uber.jaeger.metrics.Counter cntr = statsFactory.createCounter(name, tags);
        cntr.inc(expectedValue);

        verify(mockCounter).mark(expectedValue);
    }

    @Test
    public void testStatsFactoryCreatesCounter() {
        String name = "traces";
        Map<String, String> tags = new HashMap<String, String>() {{
            put("state", "started");
            put("sampled", "y");
        }};

        statsFactory.createCounter(name, tags);

        verify(registry, times(1)).meter(any(String.class));
    }

    @Test
    public void testTimerCallsRecord() {
        Timer mockTimer = mock(Timer.class);
        when(registry.timer(any(String.class))).thenReturn(mockTimer);
        String name = "traces";
        Map<String, String> tags = new HashMap<String, String>() {{
            put("state", "started");
            put("sampled", "y");
        }};
        long expectedValue = 1000;

        com.uber.jaeger.metrics.Timer timer = statsFactory.createTimer(name, tags);
        timer.durationMicros(expectedValue);

        verify(mockTimer).update(expectedValue, TimeUnit.MICROSECONDS);
    }

    @Test
    public void testStatsFactoryCreatesTimer() {
        String name = "reporting";
        Map<String, String> tags = new HashMap<String, String>() {{
            put("state", "finished");
            put("unit", "seconds");
        }};

        statsFactory.createTimer(name, tags);

        verify(registry, times(1)).timer(any(String.class));
    }

    @Test
    public void testStatsFactoryCreatesGauge() {
        String name = "reporting";
        Map<String, String> tags = new HashMap<String, String>() {{
            put("state", "started");
            put("type", "queue");
        }};

        statsFactory.createGauge(name, tags);

        verify(registry, times(1)).register(any(String.class), any(Gauge.class));
    }
}