/*
 * Copyright (c) 2021, The Jaeger Authors
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

package io.jaegertracing.internal.reporters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.spi.Reporter;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class FilteringReporterTest {
  private Reporter reporter;
  private InMemoryReporter delegate;
  private final long filterThreshold = 5; // in microseconds
  private final long deferThreshold = 20; // in microseconds
  private Metrics metrics;
  private InMemoryMetricsFactory metricsFactory;

  @Before
  public void setUp() {
    metricsFactory = new InMemoryMetricsFactory();
    metrics = new Metrics(metricsFactory);

    delegate = spy(new InMemoryReporter());

    reporter = new FilteringReporter(delegate, filterThreshold, deferThreshold, metrics);
  }

  @Test
  public void testUnfilteredNondeferredSpan() {
    // Given:
    JaegerSpan mockSpan = mockSpan(1L, 1000L);

    // When:
    reporter.report(mockSpan);

    // Then:
    assertEquals(1, delegate.getSpans().size());
    assertEquals(mockSpan, delegate.getSpans().get(0));
    assertMetrics(expect().spansPending(0).spansFiltered(0).spansStarted(0).spansDropped(0).spansSent(0));
  }

  @Test
  public void testSpanFiltered() {
    // Given:
    JaegerSpan mockSpan = mockSpan(1L, 1L);

    // When:
    reporter.report(mockSpan);

    // Then:
    assertTrue(delegate.getSpans().isEmpty());
    assertMetrics(expect().spansPending(0).spansFiltered(1).spansStarted(0).spansDropped(0).spansSent(0));
  }

  @Test
  public void testSpanDeferredThenSent() {
    // Given:
    JaegerSpan mockSpan = mockSpan(1L, 100L);

    JaegerSpan mockChildSpan = mockSpan(2L, 1L, 10L);

    // When:
    reporter.report(mockChildSpan);

    // Then:
    assertTrue(delegate.getSpans().isEmpty());
    assertMetrics(expect().spansPending(1).spansFiltered(0).spansStarted(1).spansDropped(0).spansSent(0));

    // When:
    reporter.report(mockSpan);

    // Then:
    assertEquals(2, delegate.getSpans().size());
    assertEquals(mockChildSpan, delegate.getSpans().get(0));
    assertEquals(mockSpan, delegate.getSpans().get(1));
    assertMetrics(expect().spansPending(0).spansFiltered(0).spansStarted(1).spansDropped(0).spansSent(1));
  }

  @Test
  public void testSpanDeferredThenDropped() {
    // Given:
    JaegerSpan mockSpan = mockSpan(1L, 15L);
    JaegerSpan mockChildSpan = mockSpan(2L, 1L, 10L);

    // When:
    reporter.report(mockChildSpan);

    // Then:
    assertTrue(delegate.getSpans().isEmpty());
    assertMetrics(expect().spansPending(1).spansFiltered(0).spansStarted(1).spansDropped(0).spansSent(0));

    // When:
    reporter.report(mockSpan);

    // Then:
    assertEquals(0, delegate.getSpans().size());
    assertMetrics(expect().spansPending(0).spansFiltered(0).spansStarted(1).spansDropped(1).spansSent(0));
  }

  @Test
  public void testDeferredGrandchildren() {
    // Given:
    final JaegerSpan rootSpan = mockSpan(1L, 5000L);
    final JaegerSpan firstChild = mockSpan(10L, 1L, 19L);
    final JaegerSpan firstGrandchild = mockSpan(11L, 10L, 9L);
    final JaegerSpan secondChild = mockSpan(20L, 1L, 19L);
    final JaegerSpan secondGrandchild = mockSpan(21L, 20L, 9L);

    // When:
    reporter.report(firstGrandchild);
    reporter.report(secondGrandchild);
    reporter.report(firstChild);
    reporter.report(secondChild);
    reporter.report(rootSpan);

    // Then:
    assertEquals(5, delegate.getSpans().size());
    assertEquals(firstGrandchild, delegate.getSpans().get(0));
    assertEquals(firstChild, delegate.getSpans().get(1));
    assertEquals(secondGrandchild, delegate.getSpans().get(2));
    assertEquals(secondChild, delegate.getSpans().get(3));
    assertEquals(rootSpan, delegate.getSpans().get(4));
    assertMetrics(expect().spansPending(0).spansFiltered(0).spansStarted(4).spansDropped(0).spansSent(4));
  }

  @Test
  public void testClose() {
    // Given:
    final JaegerSpan deferredSpan = mockSpan(2L, 1L, 15L);

    // When:
    reporter.report(deferredSpan);
    reporter.close();

    // Then:
    verify(delegate).close();
    assertMetrics(expect().spansPending(0).spansFiltered(0).spansStarted(1).spansDropped(1).spansSent(0));
  }

  @Test
  public void testSetOrUpdateParentsPendingSpans_noChildren_firstSibling() {
    // Given:
    final boolean hasPendingChildren = false;
    final List<JaegerSpan> pendingOnThisSpan = null;
    final List<JaegerSpan> pendingOnParentSpan = null;

    // When:
    final List<JaegerSpan> spans = FilteringReporter.setOrUpdateParentsPendingSpans(hasPendingChildren,
        pendingOnThisSpan, pendingOnParentSpan);

    // Then:
    assertNotNull("A new list was not created", spans);
    assertTrue("New list should be empty", spans.isEmpty());
  }

  @Test
  public void testSetOrUpdateParentsPendingSpans_hasChildren_firstSibling() {
    // Given:
    final boolean hasPendingChildren = true;
    final List<JaegerSpan> pendingOnThisSpan = new ArrayList<>(1);
    final JaegerSpan pendingChild = mockSpan(10, 1, 1);
    pendingOnThisSpan.add(pendingChild);
    final List<JaegerSpan> pendingOnParentSpan = null;

    // When:
    final List<JaegerSpan> spans = FilteringReporter.setOrUpdateParentsPendingSpans(hasPendingChildren,
        pendingOnThisSpan, pendingOnParentSpan);

    // Then:
    assertEquals("Existing pending children list not promoted", pendingOnThisSpan, spans);
    assertEquals("Pending child not found", pendingChild, spans.get(0));
  }

  @Test
  public void testSetOrUpdateParentsPendingSpans_noChildren_laterSibling() {
    // Given:
    final boolean hasPendingChildren = false;
    final List<JaegerSpan> pendingOnThisSpan = null;
    final List<JaegerSpan> pendingOnParentSpan = new ArrayList<>(1);
    final JaegerSpan pending = mockSpan(20, 1, 1);
    pendingOnParentSpan.add(pending);

    // When:
    final List<JaegerSpan> spans = FilteringReporter.setOrUpdateParentsPendingSpans(hasPendingChildren,
        pendingOnThisSpan, pendingOnParentSpan);

    // Then:
    assertEquals("Existing list not reused", pendingOnParentSpan, spans);
    assertEquals("Pending span not found", pending, spans.get(0));
  }

  @Test
  public void testSetOrUpdateParentsPendingSpans_hasChildren_laterSibling() {
    // Given:
    final boolean hasPendingChildren = true;
    final List<JaegerSpan> pendingOnThisSpan = new ArrayList<>(1);
    final JaegerSpan pendingChild = mockSpan(10, 1, 1);
    pendingOnThisSpan.add(pendingChild);
    final List<JaegerSpan> pendingOnParentSpan = new ArrayList<>(1);
    final JaegerSpan pending = mockSpan(20, 1, 1);
    pendingOnParentSpan.add(pending);

    // When:
    final List<JaegerSpan> spans = FilteringReporter.setOrUpdateParentsPendingSpans(hasPendingChildren,
        pendingOnThisSpan, pendingOnParentSpan);

    // Then:
    assertEquals("Existing list not reused", pendingOnParentSpan, spans);
    assertEquals("Pending span not found", pending, spans.get(0));
    assertEquals("Pending child not copied over", pendingChild, spans.get(1));
  }

  private JaegerSpan mockSpan(long spanId, long duration) {
    return mockSpan(spanId, 0L, duration);
  }

  private JaegerSpan mockSpan(long spanId, long parentSpanId, long duration) {
    JaegerSpan mockChildSpan = mock(JaegerSpan.class);
    JaegerSpanContext mockChildContext = mock(JaegerSpanContext.class);
    when(mockChildContext.getSpanId()).thenReturn(spanId);
    when(mockChildContext.getParentId()).thenReturn(parentSpanId);
    when(mockChildSpan.context()).thenReturn(mockChildContext);
    when(mockChildSpan.getDuration()).thenReturn(duration);
    return mockChildSpan;
  }

  private void assertMetrics(final ExpectedMetrics expectedMetrics) {
    if (expectedMetrics.getSpansPending() != null) {
      assertEquals("incorrect metric for spans pending",
          expectedMetrics.getSpansPending().longValue(),
          metricsFactory.getGauge("jaeger_tracer_deferred_spans_pending", ""));
    }
    if (expectedMetrics.getSpansFiltered() != null) {
      assertEquals("incorrect metric for spans filtered",
          expectedMetrics.getSpansFiltered().longValue(),
          metricsFactory.getCounter("jaeger_tracer_filtered_spans", ""));
    }
    if (expectedMetrics.getSpansStarted() != null) {
      assertEquals("incorrect metric for spans started",
          expectedMetrics.getSpansStarted().longValue(),
          metricsFactory.getCounter("jaeger_tracer_deferred_spans_started", ""));
    }
    if (expectedMetrics.getSpansDropped() != null) {
      assertEquals("incorrect metric for spans dropped",
          expectedMetrics.getSpansDropped().longValue(),
          metricsFactory.getCounter("jaeger_tracer_deferred_spans_finished", "disposition=dropped"));
    }
    if (expectedMetrics.getSpansSent() != null) {
      assertEquals("incorrect metric for spans sent",
          expectedMetrics.getSpansSent().longValue(),
          metricsFactory.getCounter("jaeger_tracer_deferred_spans_finished", "disposition=sent"));
    }
  }

  private static final class ExpectedMetrics {
    private Long spansPending;
    private Long spansFiltered;
    private Long spansStarted;
    private Long spansDropped;
    private Long spansSent;

    public ExpectedMetrics spansPending(long expect) {
      spansPending = expect;
      return this;
    }

    public Long getSpansPending() {
      return spansPending;
    }

    public ExpectedMetrics spansFiltered(long expect) {
      spansFiltered = expect;
      return this;
    }

    public Long getSpansFiltered() {
      return spansFiltered;
    }

    public ExpectedMetrics spansStarted(long expect) {
      spansStarted = expect;
      return this;
    }

    public Long getSpansStarted() {
      return spansStarted;
    }

    public ExpectedMetrics spansDropped(long expect) {
      spansDropped = expect;
      return this;
    }

    public Long getSpansDropped() {
      return spansDropped;
    }

    public ExpectedMetrics spansSent(long expect) {
      spansSent = expect;
      return this;
    }

    public Long getSpansSent() {
      return spansSent;
    }
  }

  private static ExpectedMetrics expect() {
    return new ExpectedMetrics();
  }
}
