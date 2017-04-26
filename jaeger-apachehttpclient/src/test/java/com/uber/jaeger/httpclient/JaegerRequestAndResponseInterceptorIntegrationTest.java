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
package com.uber.jaeger.httpclient;

import static org.junit.Assert.assertEquals;

import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.context.TracingUtils;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.Sampler;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

@RunWith(MockitoJUnitRunner.class)
public class JaegerRequestAndResponseInterceptorIntegrationTest {

  @Rule public MockServerRule mockServerRule = new MockServerRule(this);
  private InMemoryReporter reporter;
  private Tracer tracer;
  private Span parentSpan;

  @SuppressWarnings("unused")
  private MockServerClient mockServerClient;

  private String BAGGAGE_KEY = "Bag_End";
  private String BAGGAGE_VALUE = "smial_at_the_end_of_Bagshot_Row";

  @Before
  public void setUp() {
    mockServerClient
        .when(HttpRequest.request().withMethod("GET").withPath("/testing"))
        .respond(HttpResponse.response().withStatusCode(200));
    reporter = new InMemoryReporter();
    Sampler sampler = new ConstSampler(true);
    tracer = new Tracer.Builder("test_service", reporter, sampler).build();

    //Set up a parent span context
    TraceContext parentTraceContext = TracingUtils.getTraceContext();
    parentSpan = (Span) tracer.buildSpan("parent_operation").start();
    parentSpan.setBaggageItem(BAGGAGE_KEY, BAGGAGE_VALUE);
    parentSpan.finish();
    parentTraceContext.push(parentSpan);
  }

  @Test
  public void testAsyncHttpClientTracing() throws Exception {
    HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom();
    CloseableHttpAsyncClient client = TracingInterceptors.addTo(clientBuilder, tracer).build();

    client.start();

    //Make a request to the async client and wait for response
    client
        .execute(
            new HttpHost("localhost", mockServerRule.getPort()),
            new BasicHttpRequest("GET", "/testing"),
            new FutureCallback<org.apache.http.HttpResponse>() {
              @Override
              public void completed(org.apache.http.HttpResponse result) {}

              @Override
              public void failed(Exception ex) {}

              @Override
              public void cancelled() {}
            })
        .get();

    verifyTracing(parentSpan);
  }

  @Test
  public void testHttpClientTracing() throws Exception {
    HttpClientBuilder clientBuilder = HttpClients.custom();
    CloseableHttpClient client = TracingInterceptors.addTo(clientBuilder, tracer).build();

    //Make a request to the async client and wait for response
    client.execute(
        new HttpHost("localhost", mockServerRule.getPort()),
        new BasicHttpRequest("GET", "/testing"));

    verifyTracing(parentSpan);
  }

  private void verifyTracing(Span parentSpan) {
    //Assert that traces are correctly emitted by the client
    List<Span> spans = reporter.getSpans();
    assertEquals(2, spans.size());
    Span span = spans.get(1);
    assertEquals("GET", span.getOperationName());
    assertEquals(parentSpan.context().getSpanId(), span.context().getParentId());

    //Assert traces and baggage are propagated correctly to server
    HttpRequest[] httpRequests = mockServerClient.retrieveRecordedRequests(null);
    assertEquals(1, httpRequests.length);
    String traceData = httpRequests[0].getFirstHeader("uber-trace-id");
    String[] split = traceData.split("%3A");
    assertEquals(Long.toHexString(span.context().getTraceId()), split[0]);
    assertEquals(Long.toHexString(span.context().getSpanId()), split[1]);
    String baggage = httpRequests[0].getFirstHeader("uberctx-" + BAGGAGE_KEY);
    assertEquals(BAGGAGE_VALUE, baggage);
  }
}
