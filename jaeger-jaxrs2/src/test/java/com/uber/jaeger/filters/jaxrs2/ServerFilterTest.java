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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uber.jaeger.Constants;
import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.context.ThreadLocalTraceContext;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.propagation.FilterIntegrationTest;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.tag.Tags;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;

/**
 * Tests that {@link ServerFilter} produces a span and sets tags correctly See also:
 * {@link FilterIntegrationTest} for a complete Client/Server filter integration test
 */
@RunWith(MockitoJUnitRunner.class)
public class ServerFilterTest {
  private Tracer tracer;
  private InMemoryReporter reporter;
  private ServerFilter undertest;
  private TraceContext traceContext;

  @Mock private ContainerRequestContext containerRequestContext;
  @Mock private ContainerResponseContext containerResponseContext;

  @Before
  public void setUp() {
    reporter = new InMemoryReporter();
    tracer =
        new com.uber.jaeger.Tracer.Builder("Angry Machine", reporter, new ConstSampler(true))
            .build();
    traceContext = new ThreadLocalTraceContext();
    undertest = new ServerFilter(tracer, traceContext);
  }

  @Test
  public void filter() throws Exception {
    String method = "GET";
    String source = "source";
    URI uri = new URI("http://localhost/path");
    when(containerRequestContext.getMethod()).thenReturn(method);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getAbsolutePath()).thenReturn(uri);
    when(uriInfo.getBaseUri()).thenReturn(uri);
    when(containerRequestContext.getUriInfo()).thenReturn(uriInfo);
    MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
    headers.add(Constants.X_UBER_SOURCE, source);
    when(containerRequestContext.getHeaders()).thenReturn(headers);

    when(containerResponseContext.getStatus()).thenReturn(200);

    undertest.filter(containerRequestContext);
    undertest.filter(containerRequestContext, containerResponseContext);

    List<Span> spans = reporter.getSpans();
    assertEquals(1, spans.size());
    Map<String, Object> tags = spans.get(0).getTags();

    assertEquals("server", tags.get(Tags.SPAN_KIND.getKey()));
    assertEquals(source, tags.get(Tags.PEER_SERVICE.getKey()));
    assertEquals(uri.toString(), tags.get(Tags.HTTP_URL.getKey()));
    assertEquals("localhost", tags.get(Tags.PEER_HOSTNAME.getKey()));
  }
}
