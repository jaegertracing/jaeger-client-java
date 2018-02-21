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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.uber.jaeger.Configuration.ReporterConfiguration;
import com.uber.jaeger.Configuration.SenderConfiguration;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.propagation.Injector;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.reporters.RemoteReporter.Builder;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.RemoteControlledSampler;
import com.uber.jaeger.samplers.Sampler;
import com.uber.jaeger.senders.HttpSender;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import java.io.Closeable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TracerTest {

  Tracer tracer;
  InMemoryStatsReporter metricsReporter;

  @Before
  public void setUp() throws Exception {
    metricsReporter = new InMemoryStatsReporter();
    tracer =
        new Tracer.Builder("TracerTestService", new InMemoryReporter(), new ConstSampler(true))
            .withStatsReporter(metricsReporter)
            .build();
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
    assertEquals(
        1L, metricsReporter.counters.get("jaeger:started_spans.sampled=y").longValue());
    assertNull(metricsReporter.counters.get("jaeger:started_spans.sampled=n"));
    assertEquals(
        1L, metricsReporter.counters.get("jaeger:traces.sampled=y.state=started").longValue());
    assertNull(metricsReporter.counters.get("jaeger:traces.sampled=n.state=started"));
  }

  @Test
  public void testRegisterInjector() {
    @SuppressWarnings("unchecked")
    Injector<TextMap> injector = mock(Injector.class);

    Tracer tracer =
        new Tracer.Builder("TracerTestService", new InMemoryReporter(), new ConstSampler(true))
            .withStatsReporter(metricsReporter)
            .registerInjector(Format.Builtin.TEXT_MAP, injector)
            .build();
    Span span = (Span) tracer.buildSpan("leela").start();

    TextMap carrier = mock(TextMap.class);
    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, carrier);

    verify(injector).inject(any(SpanContext.class), any(TextMap.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testServiceNameNotNull() {
    new Tracer.Builder(null, new InMemoryReporter(), new ConstSampler(true));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testServiceNameNotEmptyNull() {
    new Tracer.Builder("  ", new InMemoryReporter(), new ConstSampler(true));
  }

  @Test
  public void testBuilderIsServerRpc() {
    Tracer.SpanBuilder spanBuilder = (Tracer.SpanBuilder) tracer.buildSpan("ndnd");
    spanBuilder.withTag(Tags.SPAN_KIND.getKey(), "server");

    assertTrue(spanBuilder.isRpcServer());
  }

  @Test
  public void testBuilderIsNotServerRpc() {
    Tracer.SpanBuilder spanBuilder = (Tracer.SpanBuilder) tracer.buildSpan("Lrrr");
    spanBuilder.withTag(Tags.SPAN_KIND.getKey(), "peachy");

    assertFalse(spanBuilder.isRpcServer());
  }

  @Test
  public void testWithBaggageRestrictionManager() {
    metricsReporter = new InMemoryStatsReporter();
    Metrics metrics = new Metrics(new StatsFactoryImpl(metricsReporter));
    tracer =
        new Tracer.Builder("TracerTestService", new InMemoryReporter(), new ConstSampler(true))
            .withMetrics(metrics)
            .build();
    Span span = (Span) tracer.buildSpan("some-operation").start();
    final String key = "key";
    tracer.setBaggage(span, key, "value");

    assertEquals(
        1L, metricsReporter.counters.get("jaeger:baggage_updates.result=ok").longValue());
  }

  @Test
  public void testTracerImplementsCloseable() {
    assertTrue(Closeable.class.isAssignableFrom(Tracer.class));
  }

  @Test
  public void testClose() {
    Reporter reporter = mock(Reporter.class);
    Sampler sampler = mock(Sampler.class);
    tracer = new Tracer.Builder("bonda", reporter, sampler).build();
    tracer.close();
    verify(reporter).close();
    verify(sampler).close();
  }

  @Test
  public void testAsChildOfAcceptNull() {
    tracer = new Tracer.Builder("foo", new InMemoryReporter(), new ConstSampler(true)).build();

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

    assertEquals(
        1L, metricsReporter.counters.get("jaeger:started_spans.sampled=y").longValue());
    assertEquals(
        1L, metricsReporter.counters.get("jaeger:started_spans.sampled=n").longValue());
    assertEquals(
        1L, metricsReporter.counters.get("jaeger:traces.sampled=y.state=started").longValue());
    assertNull(metricsReporter.counters.get("jaeger:traces.sampled=n.state=started"));
  }
}
