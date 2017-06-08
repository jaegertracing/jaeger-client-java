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

package com.uber.jaeger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;

import io.opentracing.ActiveSpan;
import io.opentracing.ActiveSpanSource;
import io.opentracing.References;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PropagationTest {
  @Test
  public void testDebugCorrelationId() {
    Tracer tracer =
        new Tracer.Builder("test", new InMemoryReporter(), new ConstSampler(true)).build();
    Map<String, String> headers = new HashMap<>();
    headers.put(Constants.DEBUG_ID_HEADER_KEY, "Coraline");
    TextMap carrier = new TextMapExtractAdapter(headers);
    SpanContext spanContext = (SpanContext) tracer.extract(Format.Builtin.TEXT_MAP, carrier);
    assertTrue(spanContext.isDebugIdContainerOnly());
    assertEquals("Coraline", spanContext.getDebugId());
    Span span = (Span) tracer.buildSpan("span").asChildOf(spanContext).startManual();
    spanContext = (SpanContext) span.context();
    assertTrue(spanContext.isSampled());
    assertTrue(spanContext.isDebug());
    assertEquals("Coraline", span.getTags().get(Constants.DEBUG_ID_HEADER_KEY));
  }

  @Test
  public void testActiveSpanPropagation() {
    Tracer tracer =
        new Tracer.Builder("test", new InMemoryReporter(), new ConstSampler(true)).build();
    try (ActiveSpan parent = tracer.buildSpan("parent").startActive()) {
      assertEquals(parent, tracer.activeSpan());
    }
  }

  @Test
  public void testActiveSpanAutoReference() {
    InMemoryReporter reporter = new InMemoryReporter();
    Tracer tracer =
        new Tracer.Builder("test", reporter, new ConstSampler(true)).build();
    try (ActiveSpan parent = tracer.buildSpan("parent").startActive()) {
      tracer.buildSpan("child").startActive().deactivate();
    }
    assertEquals(2, reporter.getSpans().size());

    Span childSpan = reporter.getSpans().get(0);
    Span parentSpan = reporter.getSpans().get(1);

    assertEquals("child", childSpan.getOperationName());
    assertEquals(1, childSpan.getReferences().size());
    assertEquals("parent", parentSpan.getOperationName());
    assertTrue(parentSpan.getReferences().isEmpty());
    assertEquals(References.CHILD_OF, childSpan.getReferences().get(0).getType());
    assertEquals(parentSpan.context(),
        childSpan.getReferences().get(0).getSpanContext());
    assertEquals(parentSpan.context().getTraceId(), childSpan.context().getTraceId());
    assertEquals(parentSpan.context().getSpanId(), childSpan.context().getParentId());
  }

  @Test
  public void testIgnoreActiveSpan() {
    InMemoryReporter reporter = new InMemoryReporter();
    Tracer tracer =
        new Tracer.Builder("test", reporter, new ConstSampler(true)).build();
    try (ActiveSpan parent = tracer.buildSpan("parent").startActive()) {
      tracer.buildSpan("child").ignoreActiveSpan().startActive().deactivate();
    }
    assertEquals(2, reporter.getSpans().size());

    Span childSpan = reporter.getSpans().get(0);
    Span parentSpan = reporter.getSpans().get(1);

    assertTrue(reporter.getSpans().get(0).getReferences().isEmpty());
    assertTrue(reporter.getSpans().get(1).getReferences().isEmpty());
    assertNotEquals(parentSpan.context().getTraceId(), childSpan.context().getTraceId());
    assertEquals(0, childSpan.context().getParentId());
  }

  @Test
  public void testNoAutoRefWithExistingRefs() {
    InMemoryReporter reporter = new InMemoryReporter();
    Tracer tracer =
        new Tracer.Builder("test", reporter, new ConstSampler(true)).build();

    io.opentracing.Span initialSpan = tracer.buildSpan("initial").startManual();

    try (ActiveSpan parent = tracer.buildSpan("parent").startActive()) {
      tracer.buildSpan("child").asChildOf(initialSpan.context()).startActive().deactivate();
    }

    initialSpan.finish();

    assertEquals(3, reporter.getSpans().size());

    Span childSpan = reporter.getSpans().get(0);
    Span parentSpan = reporter.getSpans().get(1);
    Span initSpan = reporter.getSpans().get(2);

    assertTrue(initSpan.getReferences().isEmpty());
    assertTrue(parentSpan.getReferences().isEmpty());

    assertEquals(initSpan.context().getTraceId(), childSpan.context().getTraceId());
    assertEquals(initSpan.context().getSpanId(), childSpan.context().getParentId());

    assertEquals(0, initSpan.context().getParentId());
    assertEquals(0, parentSpan.context().getParentId());
  }

  @Test
  public void testCustomActiveSpanSource() {
    ActiveSpan activeSpan = mock(ActiveSpan.class);
    Tracer tracer =
        new Tracer.Builder("test", new InMemoryReporter(), new ConstSampler(true))
        .withActiveSpanSource(new ActiveSpanSource() {

          @Override
          public ActiveSpan activeSpan() {
            return activeSpan;
          }

          @Override
          public ActiveSpan makeActive(io.opentracing.Span span) {
            return activeSpan;
          }
        }).build();
    assertEquals(activeSpan, tracer.activeSpan());
  }
}
