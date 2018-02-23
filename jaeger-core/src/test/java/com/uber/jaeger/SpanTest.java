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

package com.uber.jaeger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uber.jaeger.baggage.BaggageRestrictionManager;
import com.uber.jaeger.baggage.DefaultBaggageRestrictionManager;
import com.uber.jaeger.baggage.Restriction;
import com.uber.jaeger.metrics.InMemoryMetricsFactory;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.utils.Clock;
import io.opentracing.References;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SpanTest {
  private Clock clock;
  private InMemoryReporter reporter;
  private Tracer tracer;
  private Span span;
  private InMemoryMetricsFactory metricsFactory;
  private Metrics metrics;

  @Before
  public void setUp() {
    metricsFactory = new InMemoryMetricsFactory();
    reporter = new InMemoryReporter();
    clock = mock(Clock.class);
    metrics = new Metrics(metricsFactory);
    tracer =
        new Tracer.Builder("SamplerTest", reporter, new ConstSampler(true))
            .withMetrics(metrics)
            .withClock(clock)
            .withBaggageRestrictionManager(new DefaultBaggageRestrictionManager())
            .withExpandExceptionLogs()
            .build();
    span = (Span) tracer.buildSpan("some-operation").start();
  }

  @Test
  public void testSpanMetrics() {
    assertEquals(1, metricsFactory.getCounter("jaeger:started_spans", "sampled=y"));
    assertEquals(1, metricsFactory.getCounter("jaeger:traces", "sampled=y,state=started"));
  }

  @Test
  public void testSetAndGetBaggageItem() {
    final String service = "SamplerTest";
    final BaggageRestrictionManager mgr = Mockito.mock(DefaultBaggageRestrictionManager.class);
    tracer =
        new Tracer.Builder(service, reporter, new ConstSampler(true))
            .withClock(clock)
            .withBaggageRestrictionManager(mgr)
            .build();
    span = (Span) tracer.buildSpan("some-operation").start();

    final String key = "key";
    final String value = "value";
    when(mgr.getRestriction(service, key)).thenReturn(Restriction.of(true, 10));
    span.setBaggageItem(key, "value");
    verify(mgr).getRestriction(service, key);
    assertEquals(value, span.getBaggageItem(key));
  }

  @Test
  public void testSetBooleanTag() {
    Boolean expected = true;
    String key = "tag.key";

    span.setTag(key, expected);
    assertEquals(expected, span.getTags().get(key));
  }

  @Test
  public void testSetOperationName() {
    String expected = "modified.operation";

    assertEquals("some-operation", span.getOperationName());
    span.setOperationName(expected);
    assertEquals(expected, span.getOperationName());
  }

  @Test
  public void testSetStringTag() {
    String expected = "expected.value";
    String key = "tag.key";

    span.setTag(key, expected);
    assertEquals(expected, span.getTags().get(key));
  }

  @Test
  public void testSetNumberTag() {
    Integer expected = 5;
    String key = "tag.key";

    span.setTag(key, expected);
    assertEquals(expected, span.getTags().get(key));
  }

  @Test
  public void testWithTimestampAccurateClock() {
    testWithTimestamp(true);
  }

  @Test
  public void testWithTimestampInaccurateClock() {
    testWithTimestamp(false);
  }

  private void testWithTimestamp(boolean accurate) {
    when(clock.isMicrosAccurate()).thenReturn(accurate);
    when(clock.currentTimeMicros())
        .thenThrow(new IllegalStateException("currentTimeMicros() called"));
    when(clock.currentNanoTicks())
        .thenThrow(new IllegalStateException("currentNanoTicks() called"));

    Span span = (Span) tracer.buildSpan("test-service-name").withStartTimestamp(567).start();
    span.finish(999);

    assertEquals(1, reporter.getSpans().size());
    assertEquals(567, span.getStart());
    assertEquals(999 - 567, span.getDuration());
  }

  @Test
  public void testMultipleSpanFinishDoesNotCauseMultipleReportCalls() {
    Span span = (Span) tracer.buildSpan("test-service-name").start();
    span.finish();

    assertEquals(1, reporter.getSpans().size());

    Span reportedSpan = reporter.getSpans().get(0);

    // new finish calls will not affect size of reporter.getSpans()
    span.finish();

    assertEquals(1, reporter.getSpans().size());
    assertEquals(reportedSpan, reporter.getSpans().get(0));
  }

  @Test
  public void testWithoutTimestampsAccurateClock() {
    when(clock.isMicrosAccurate()).thenReturn(true);
    when(clock.currentTimeMicros()).thenReturn(1L).thenReturn(5L);
    when(clock.currentNanoTicks())
        .thenThrow(new IllegalStateException("currentNanoTicks() called"));

    Span span = (Span) tracer.buildSpan("test-service-name").start();
    span.finish();

    assertEquals(1, reporter.getSpans().size());
    assertEquals(1, span.getStart());
    assertEquals(4, span.getDuration());
  }

  @Test
  public void testWithoutTimestampsInaccurateClock() {
    when(clock.isMicrosAccurate()).thenReturn(false);
    when(clock.currentTimeMicros())
        .thenReturn(100L)
        .thenThrow(new IllegalStateException("currentTimeMicros() called 2nd time"));
    when(clock.currentNanoTicks()).thenReturn(20000L).thenReturn(30000L);

    Span span = (Span) tracer.buildSpan("test-service-name").start();
    span.finish();

    assertEquals(1, reporter.getSpans().size());
    assertEquals(100, span.getStart());
    assertEquals(10, span.getDuration());
  }

  @Test
  public void testSpanToString() {
    Span span = (Span) tracer.buildSpan("test-operation").start();
    SpanContext expectedContext = span.context();
    SpanContext actualContext = SpanContext.contextFromString(span.context().contextAsString());

    assertEquals(expectedContext.getTraceId(), actualContext.getTraceId());
    assertEquals(expectedContext.getSpanId(), actualContext.getSpanId());
    assertEquals(expectedContext.getParentId(), actualContext.getParentId());
    assertEquals(expectedContext.getFlags(), actualContext.getFlags());
  }

  @Test
  public void testOperationName() {
    String expectedOperation = "leela";
    Span span = (Span) tracer.buildSpan(expectedOperation).start();
    assertEquals(expectedOperation, span.getOperationName());
  }

  @Test
  public void testLogWithTimestamp() {
    long expectedTimestamp = 2222;
    final String expectedLog = "some-log";
    final String expectedEvent = "event";
    Map<String, String> expectedFields = new HashMap<String, String>() {
      {
        put(expectedEvent, expectedLog);
      }
    };

    span.log(expectedTimestamp, expectedEvent);
    span.log(expectedTimestamp, expectedFields);
    span.log(expectedTimestamp, (String) null);
    span.log(expectedTimestamp, (Map<String, ?>) null);

    LogData actualLogData = span.getLogs().get(0);

    assertEquals(expectedTimestamp, actualLogData.getTime());
    assertEquals(expectedEvent, actualLogData.getMessage());

    actualLogData = span.getLogs().get(1);

    assertEquals(expectedTimestamp, actualLogData.getTime());
    assertNull(actualLogData.getMessage());
    assertEquals(expectedFields, actualLogData.getFields());
  }

  @Test
  public void testLog() {
    final long expectedTimestamp = 2222;
    final String expectedLog = "some-log";
    final String expectedEvent = "expectedEvent";

    when(clock.currentTimeMicros()).thenReturn(expectedTimestamp);

    span.log(expectedEvent);

    Map<String, String> expectedFields = new HashMap<String, String>() {
      {
        put(expectedEvent, expectedLog);
      }
    };
    span.log(expectedFields);
    span.log((String) null);
    span.log((Map<String, ?>) null);

    LogData actualLogData = span.getLogs().get(0);

    assertEquals(expectedTimestamp, actualLogData.getTime());
    assertEquals(expectedEvent, actualLogData.getMessage());

    actualLogData = span.getLogs().get(1);

    assertEquals(expectedTimestamp, actualLogData.getTime());
    assertNull(actualLogData.getMessage());
    assertEquals(expectedFields, actualLogData.getFields());
  }

  @Test
  public void testSpanDetectsSamplingPriorityGreaterThanZero() {
    Span span = (Span) tracer.buildSpan("test-service-operation").start();
    Tags.SAMPLING_PRIORITY.set(span, 1);

    assertEquals(span.context().getFlags() & SpanContext.flagSampled, SpanContext.flagSampled);
    assertEquals(span.context().getFlags() & SpanContext.flagDebug, SpanContext.flagDebug);
  }

  @Test
  public void testSpanDetectsSamplingPriorityLessThanZero() {
    Span span = (Span) tracer.buildSpan("test-service-operation").start();

    assertEquals(span.context().getFlags() & SpanContext.flagSampled, SpanContext.flagSampled);
    Tags.SAMPLING_PRIORITY.set(span, -1);
    assertEquals(span.context().getFlags() & SpanContext.flagSampled, 0);
  }

  @Test
  public void testBaggageOneReference() {
    io.opentracing.Span parent = tracer.buildSpan("foo").start();
    parent.setBaggageItem("foo", "bar");

    io.opentracing.Span child = tracer.buildSpan("foo")
        .asChildOf(parent)
        .start();

    child.setBaggageItem("a", "a");

    assertNull(parent.getBaggageItem("a"));
    assertEquals("a", child.getBaggageItem("a"));
    assertEquals("bar", child.getBaggageItem("foo"));
  }

  @Test
  public void testBaggageMultipleReferences() {
    io.opentracing.Span parent1 = tracer.buildSpan("foo").start();
    parent1.setBaggageItem("foo", "bar");
    io.opentracing.Span parent2 = tracer.buildSpan("foo").start();
    parent2.setBaggageItem("foo2", "bar");

    io.opentracing.Span child = tracer.buildSpan("foo")
        .asChildOf(parent1)
        .addReference(References.FOLLOWS_FROM, parent2.context())
        .start();

    child.setBaggageItem("a", "a");
    child.setBaggageItem("foo2", "b");

    assertNull(parent1.getBaggageItem("a"));
    assertNull(parent2.getBaggageItem("a"));
    assertEquals("a", child.getBaggageItem("a"));
    assertEquals("bar", child.getBaggageItem("foo"));
    assertEquals("b", child.getBaggageItem("foo2"));
  }

  @Test
  public void testImmutableBaggage() {
    io.opentracing.Span span = tracer.buildSpan("foo").start();
    span.setBaggageItem("foo", "bar");
    {
      Iterator<Entry<String, String>> baggageIter = span.context().baggageItems().iterator();
      baggageIter.next();
      baggageIter.remove();
    }

    Iterator<Entry<String, String>> baggageIter = span.context().baggageItems().iterator();
    baggageIter.next();
    assertFalse(baggageIter.hasNext());
  }

  @Test
  public void testExpandExceptionLogs() {
    RuntimeException ex = new RuntimeException(new NullPointerException("npe"));
    Map<String, Object> logs = new HashMap<>();
    logs.put(Fields.ERROR_OBJECT, ex);
    Span span = (Span)tracer.buildSpan("foo").start();
    span.log(logs);

    List<LogData> logData = span.getLogs();
    assertEquals(1, logData.size());
    assertEquals(4, logData.get(0).getFields().size());

    assertEquals(ex, logData.get(0).getFields().get(Fields.ERROR_OBJECT));
    assertEquals(ex.getMessage(), logData.get(0).getFields().get(Fields.MESSAGE));
    assertEquals(ex.getClass().getName(), logData.get(0).getFields().get(Fields.ERROR_KIND));
    StringWriter sw = new StringWriter();
    ex.printStackTrace(new PrintWriter(sw));
    assertEquals(sw.toString(), logData.get(0).getFields().get(Fields.STACK));
  }

  @Test
  public void testExpandExceptionLogsExpanded() {
    RuntimeException ex = new RuntimeException(new NullPointerException("npe"));
    Map<String, Object> logs = new HashMap<>();
    logs.put(Fields.ERROR_OBJECT, ex);
    logs.put(Fields.MESSAGE, ex.getMessage());
    logs.put(Fields.ERROR_KIND, ex.getClass().getName());
    StringWriter sw = new StringWriter();
    ex.printStackTrace(new PrintWriter(sw));
    logs.put(Fields.STACK, sw.toString());
    Span span = (Span)tracer.buildSpan("foo").start();
    span.log(logs);

    List<LogData> logData = span.getLogs();
    assertEquals(1, logData.size());
    assertEquals(4, logData.get(0).getFields().size());

    assertEquals(ex, logData.get(0).getFields().get(Fields.ERROR_OBJECT));
    assertEquals(ex.getMessage(), logData.get(0).getFields().get(Fields.MESSAGE));
    assertEquals(ex.getClass().getName(), logData.get(0).getFields().get(Fields.ERROR_KIND));
    assertEquals(sw.toString(), logData.get(0).getFields().get(Fields.STACK));
  }

  @Test
  public void testExpandExceptionLogsLoggedNoException() {
    Span span = (Span)tracer.buildSpan("foo").start();

    Object object = new Object();
    Map<String, Object> logs = new HashMap<>();
    logs.put(Fields.ERROR_OBJECT, object);
    span.log(logs);

    List<LogData> logData = span.getLogs();
    assertEquals(1, logData.size());
    assertEquals(1, logData.get(0).getFields().size());
    assertEquals(object, logData.get(0).getFields().get(Fields.ERROR_OBJECT));
  }

  @Test
  public void testNoExpandExceptionLogs() {
    Tracer tracer = new Tracer.Builder("fo", reporter, new ConstSampler(true))
        .build();

    Span span = (Span)tracer.buildSpan("foo").start();

    RuntimeException ex = new RuntimeException();
    Map<String, Object> logs = new HashMap<>();
    logs.put(Fields.ERROR_OBJECT, ex);
    span.log(logs);

    List<LogData> logData = span.getLogs();
    assertEquals(1, logData.size());
    assertEquals(1, logData.get(0).getFields().size());
    assertEquals(ex, logData.get(0).getFields().get(Fields.ERROR_OBJECT));
  }

  @Test
  public void testSpanNotSampled() {
    Tracer tracer = new Tracer.Builder("fo", reporter, new ConstSampler(false))
        .build();
    io.opentracing.Span foo = tracer.buildSpan("foo")
        .start();
    foo.log(Collections.emptyMap())
        .finish();
    assertEquals(0, reporter.getSpans().size());
  }
}
