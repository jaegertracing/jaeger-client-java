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
package com.uber.jaeger.propagation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.Span;
import com.uber.jaeger.context.ThreadLocalTraceContext;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.filters.jaxrs2.ClientFilter;
import com.uber.jaeger.filters.jaxrs2.ServerRequestCarrier;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class FilterIntegrationTest {
  private JerseyServer server;
  private Client client;
  private Tracer tracer;
  private InMemoryReporter reporter;
  private InMemoryStatsReporter metricsReporter;
  private TraceContext traceContext;
  public static final String BAGGAGE_KEY = "a-big-metal-door";
  private ObjectMapper mapper = new ObjectMapper();

  // http://www.syfy.com/darkmatter/videos/a-big-metal-door
  public static final String BAGGAGE_VALUE = "I-Keep-It-Locked-Always";

  @Before
  public void setUp() throws Exception {
    metricsReporter = new InMemoryStatsReporter();
    reporter = new InMemoryReporter();
    tracer =
        new com.uber.jaeger.Tracer.Builder("some-op-name", reporter, new ConstSampler(true))
            .withStatsReporter(metricsReporter)
            .build();

    traceContext = new ThreadLocalTraceContext();

    // start the server
    server = new JerseyServer(tracer, traceContext);
    server.start();
    // create the client
    client =
        ClientBuilder.newClient()
            .register(new ClientFilter(tracer, traceContext))
            .register(JacksonFeature.class);
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }

  @Test
  public void testJerseyClientReceivesSpan() throws Exception {
    WebTarget target = client.target(server.BASE_URI).path("jersey").path("hop1");

    Span span = (Span) tracer.buildSpan("root-span").start();
    span.setBaggageItem(BAGGAGE_KEY, BAGGAGE_VALUE);
    traceContext.push(span);

    Response resp = target.request(MediaType.APPLICATION_JSON_TYPE).get();

    String responseStr = resp.readEntity(String.class);
    CallTreeNode callTree = mapper.readValue(responseStr, CallTreeNode.class);

    String strContext = span.getContext().contextAsString();
    String traceID = strContext.substring(0, strContext.indexOf(':'));
    boolean isSampled = true;

    assertEquals(6, reporter.getSpans().size());
    assertTrue(callTree.validateTraceIds(traceID, isSampled));

    assertEquals(
        3L, metricsReporter.counters.get("jaeger.traces.sampled=y.state=joined").longValue());
    assertEquals(
        6L,
        metricsReporter.counters.get("jaeger.spans.group=lifecycle.state=finished").longValue());
    assertEquals(
        1L, metricsReporter.counters.get("jaeger.traces.sampled=y.state=started").longValue());
    assertEquals(
        7L, metricsReporter.counters.get("jaeger.spans.group=lifecycle.state=started").longValue());
  }

  /*
   * This test exists because opentracing's convention around missing tracer
   * state headers may change to stop supporting the automatic creation of
   * building a span.
   */
  @Test
  public void testExtractorReturnsNullWhenTracerStateHeaderIsMissing() {
    ContainerRequestContext reqContext = mock(ContainerRequestContext.class);
    given(reqContext.getHeaders()).willReturn(new MultivaluedHashMap<String, String>());
    ServerRequestCarrier carrier = new ServerRequestCarrier(reqContext);
    SpanContext spanCtx = tracer.extract(Format.Builtin.HTTP_HEADERS, carrier);
    assertNull(spanCtx);
  }
}
