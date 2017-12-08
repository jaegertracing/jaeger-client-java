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

package com.uber.jaeger.httpclient;

import static org.junit.Assert.assertEquals;

import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.context.TracingUtils;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.Sampler;
import java.lang.reflect.Field;
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
import org.junit.After;
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

  private static final String BAGGAGE_KEY = "Bag_End";
  private static final String BAGGAGE_VALUE = "smial_at_the_end_of_Bagshot_Row";

  @Before
  public void setUp() {
    mockServerClient
        .when(HttpRequest.request().withMethod("GET").withPath("/testing"))
        .respond(HttpResponse.response().withStatusCode(200));
    reporter = new InMemoryReporter();
    Sampler sampler = new ConstSampler(true);
    tracer = new Tracer.Builder("test_service", reporter, sampler).build();
    TracingUtils.setTracer(tracer);

    parentSpan = (Span) tracer.buildSpan("parent_operation").startManual();
    parentSpan.setBaggageItem(BAGGAGE_KEY, BAGGAGE_VALUE);
    parentSpan.finish();
    //Set up a parent span context
    TracingUtils.getTraceContext().push(parentSpan);
  }

  @After
  public void tearDown() throws Exception {
    Field field = TracingUtils.class.getDeclaredField("tracer");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Test
  public void testAsyncHttpClientTracing() throws Exception {
    HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom();
    CloseableHttpAsyncClient client = TracingInterceptors.addTo(clientBuilder, tracer).build();

    client.start();

    // Verify that parent span is on top of the stack _before_ request is made
    assertEquals(parentSpan, TracingUtils.getTraceContext().getCurrentSpan());

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

    // Verify that parent span is on top of the stack _after_ request is made
    assertEquals(parentSpan, TracingUtils.getTraceContext().getCurrentSpan());

    verifyTracing(parentSpan);
  }

  @Test
  public void testHttpClientTracing() throws Exception {
    HttpClientBuilder clientBuilder = HttpClients.custom();
    CloseableHttpClient client = TracingInterceptors.addTo(clientBuilder, tracer).build();

    //Make a request to the sync client and wait for response
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

  @Test
  public void testHttpClientTracingWithSplitSpanCreationInterceptors() throws Exception {
    HttpClientBuilder clientBuilder = HttpClients.custom();
    CloseableHttpClient client = CustomTracingInterceptor.addTo(clientBuilder, tracer).build();

    // Make a request to the sync client and wait for response
    client.execute(
        new HttpHost("localhost", mockServerRule.getPort()),
        new BasicHttpRequest("GET", "/testing"));

    verifyTracing(parentSpan);
  }

  static class CustomTracingInterceptor {
    static HttpClientBuilder addTo(HttpClientBuilder clientBuilder, Tracer tracer) {
      return clientBuilder
          .addInterceptorFirst(new SpanCreationRequestInterceptor(tracer))
          .addInterceptorLast(new SpanInjectionRequestInterceptor(tracer))
          .addInterceptorFirst(new TracingResponseInterceptor());
    }
  }
}
