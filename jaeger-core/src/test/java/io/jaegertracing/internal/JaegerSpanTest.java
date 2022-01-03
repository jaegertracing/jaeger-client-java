/*
 * Copyright (c) 2018, The Jaeger Authors
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

package io.jaegertracing.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jaegertracing.internal.baggage.DefaultBaggageRestrictionManager;
import io.jaegertracing.internal.baggage.Restriction;
import io.jaegertracing.internal.clock.Clock;
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.internal.utils.Utils;
import io.jaegertracing.spi.BaggageRestrictionManager;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.log.Fields;
import io.opentracing.noop.NoopSpan;
import io.opentracing.tag.AbstractTag;
import io.opentracing.tag.BooleanTag;
import io.opentracing.tag.IntTag;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tags;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class JaegerSpanTest {
  private Clock clock;
  private InMemoryReporter reporter;
  private JaegerTracer tracer;
  private JaegerTracer tracer128;
  private JaegerSpan jaegerSpan;
  private InMemoryMetricsFactory metricsFactory;
  private Metrics metrics;

  @Before
  public void setUp() {
    metricsFactory = new InMemoryMetricsFactory();
    reporter = new InMemoryReporter();
    clock = mock(Clock.class);
    metrics = new Metrics(metricsFactory);
    final JaegerTracer.Builder tracerBuilder = new JaegerTracer.Builder("SamplerTest")
        .withReporter(reporter)
        .withSampler(new ConstSampler(true))
        .withMetrics(metrics)
        .withClock(clock)
        .withBaggageRestrictionManager(new DefaultBaggageRestrictionManager())
        .withExpandExceptionLogs();
    tracer = tracerBuilder.build();
    tracer128 = tracerBuilder.withTraceId128Bit().build();
    jaegerSpan = tracer.buildSpan("some-operation").start();
  }

  @Test
  public void testTraceId64Bit() {
    tracer = new JaegerTracer.Builder("traceId64Bit").build();
    jaegerSpan = tracer.buildSpan("foo").start();
    assertEquals(0, jaegerSpan.context().getTraceIdHigh());
  }

  @Test
  public void testTraceId128Bit() {
    tracer = new JaegerTracer.Builder("traceId128Bit").withTraceId128Bit().build();
    jaegerSpan = tracer.buildSpan("foo").start();
    assertNotEquals(0, jaegerSpan.context().getTraceIdHigh());
  }


  @Test
  public void testSpanMetrics() {
    assertEquals(1, metricsFactory.getCounter("jaeger_tracer_started_spans", "sampled=y"));
    assertEquals(1, metricsFactory.getCounter("jaeger_tracer_traces", "sampled=y,state=started"));
  }

  @Test
  public void testSetAndGetBaggageItem() {
    final String service = "SamplerTest";
    final BaggageRestrictionManager mgr = Mockito.mock(DefaultBaggageRestrictionManager.class);
    tracer = new JaegerTracer.Builder(service)
        .withReporter(reporter)
        .withSampler(new ConstSampler(true))
        .withClock(clock)
        .withBaggageRestrictionManager(mgr)
        .build();
    jaegerSpan = tracer.buildSpan("some-operation").start();

    final String key = "key";
    final String value = "value";
    when(mgr.getRestriction(service, key)).thenReturn(Restriction.of(true, 10));
    jaegerSpan.setBaggageItem(key, "value");
    verify(mgr).getRestriction(service, key);
    assertEquals(value, jaegerSpan.getBaggageItem(key));
  }

  @Test
  public void testSetNullTag() {
    String key = "tag.key";

    jaegerSpan.setTag(key, (String)null);
    assertNull(jaegerSpan.getTags().get(key));
    assertTrue(jaegerSpan.getTags().containsKey(key));
  }

  @Test
  public void testSetBooleanTag() {
    Boolean expected = true;
    String key = "tag.key";

    jaegerSpan.setTag(key, expected);
    assertEquals(expected, jaegerSpan.getTags().get(key));
  }

  @Test
  public void testSetOperationName() {
    String expected = "modified.operation";

    assertEquals("some-operation", jaegerSpan.getOperationName());
    jaegerSpan.setOperationName(expected);
    assertEquals(expected, jaegerSpan.getOperationName());
  }

  @Test
  public void testSetStringTag() {
    String expected = "expected.value";
    String key = "tag.key";

    jaegerSpan.setTag(key, expected);
    assertEquals(expected, jaegerSpan.getTags().get(key));
  }

  @Test
  public void testSetNumberTag() {
    Integer expected = 5;
    String key = "tag.key";

    jaegerSpan.setTag(key, expected);
    assertEquals(expected, jaegerSpan.getTags().get(key));
  }

  @Test
  public void testFinished() {
    jaegerSpan.finish();
    assertTrue(jaegerSpan.isFinished());
  }

  @Test
  public void testSetTag() {
    jaegerSpan.setTag(new StringTag("stringTag"), "stringTagValue")
        .setTag(new IntTag("numberTag"), 1)
        .setTag(new BooleanTag("booleanTag"), true)
        .setTag(new AbstractTag<Object>("objectTag") {
          @Override
          public void set(Span span, Object tagValue) {
          }
        }, this);

    Map<String, Object> tags = jaegerSpan.getTags();
    assertEquals("stringTagValue", tags.get("stringTag"));
    assertEquals(1, tags.get("numberTag"));
    assertEquals(true, tags.get("booleanTag"));
    assertEquals(this, tags.get("objectTag"));
  }

  @Test
  public void testToTraceId() {
    assertEquals(jaegerSpan.context().getTraceId(), jaegerSpan.context().toTraceId());
  }

  @Test
  public void testToSpanId() {
    assertEquals(Utils.to16HexString(jaegerSpan.context().getSpanId()), jaegerSpan.context().toSpanId());
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

    long start = 1000000000000567L;
    long end = 1000000000000999L;

    JaegerSpan jaegerSpan = tracer.buildSpan("test-service-name").withStartTimestamp(start).start();
    jaegerSpan.finish(end);

    assertEquals(1, reporter.getSpans().size());
    assertEquals(start, jaegerSpan.getStart());
    assertEquals(end - start, jaegerSpan.getDuration());
  }

  @Test
  public void testInvalidStartTimestampMicros() {
    long start = 567L;
    JaegerSpan jaegerSpan = tracer.buildSpan("test-service-name").withStartTimestamp(start).start();
    jaegerSpan.finish();

    assertEquals(1, reporter.getSpans().size());
    assertEquals(start, jaegerSpan.getStart());
  }

  @Test
  public void testMultipleSpanFinishDoesNotCauseMultipleReportCalls() {
    JaegerSpan jaegerSpan = tracer.buildSpan("test-service-name").start();
    jaegerSpan.finish();

    assertEquals(1, reporter.getSpans().size());

    JaegerSpan reportedJaegerSpan = reporter.getSpans().get(0);

    // new finish calls will not affect size of reporter.getJaegerSpans()
    jaegerSpan.finish();

    assertEquals(1, reporter.getSpans().size());
    assertEquals(reportedJaegerSpan, reporter.getSpans().get(0));
  }

  @Test
  public void testWithoutTimestampsAccurateClock() {
    when(clock.isMicrosAccurate()).thenReturn(true);
    when(clock.currentTimeMicros()).thenReturn(1L).thenReturn(5L);
    when(clock.currentNanoTicks())
        .thenThrow(new IllegalStateException("currentNanoTicks() called"));

    JaegerSpan jaegerSpan = tracer.buildSpan("test-service-name").start();
    jaegerSpan.finish();

    assertEquals(1, reporter.getSpans().size());
    assertEquals(1, jaegerSpan.getStart());
    assertEquals(4, jaegerSpan.getDuration());
  }

  @Test
  public void testWithoutTimestampsInaccurateClock() {
    when(clock.isMicrosAccurate()).thenReturn(false);
    when(clock.currentTimeMicros())
        .thenReturn(100L)
        .thenThrow(new IllegalStateException("currentTimeMicros() called 2nd time"));
    when(clock.currentNanoTicks()).thenReturn(20000L).thenReturn(30000L);

    JaegerSpan jaegerSpan = tracer.buildSpan("test-operation").start();
    jaegerSpan.finish();

    assertEquals(1, reporter.getSpans().size());
    assertEquals(100, jaegerSpan.getStart());
    assertEquals(10, jaegerSpan.getDuration());
  }

  @Test
  public void testSpanToString() {
    String operation = "test-operation";
    long traceIdLow = 1L;
    long spanId = 2L;
    long parentId = 3L;
    JaegerSpan span = new JaegerSpan(
        tracer,
        operation,
        new JaegerSpanContext(
            0, traceIdLow, spanId, parentId,
            (byte) 4, Collections.emptyMap(), null /* debugId */, new JaegerObjectFactory()),
        0,
        0,
        false,
        Collections.emptyMap(),
        Collections.emptyList());
    assertEquals(
        "0000000000000001:0000000000000002:0000000000000003:4 - test-operation",
        span.toString());
    span.finish();
  }

  @Test
  public void testSpanToStringWith128BitTraceId() {
    String operation = "test-operation";
    long traceIdLow = 1L;
    long traceIdHigh = 2L;
    long spanId = 3L;
    long parentId = 4L;
    JaegerSpan span = new JaegerSpan(
        tracer128,
        operation,
        new JaegerSpanContext(
            traceIdHigh, traceIdLow, spanId, parentId,
            (byte) 4, Collections.emptyMap(), null /* debugId */, new JaegerObjectFactory()),
        0,
        0,
        false,
        Collections.emptyMap(),
        Collections.emptyList());
    assertEquals(
        "00000000000000020000000000000001:0000000000000003:0000000000000004:4 - test-operation",
        span.toString());
    span.finish();
  }

  @Test
  public void testOperationName() {
    String expectedOperation = "leela";
    JaegerSpan jaegerSpan = tracer.buildSpan(expectedOperation).start();
    assertEquals(expectedOperation, jaegerSpan.getOperationName());
  }

  @Test
  public void testLogWithTimestamp() {
    long expectedTimestamp = 2222;
    final String expectedLog = "some-log";
    final String expectedEvent = "event";
    Map<String, String> expectedFields = new HashMap<String, String>();
    expectedFields.put(expectedEvent, expectedLog);

    jaegerSpan.log(expectedTimestamp, expectedEvent);
    jaegerSpan.log(expectedTimestamp, expectedFields);
    jaegerSpan.log(expectedTimestamp, (String) null);
    jaegerSpan.log(expectedTimestamp, (Map<String, ?>) null);

    LogData actualLogData = jaegerSpan.getLogs().get(0);

    assertEquals(expectedTimestamp, actualLogData.getTime());
    assertEquals(expectedEvent, actualLogData.getMessage());

    actualLogData = jaegerSpan.getLogs().get(1);

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

    jaegerSpan.log(expectedEvent);

    Map<String, String> expectedFields = Collections.singletonMap(expectedEvent, expectedLog);
    jaegerSpan.log(expectedFields);
    jaegerSpan.log((String) null);
    jaegerSpan.log((Map<String, ?>) null);

    LogData actualLogData = jaegerSpan.getLogs().get(0);

    assertEquals(expectedTimestamp, actualLogData.getTime());
    assertEquals(expectedEvent, actualLogData.getMessage());

    actualLogData = jaegerSpan.getLogs().get(1);

    assertEquals(expectedTimestamp, actualLogData.getTime());
    assertNull(actualLogData.getMessage());
    assertEquals(expectedFields, actualLogData.getFields());
  }

  @Test
  public void testSpanDetectsSamplingPriorityGreaterThanZero() {
    JaegerSpan jaegerSpan = tracer.buildSpan("test-service-operation").start();
    Tags.SAMPLING_PRIORITY.set(jaegerSpan, 1);

    JaegerSpanContext context = jaegerSpan.context();
    assertEquals(context.getFlags() & JaegerSpanContext.flagSampled, JaegerSpanContext.flagSampled);
    assertEquals(context.getFlags() & JaegerSpanContext.flagDebug, JaegerSpanContext.flagDebug);
  }

  @Test
  public void testSpanDetectsSamplingPriorityLessThanZero() {
    // setup
    JaegerSpan jaegerSpan = tracer.buildSpan("test-service-operation").start();

    // sanity check
    assertEquals(
        JaegerSpanContext.flagSampled,
        jaegerSpan.context().getFlags() & JaegerSpanContext.flagSampled
    );

    // test
    Tags.SAMPLING_PRIORITY.set(jaegerSpan, -1);

    // verify
    assertEquals(
        0,
        jaegerSpan.context().getFlags() & JaegerSpanContext.flagSampled
    );
  }

  @Test
  public void testBaggageOneReference() {
    JaegerSpan parent = tracer.buildSpan("foo").start();
    parent.setBaggageItem("foo", "bar");

    JaegerSpan child = tracer.buildSpan("foo")
        .asChildOf(parent)
        .start();

    child.setBaggageItem("a", "a");

    assertNull(parent.getBaggageItem("a"));
    assertEquals("a", child.getBaggageItem("a"));
    assertEquals("bar", child.getBaggageItem("foo"));
  }

  @Test
  public void testBaggageMultipleReferences() {
    JaegerSpan parent1 = tracer.buildSpan("foo").start();
    parent1.setBaggageItem("foo", "bar");
    JaegerSpan parent2 = tracer.buildSpan("foo").start();
    parent2.setBaggageItem("foo2", "bar");

    JaegerSpan child = tracer.buildSpan("foo")
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
    JaegerSpan span = tracer.buildSpan("foo").start();
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
    JaegerSpan jaegerSpan = tracer.buildSpan("foo").start();
    jaegerSpan.log(logs);

    List<LogData> logData = jaegerSpan.getLogs();
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
    JaegerSpan jaegerSpan = tracer.buildSpan("foo").start();
    jaegerSpan.log(logs);

    List<LogData> logData = jaegerSpan.getLogs();
    assertEquals(1, logData.size());
    assertEquals(4, logData.get(0).getFields().size());

    assertEquals(ex, logData.get(0).getFields().get(Fields.ERROR_OBJECT));
    assertEquals(ex.getMessage(), logData.get(0).getFields().get(Fields.MESSAGE));
    assertEquals(ex.getClass().getName(), logData.get(0).getFields().get(Fields.ERROR_KIND));
    assertEquals(sw.toString(), logData.get(0).getFields().get(Fields.STACK));
  }

  @Test
  public void testExpandExceptionLogsLoggedNoException() {
    JaegerSpan jaegerSpan = tracer.buildSpan("foo").start();

    Object object = new Object();
    Map<String, Object> logs = new HashMap<>();
    logs.put(Fields.ERROR_OBJECT, object);
    jaegerSpan.log(logs);

    List<LogData> logData = jaegerSpan.getLogs();
    assertEquals(1, logData.size());
    assertEquals(1, logData.get(0).getFields().size());
    assertEquals(object, logData.get(0).getFields().get(Fields.ERROR_OBJECT));
  }

  @Test
  public void testNoExpandExceptionLogs() {
    JaegerTracer tracer = new JaegerTracer.Builder("fo")
        .withReporter(reporter)
        .withSampler(new ConstSampler(true))
        .build();

    JaegerSpan jaegerSpan = tracer.buildSpan("foo").start();

    RuntimeException ex = new RuntimeException();
    Map<String, Object> logs = new HashMap<>();
    logs.put(Fields.ERROR_OBJECT, ex);
    jaegerSpan.log(logs);

    List<LogData> logData = jaegerSpan.getLogs();
    assertEquals(1, logData.size());
    assertEquals(1, logData.get(0).getFields().size());
    assertEquals(ex, logData.get(0).getFields().get(Fields.ERROR_OBJECT));
  }

  @Test
  public void testSpanNotSampled() {
    JaegerTracer tracer = new JaegerTracer.Builder("fo")
        .withReporter(reporter)
        .withSampler(new ConstSampler(false))
        .build();
    JaegerSpan foo = tracer.buildSpan("foo")
        .start();
    foo.log(Collections.emptyMap())
        .finish();
    assertEquals(0, reporter.getSpans().size());
  }

  @Test
  public void testAsChildOfIgnoreUnexpectedContextImpl() {
    JaegerTracer tracer = new JaegerTracer.Builder("foo")
        .withReporter(reporter)
        .withSampler(new ConstSampler(true))
        .build();
    JaegerSpan jaegerSpan = tracer.buildSpan("foo")
        .asChildOf(NoopSpan.INSTANCE.context()).start();
    jaegerSpan.finish();
    assertTrue(jaegerSpan.getReferences().isEmpty());
  }

  @Test
  public void testAsChildOfAcceptNull() {
    JaegerTracer tracer = new JaegerTracer.Builder("foo")
        .withReporter(reporter)
        .withSampler(new ConstSampler(true))
        .build();

    JaegerSpan jaegerSpan = tracer.buildSpan("foo")
        .asChildOf((Span) null).start();
    jaegerSpan.finish();
    assertTrue(jaegerSpan.getReferences().isEmpty());

    jaegerSpan = tracer.buildSpan("foo")
        .asChildOf((SpanContext) null).start();
    jaegerSpan.finish();
    assertTrue(jaegerSpan.getReferences().isEmpty());
  }

  @Test
  public void testConcurrentModificationLogs() {
    JaegerTracer tracer = new JaegerTracer.Builder("foo")
        .withSampler(new ConstSampler(true))
        .build();

    JaegerSpan span = tracer.buildSpan("bar").start();
    span.log("foo");
    span.getLogs().forEach(spanLog -> span.log("foo2"));
  }

  @Test
  public void testConcurrentModificationTags() {
    JaegerTracer tracer = new JaegerTracer.Builder("foo")
        .withSampler(new ConstSampler(true))
        .build();

    JaegerSpan span = tracer.buildSpan("foo").start();
    span.setTag("foo", "bar");
    span.getTags().forEach((k, v) -> span.setTag("foo2", "bar"));
  }

  @Test
  public void testConcurrentModificationBaggage() {
    jaegerSpan.getTags();
    JaegerTracer tracer = new JaegerTracer.Builder("foo")
        .withSampler(new ConstSampler(true))
        .build();

    JaegerSpan span = tracer.buildSpan("foo").start();
    span.setBaggageItem("foo", "bar");
    span.context().baggageItems().forEach(entry -> span.setBaggageItem("foo2", "bar"));
  }

  @Test
  public void testSamplingTagOrder() {
    // See: https://github.com/jaegertracing/jaeger-client-java/issues/738
    // LinkedHashMap to control map iteration order
    Map<String, Object> tags = new LinkedHashMap<>();
    tags.put("tag1", "tag1");
    tags.put(Tags.SAMPLING_PRIORITY.getKey(), 1);
    tags.put("tag2", "tag2");

    String operation = "test-operation";
    long traceIdLow = 1L;
    long spanId = 2L;
    long parentId = 3L;
    JaegerSpan span = new JaegerSpan(
            tracer,
            operation,
            new JaegerSpanContext(
                    0, traceIdLow, spanId, parentId,
                    (byte) 4, Collections.emptyMap(), null /* debugId */, new JaegerObjectFactory()),
            0,
            0,
            false,
            tags,
            Collections.emptyList());

    assertTrue(span.context().isSampled());
    assertEquals(3, span.getTags().size());
  }
}
