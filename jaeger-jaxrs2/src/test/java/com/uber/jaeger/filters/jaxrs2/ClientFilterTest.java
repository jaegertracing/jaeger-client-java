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

package com.uber.jaeger.filters.jaxrs2;

import static com.uber.jaeger.filters.jaxrs2.Constants.CURRENT_SPAN_CONTEXT_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import com.uber.jaeger.Span;
import com.uber.jaeger.context.ThreadLocalTraceContext;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.propagation.FilterIntegrationTest;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Tests that {@link ClientFilter} produces a span and sets tags correctly See also:
 * {@link FilterIntegrationTest} for a complete Client/Server filter integration test
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientFilterTest {

  private Tracer tracer;
  private TraceContext traceContext;
  private InMemoryReporter reporter;
  private ClientFilter undertest;

  @Mock private ClientRequestContext clientRequestContext;

  @Mock private ClientResponseContext clientResponseContext;

  @Before
  public void setUp() throws Exception {
    reporter = new InMemoryReporter();
    tracer =
        new com.uber.jaeger.Tracer.Builder("Angry Machine", reporter, new ConstSampler(true))
            .build();
    traceContext = new ThreadLocalTraceContext();
    undertest = new ClientFilter(tracer, traceContext);
  }

  @Test
  public void testFilter() throws Exception {
    String method = "GET";
    when(clientRequestContext.getMethod()).thenReturn(method);
    when(clientRequestContext.getUri()).thenReturn(new URI("http://localhost/path"));
    when(clientRequestContext.getHeaders()).thenReturn(new MultivaluedHashMap<String, Object>());

    when(clientResponseContext.getStatus()).thenReturn(200);

    undertest.filter(clientRequestContext);

    assertNull(clientRequestContext.getProperty(CURRENT_SPAN_CONTEXT_KEY));

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);

    Mockito.verify(clientRequestContext)
        .setProperty(stringCaptor.capture(), objectCaptor.capture());
    when(clientRequestContext.getProperty(stringCaptor.getValue()))
        .thenReturn(objectCaptor.getValue());

    undertest.filter(clientRequestContext, clientResponseContext);

    assertNotNull(clientRequestContext.getProperty(CURRENT_SPAN_CONTEXT_KEY));

    List<Span> spans = reporter.getSpans();
    Map<String, Object> tags = spans.get(0).getTags();
    assertEquals(200, tags.get(Tags.HTTP_STATUS.getKey()));
    assertEquals("localhost", tags.get(Tags.PEER_HOSTNAME.getKey()));
    assertEquals("http://localhost/path", tags.get(Tags.HTTP_URL.getKey()));
  }
}
