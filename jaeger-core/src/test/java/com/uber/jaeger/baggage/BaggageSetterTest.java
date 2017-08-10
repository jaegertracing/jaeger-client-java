/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger.baggage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uber.jaeger.LogData;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class BaggageSetterTest {

  private InMemoryReporter reporter;
  private Tracer tracer;
  private Span span;
  private InMemoryStatsReporter metricsReporter;
  private Metrics metrics;
  private BaggageRestrictionManager mgr;
  private BaggageSetter setter;

  private static final String KEY = "key";
  private static final String SERVICE = "SamplerTest";

  @Before
  public void setUp() throws Exception {
    metricsReporter = new InMemoryStatsReporter();
    reporter = new InMemoryReporter();
    metrics = new Metrics(new StatsFactoryImpl(metricsReporter));
    mgr = mock(DefaultBaggageRestrictionManager.class);
    setter = new BaggageSetter(mgr, metrics);
    tracer =
        new Tracer.Builder(SERVICE, reporter, new ConstSampler(true))
            .withStatsReporter(metricsReporter)
            .build();
    span = (Span) tracer.buildSpan("some-operation").startManual();
  }

  @Test
  public void testInvalidBaggage() {
    when(mgr.getRestriction(SERVICE, KEY)).thenReturn(Restriction.of(false, 0));

    final String value = "value";
    SpanContext ctx = setter.setBaggage(span, KEY, value);

    assertBaggageLogs(span, KEY, value, false, false, true);
    assertNull(ctx.getBaggageItem(KEY));

    assertEquals(
        1L, metricsReporter.counters.get("jaeger.baggage-update.result=err").longValue());
  }

  @Test
  public void testTruncatedBaggage() {
    when(mgr.getRestriction(SERVICE, KEY)).thenReturn(Restriction.of(true, 5));
    final String value = "0123456789";
    final String expected = "01234";
    SpanContext ctx = setter.setBaggage(span, KEY, value);

    assertBaggageLogs(span, KEY, expected, true, false, false);
    assertEquals(expected, ctx.getBaggageItem(KEY));

    assertEquals(
        1L, metricsReporter.counters.get("jaeger.baggage-truncate").longValue());
    assertEquals(
        1L, metricsReporter.counters.get("jaeger.baggage-update.result=ok").longValue());
  }

  @Test
  public void testOverrideBaggage() {
    when(mgr.getRestriction(SERVICE, KEY)).thenReturn(Restriction.of(true, 5));
    final String value = "value";
    SpanContext ctx = setter.setBaggage(span, KEY, value);
    Span child = (Span) tracer.buildSpan("some-operation").asChildOf(ctx).startManual();
    ctx = setter.setBaggage(child, KEY, value);

    assertBaggageLogs(child, KEY, value, false, true, false);
    assertEquals(value, ctx.getBaggageItem(KEY));

    assertEquals(
        2L, metricsReporter.counters.get("jaeger.baggage-update.result=ok").longValue());
  }

  @Test
  public void testUnsampledSpan() {
    tracer =
        new Tracer.Builder("SamplerTest", reporter, new ConstSampler(false))
            .withStatsReporter(metricsReporter)
            .build();
    span = (Span) tracer.buildSpan("some-operation").startManual();

    when(mgr.getRestriction(SERVICE, KEY)).thenReturn(Restriction.of(true, 5));
    final String value = "value";
    SpanContext ctx = setter.setBaggage(span, KEY, value);

    assertEquals(value, ctx.getBaggageItem(KEY));
    // No logs should be written if the span is not sampled
    assertNull(span.getLogs());
  }

  private void assertBaggageLogs(
      Span span,
      String key,
      String value,
      boolean truncate,
      boolean override,
      boolean invalid
  ) {
    List<LogData> logs = span.getLogs();
    assertEquals(false, logs.isEmpty());
    Map<String, ?> fields = logs.get(logs.size() - 1).getFields();
    assertEquals("baggage", fields.get("event"));
    assertEquals(key, fields.get("key"));
    assertEquals(value, fields.get("value"));
    if (truncate) {
      assertEquals("true", fields.get("truncated"));
    }
    if (override) {
      assertEquals("true", fields.get("override"));
    }
    if (invalid) {
      assertEquals("true", fields.get("invalid"));
    }
  }
}
