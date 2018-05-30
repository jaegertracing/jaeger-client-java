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

package io.jaegertracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import io.jaegertracing.reporters.InMemoryReporter;
import io.jaegertracing.samplers.ConstSampler;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PropagationTest {
  @Test
  public void testDebugCorrelationId() {
    JaegerTracer tracer = new JaegerTracer.Builder("test")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .build();
    Map<String, String> headers = new HashMap<>();
    headers.put(Constants.DEBUG_ID_HEADER_KEY, "Coraline");
    TextMap carrier = new TextMapExtractAdapter(headers);
    JaegerSpanContext jaegerSpanContext = (JaegerSpanContext) tracer.extract(Format.Builtin.TEXT_MAP, carrier);
    assertTrue(jaegerSpanContext.isDebugIdContainerOnly());
    assertEquals("Coraline", jaegerSpanContext.getDebugId());
    Span span = tracer.buildSpan("span").asChildOf(jaegerSpanContext).start();
    jaegerSpanContext = (JaegerSpanContext) span.context();
    assertTrue(jaegerSpanContext.isSampled());
    assertTrue(jaegerSpanContext.isDebug());
    assertEquals("Coraline", ((JaegerSpan) span).getTags().get(Constants.DEBUG_ID_HEADER_KEY));
  }

  @Test
  public void testActiveSpanPropagation() {
    JaegerTracer tracer = new JaegerTracer.Builder("test")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .build();
    try (Scope parent = tracer.buildSpan("parent").startActive(true)) {
      assertEquals(parent, tracer.scopeManager().active());
    }
  }

  @Test
  public void testActiveSpanAutoReference() {
    InMemoryReporter reporter = new InMemoryReporter();
    JaegerTracer tracer = new JaegerTracer.Builder("test")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .build();
    try (Scope parent = tracer.buildSpan("parent").startActive(true)) {
      tracer.buildSpan("child").startActive(true).close();
    }
    assertEquals(2, reporter.getSpans().size());

    JaegerSpan childSpan = (JaegerSpan) reporter.getSpans().get(0);
    assertEquals("child", childSpan.getOperationName());
    assertEquals(1, childSpan.getReferences().size());
    assertEquals(References.CHILD_OF, childSpan.getReferences().get(0).getType());

    JaegerSpan parentSpan = (JaegerSpan) reporter.getSpans().get(1);
    assertEquals("parent", parentSpan.getOperationName());
    assertTrue(parentSpan.getReferences().isEmpty());

    JaegerSpanContext childSpanContext = (JaegerSpanContext) childSpan.context();
    JaegerSpanContext parentSpanContext = (JaegerSpanContext) parentSpan.context();
    assertEquals(parentSpan.context(), childSpan.getReferences().get(0).getSpanContext());
    assertEquals(parentSpanContext.getTraceId(), childSpanContext.getTraceId());
    assertEquals(parentSpanContext.getSpanId(), childSpanContext.getParentId());
  }

  @Test
  public void testActiveSpanAutoFinishOnClose() {
    InMemoryReporter reporter = new InMemoryReporter();
    JaegerTracer tracer = new JaegerTracer.Builder("test")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .build();
    tracer.buildSpan("parent").startActive(true).close();
    assertEquals(1, reporter.getSpans().size());
  }

  @Test
  public void testActiveSpanNotAutoFinishOnClose() {
    InMemoryReporter reporter = new InMemoryReporter();
    JaegerTracer tracer = new JaegerTracer.Builder("test")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .build();
    Scope scope = tracer.buildSpan("parent").startActive(false);
    Span span = scope.span();
    scope.close();
    assertTrue(reporter.getSpans().isEmpty());
    span.finish();
    assertEquals(1, reporter.getSpans().size());
  }

  @Test
  public void testIgnoreActiveSpan() {
    InMemoryReporter reporter = new InMemoryReporter();
    JaegerTracer tracer = new JaegerTracer.Builder("test")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .build();
    try (Scope parent = tracer.buildSpan("parent").startActive(true)) {
      tracer.buildSpan("child").ignoreActiveSpan().startActive(true).close();
    }
    assertEquals(2, reporter.getSpans().size());

    JaegerSpan childSpan = (JaegerSpan) reporter.getSpans().get(0);
    JaegerSpan parentSpan = (JaegerSpan) reporter.getSpans().get(1);
    JaegerSpanContext parentSpanContext = (JaegerSpanContext) parentSpan.context();
    JaegerSpanContext childSpanContext = (JaegerSpanContext) childSpan.context();

    assertTrue(((JaegerSpan) reporter.getSpans().get(0)).getReferences().isEmpty());
    assertTrue(((JaegerSpan) reporter.getSpans().get(1)).getReferences().isEmpty());
    assertNotEquals(parentSpanContext.getTraceId(), childSpanContext.getTraceId());
    assertEquals(0, childSpanContext.getParentId());
  }

  @Test
  public void testNoAutoRefWithExistingRefs() {
    InMemoryReporter reporter = new InMemoryReporter();
    JaegerTracer tracer = new JaegerTracer.Builder("test")
        .withReporter(reporter)
        .withSampler(new ConstSampler(true))
        .build();

    Span initialSpan = tracer.buildSpan("initial").start();

    try (Scope parent = tracer.buildSpan("parent").startActive(true)) {
      tracer.buildSpan("child").asChildOf(initialSpan.context()).startActive(true).close();
    }

    initialSpan.finish();

    assertEquals(3, reporter.getSpans().size());

    JaegerSpan parentSpan = (JaegerSpan) reporter.getSpans().get(1);
    assertTrue(parentSpan.getReferences().isEmpty());

    JaegerSpan initSpan = (JaegerSpan) reporter.getSpans().get(2);
    assertTrue(initSpan.getReferences().isEmpty());

    JaegerSpanContext initialSpanContext = (JaegerSpanContext) initSpan.context();
    assertEquals(0, initialSpanContext.getParentId());

    JaegerSpan childSpan = (JaegerSpan) reporter.getSpans().get(0);
    JaegerSpanContext childSpanContext = (JaegerSpanContext) childSpan.context();
    assertEquals(initialSpanContext.getTraceId(), childSpanContext.getTraceId());
    assertEquals(initialSpanContext.getSpanId(), childSpanContext.getParentId());

    JaegerSpanContext parentSpanContext = (JaegerSpanContext) parentSpan.context();
    assertEquals(0, parentSpanContext.getParentId());
  }

  @Test
  public void testCustomScopeManager() {
    Scope scope = mock(Scope.class);
    JaegerTracer tracer = new JaegerTracer.Builder("test")
        .withReporter(new InMemoryReporter())
        .withSampler(new ConstSampler(true))
        .withScopeManager(new ScopeManager() {

          @Override
          public Scope activate(Span span, boolean finishSpanOnClose) {
            return scope;
          }

          @Override
          public Scope active() {
            return scope;
          }
        }).build();
    assertEquals(scope, tracer.scopeManager().active());
  }
}
