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

package io.jaegertracing.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.AutoFinishScopeManager;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for removed deprecated APIs from 0.32.0 to 0.33.0.
 */
@SuppressWarnings("deprecation")
public class RemovedFrom032Test {
  private static final String SPAN_NAME = "foo";

  private InMemoryReporter reporter;
  private Tracer tracer;

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
  public void testScopeManager_active() {
    Span span = tracer.buildSpan(SPAN_NAME).start();
    Scope scope = tracer.activateSpan(span);
    try {
      assertEquals(scope, tracer.scopeManager().active());
    } finally {
      scope.close();
    }
  }

  @Test
  public void testScopeManager_activate_and_finish() {
    Span span = tracer.buildSpan(SPAN_NAME).start();
    Scope scope = tracer.scopeManager().activate(span, true);
    try {
      assertEquals(scope, tracer.scopeManager().active());
    } finally {
      scope.close();
    }
    assertEquals(Collections.singletonList(span), reporter.getSpans());
  }

  @Test
  public void testScope_span() {
    Span span = tracer.buildSpan(SPAN_NAME).start();
    Scope scope = tracer.scopeManager().activate(span);
    try {
      assertEquals(span, scope.span());
    } finally {
      scope.close();
    }
  }

  @Test
  public void testSpanBuilder_startActive() {
    Scope scope = tracer.buildSpan(SPAN_NAME).startActive(true);
    try {
      assertEquals(scope.span(), tracer.activeSpan());
    } finally {
      scope.close();
    }
    assertEquals(Collections.singletonList(scope.span()), reporter.getSpans());
  }

  @Test
  public void testTracer_startManual() {
    Span span = tracer.buildSpan(SPAN_NAME).startManual();
    assertEquals(null, tracer.activeSpan());
    span.finish();
    assertEquals(Collections.singletonList(span), reporter.getSpans());
  }

  @Test
  public void testAutoFinishScopeManager() {
    assertNotNull(new AutoFinishScopeManager());
  }
}
