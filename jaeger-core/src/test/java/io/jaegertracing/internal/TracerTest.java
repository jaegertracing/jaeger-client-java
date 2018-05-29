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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.jaegertracing.JaegerTracer;
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.reporter.InMemoryReporter;
import io.jaegertracing.reporter.RemoteReporter;
import io.jaegertracing.sampler.ConstSampler;
import io.jaegertracing.spi.Injector;
import io.jaegertracing.spi.Reporter;
import io.jaegertracing.spi.Sampler;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import java.io.Closeable;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TracerTest {

  JaegerTracer tracer;
  InMemoryMetricsFactory metricsFactory;

  @Before
  public void setUp() throws Exception {
    metricsFactory = new InMemoryMetricsFactory();
    tracer = new JaegerTracer.Builder("TracerTestService")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .withMetrics(new Metrics(metricsFactory))
            .build();
  }

  @Test
  public void testDefaultConstructor() {
    JaegerTracer tracer = new JaegerTracer.Builder("name").build();
    assertTrue(tracer.getReporter() instanceof RemoteReporter);
    // no exception
    tracer.buildSpan("foo").start().finish();
  }

  @Test
  public void testBuildSpan() {
    String expectedOperation = "fry";
    Span span = (Span) tracer.buildSpan(expectedOperation).start();

    assertEquals(expectedOperation, span.getOperationName());
  }

  @Test
  public void testTracerMetrics() {
    String expectedOperation = "fry";
    tracer.buildSpan(expectedOperation).start();
    assertEquals(1, metricsFactory.getCounter("jaeger:started_spans", "sampled=y"));
    assertEquals(0, metricsFactory.getCounter("jaeger:started_spans", "sampled=n"));
    assertEquals(1, metricsFactory.getCounter("jaeger:traces", "sampled=y,state=started"));
    assertEquals(0, metricsFactory.getCounter("jaeger:traces", "sampled=n,state=started"));
  }

  @Test
  public void testRegisterInjector() {
    @SuppressWarnings("unchecked")
    Injector<TextMap> injector = mock(Injector.class);

    Tracer tracer = new JaegerTracer.Builder("TracerTestService")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .withMetrics(new Metrics(new InMemoryMetricsFactory()))
            .registerInjector(Format.Builtin.TEXT_MAP, injector)
            .build();
    Span span = (Span) tracer.buildSpan("leela").start();

    TextMap carrier = mock(TextMap.class);
    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, carrier);

    verify(injector).inject(any(SpanContext.class), any(TextMap.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testServiceNameNotNull() {
    new JaegerTracer.Builder(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testServiceNameNotEmptyNull() {
    new JaegerTracer.Builder("  ");
  }

  @Test
  public void testBuilderIsServerRpc() {
    JaegerBaseTracer.SpanBuilder spanBuilder = (JaegerBaseTracer.SpanBuilder) tracer.buildSpan("ndnd");
    spanBuilder.withTag(Tags.SPAN_KIND.getKey(), "server");

    assertTrue(spanBuilder.isRpcServer());
  }

  @Test
  public void testBuilderIsNotServerRpc() {
    JaegerBaseTracer.SpanBuilder spanBuilder = (JaegerBaseTracer.SpanBuilder) tracer.buildSpan("Lrrr");
    spanBuilder.withTag(Tags.SPAN_KIND.getKey(), "peachy");

    assertFalse(spanBuilder.isRpcServer());
  }

  @Test
  public void testWithBaggageRestrictionManager() {
    tracer = new JaegerTracer.Builder("TracerTestService")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .withMetrics(new Metrics(metricsFactory))
            .build();
    Span span = (Span) tracer.buildSpan("some-operation").start();
    final String key = "key";
    tracer.setBaggage(span, key, "value");

    assertEquals(1, metricsFactory.getCounter("jaeger:baggage_updates", "result=ok"));
  }

  @Test
  public void testTracerImplementsCloseable() {
    assertTrue(Closeable.class.isAssignableFrom(JaegerBaseTracer.class));
  }

  @Test
  public void testClose() throws IOException {
    Reporter reporter = mock(Reporter.class);
    Sampler sampler = mock(Sampler.class);
    tracer = new JaegerTracer.Builder("bonda")
        .withReporter(reporter)
        .withSampler(sampler)
        .build();
    tracer.close();
    verify(reporter).close();
    verify(sampler).close();
  }

  @Test
  public void testAsChildOfAcceptNull() {
    tracer = new JaegerTracer.Builder("foo")
        .withReporter(new InMemoryReporter())
        .withSampler(new ConstSampler(true))
        .build();

    Span span = (Span)tracer.buildSpan("foo").asChildOf((Span) null).start();
    span.finish();
    assertTrue(span.getReferences().isEmpty());

    span = (Span)tracer.buildSpan("foo").asChildOf((io.opentracing.SpanContext) null).start();
    span.finish();
    assertTrue(span.getReferences().isEmpty());
  }

  @Test
  public void testActiveSpan() {
    io.opentracing.Span mockSpan = Mockito.mock(io.opentracing.Span.class);
    tracer.scopeManager().activate(mockSpan, true);
    assertEquals(mockSpan, tracer.activeSpan());
  }

  @Test
  public void testSpanContextNotSampled() {
    String expectedOperation = "fry";
    Span first = (Span) tracer.buildSpan(expectedOperation).start();
    tracer.buildSpan(expectedOperation).asChildOf(first.context().withFlags((byte) 0)).start();

    assertEquals(1, metricsFactory.getCounter("jaeger:started_spans", "sampled=y"));
    assertEquals(1, metricsFactory.getCounter("jaeger:started_spans", "sampled=n"));
    assertEquals(1, metricsFactory.getCounter("jaeger:traces", "sampled=y,state=started"));
    assertEquals(0, metricsFactory.getCounter("jaeger:traces", "sampled=n,state=started"));
  }
}
