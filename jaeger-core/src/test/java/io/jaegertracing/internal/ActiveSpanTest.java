/*
 * Copyright (c) 2018, The Jaeger Authors.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ActiveSpanTest {
  InMemoryReporter reporter;
  JaegerTracer tracer;

  @Before
  public void setUp() {
    reporter = new InMemoryReporter();
    tracer =
        new JaegerTracer.Builder("TracerTestService")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .build();
  }

  @Test
  public void testActiveSpan() {
    JaegerSpan mockSpan = Mockito.mock(JaegerSpan.class);
    try (Scope scope = tracer.scopeManager().activate(mockSpan)) {
      assertEquals(mockSpan, tracer.activeSpan());
    }
  }

  @Test
  public void testActivateSpan() {
    JaegerSpan mockSpan = Mockito.mock(JaegerSpan.class);
    tracer.activateSpan(mockSpan);
    assertEquals(mockSpan, tracer.activeSpan());
  }

  @Test
  public void testActiveSpanPropagation() {
    Span span = tracer.buildSpan("parent").start();
    try (Scope parent = tracer.activateSpan(span)) {
      assertEquals(span, tracer.scopeManager().activeSpan());
    }
  }

  @Test
  public void testActiveSpanAutoReference() {
    Span span = tracer.buildSpan("parent").start();
    try (Scope ignored = tracer.activateSpan(span)) {
      tracer.buildSpan("child").start().finish();
    } finally {
      span.finish();
    }
    assertEquals(2, reporter.getSpans().size());

    JaegerSpan childSpan = reporter.getSpans().get(0);
    assertEquals("child", childSpan.getOperationName());
    assertEquals(1, childSpan.getReferences().size());
    assertEquals(References.CHILD_OF, childSpan.getReferences().get(0).getType());

    JaegerSpan parentSpan = reporter.getSpans().get(1);
    assertEquals("parent", parentSpan.getOperationName());
    assertTrue(parentSpan.getReferences().isEmpty());

    JaegerSpanContext childSpanContext = childSpan.context();
    JaegerSpanContext parentSpanContext = parentSpan.context();
    assertEquals(parentSpan.context(), childSpan.getReferences().get(0).getSpanContext());
    assertEquals(parentSpanContext.getTraceId(), childSpanContext.getTraceId());
    assertEquals(parentSpanContext.getSpanId(), childSpanContext.getParentId());
  }

  @Test
  public void testActiveSpanAutoFinishOnClose() {
    tracer.buildSpan("parent").startActive(true).close();
    assertEquals(1, reporter.getSpans().size());
  }

  @Test
  public void testActiveSpanNotAutoFinishOnClose() {
    Span span = tracer.buildSpan("parent").start();
    try (Scope ignored = tracer.activateSpan(span)) {
      // noop
    }
    assertTrue(reporter.getSpans().isEmpty());
    span.finish();
    assertEquals(1, reporter.getSpans().size());
  }

  @Test
  public void testIgnoreActiveSpan() {
    Span span = tracer.buildSpan("parent").start();
    try (Scope ignored = tracer.activateSpan(span)) {
      tracer.buildSpan("child").ignoreActiveSpan().start().finish();
    } finally {
      span.finish();
    }
    assertEquals(2, reporter.getSpans().size());

    JaegerSpan childSpan = reporter.getSpans().get(0);
    JaegerSpan parentSpan = reporter.getSpans().get(1);
    JaegerSpanContext parentSpanContext = parentSpan.context();
    JaegerSpanContext childSpanContext = childSpan.context();

    assertTrue((reporter.getSpans().get(0)).getReferences().isEmpty());
    assertTrue((reporter.getSpans().get(1)).getReferences().isEmpty());
    assertNotEquals(parentSpanContext.getTraceId(), childSpanContext.getTraceId());
    assertEquals(0, childSpanContext.getParentId());
  }

  @Test
  public void testNoAutoRefWithExistingRefs() {
    JaegerSpan initialSpan = tracer.buildSpan("initial").start();
    JaegerSpan parent = tracer.buildSpan("parent").start();
    try (Scope ignored = tracer.activateSpan(parent)) {
      tracer.buildSpan("child").asChildOf(initialSpan.context()).start().finish();
    } finally {
      parent.finish();
      initialSpan.finish();
    }

    initialSpan.finish();

    assertEquals(3, reporter.getSpans().size());

    JaegerSpan parentSpan = reporter.getSpans().get(1);
    assertTrue(parentSpan.getReferences().isEmpty());

    JaegerSpan initSpan = reporter.getSpans().get(2);
    assertTrue(initSpan.getReferences().isEmpty());

    JaegerSpanContext initialSpanContext = initSpan.context();
    assertEquals(0, initialSpanContext.getParentId());

    JaegerSpan childSpan = reporter.getSpans().get(0);
    JaegerSpanContext childSpanContext = childSpan.context();
    assertEquals(initialSpanContext.getTraceId(), childSpanContext.getTraceId());
    assertEquals(initialSpanContext.getSpanId(), childSpanContext.getParentId());

    JaegerSpanContext parentSpanContext = parentSpan.context();
    assertEquals(0, parentSpanContext.getParentId());
  }

  @Test
  public void testCustomScopeManager() {
    Span span = mock(Span.class);
    Scope scope = mock(Scope.class);
    JaegerTracer tracer = new JaegerTracer.Builder("test")
        .withReporter(new InMemoryReporter())
        .withSampler(new ConstSampler(true))
        .withScopeManager(new ScopeManager() {

          @Override
          public Scope activate(Span span) {
            return scope;
          }

          @Override
          public Span activeSpan() {
            return span;
          }
        }).build();
    assertEquals(span, tracer.scopeManager().activeSpan());
    assertEquals(scope, tracer.scopeManager().activate(span));
  }


  @Test
  public void testCustomSpanOnSpanManager() {
    // prepare
    Span activeSpan = mock(Span.class);
    ScopeManager scopeManager = tracer.scopeManager();

    // test
    scopeManager.activate(activeSpan);

    // check
    assertEquals(activeSpan, tracer.activeSpan());
  }
}
