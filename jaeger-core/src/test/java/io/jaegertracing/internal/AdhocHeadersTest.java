/*
 * Copyright (c) 2018, The Jaeger Authors.
 * Copyright (c) 2016-2017, Uber Technologies, Inc
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.internal.metrics.InMemoryMetricsFactory;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class AdhocHeadersTest {
  JaegerTracer tracer;
  InMemoryMetricsFactory metricsFactory;

  @Before
  public void setUp() {
    metricsFactory = new InMemoryMetricsFactory();
    tracer =
        new JaegerTracer.Builder("TracerTestService")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .withMetrics(new Metrics(metricsFactory))
            .build();
  }

  @Test
  public void testDebugCorrelationId() {
    Map<String, String> headers = Collections.singletonMap(Constants.DEBUG_ID_HEADER_KEY, "Coraline");
    TextMap carrier = new TextMapExtractAdapter(headers);

    JaegerSpanContext inboundSpanContext = tracer.extract(Format.Builtin.TEXT_MAP, carrier);
    assertNotNull(inboundSpanContext);
    assertFalse(inboundSpanContext.hasTrace());
    assertEquals("Coraline", inboundSpanContext.getDebugId());

    JaegerSpan span = tracer.buildSpan("span").asChildOf(inboundSpanContext).start();
    JaegerSpanContext serverSpanContext = span.context();
    assertTrue(serverSpanContext.isSampled());
    assertTrue(serverSpanContext.isDebug());
    assertEquals("Coraline", span.getTags().get(Constants.DEBUG_ID_HEADER_KEY));
  }


  @Test
  public void testStartTraceWithAdhocBaggage() {
    traceWithAdhocBaggage(new HashMap<String, String>());
  }

  @Test
  public void testJoinTraceWithAdhocBaggage() {
    Span span = tracer.buildSpan("test").start();
    Map<String, String> headers = new HashMap<String, String>();
    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMapInjectAdapter(headers));
    assertEquals(1, headers.size());

    traceWithAdhocBaggage(headers);
  }

  private void traceWithAdhocBaggage(Map<String, String> headers) {
    headers.put("jaeger-baggage", "k1=v1, k2 = v2");

    JaegerSpanContext parent = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(headers));
    JaegerSpan span = tracer.buildSpan("test").asChildOf(parent).start();

    assertTrue(span.context().isSampled());
    assertEquals("must have baggage", "v1", span.getBaggageItem("k1"));
    assertEquals("must have baggage", "v2", span.getBaggageItem("k2"));
  }
}
