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

package com.uber.jaeger.filters.jaxrs2;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uber.jaeger.Constants;
import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.context.ActiveSpanSourceTraceContext;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.propagation.FilterIntegrationTest;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.tag.Tags;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
    traceContext = new ActiveSpanSourceTraceContext(tracer);
    undertest = new ServerFilter(tracer, traceContext);
  }

  @Test
  public void filter() throws Exception {
    String method = "GET";
    URI uri = new URI("http://localhost/path");
    when(containerRequestContext.getMethod()).thenReturn(method);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getAbsolutePath()).thenReturn(uri);
    when(uriInfo.getBaseUri()).thenReturn(uri);
    when(containerRequestContext.getUriInfo()).thenReturn(uriInfo);
    MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
    headers.add(Constants.X_UBER_SOURCE, "source");
    when(containerRequestContext.getHeaders()).thenReturn(headers);

    when(containerResponseContext.getStatus()).thenReturn(200);

    undertest.filter(containerRequestContext);
    undertest.filter(containerRequestContext, containerResponseContext);

    List<Span> spans = reporter.getSpans();
    assertEquals(1, spans.size());
    Span span = spans.get(0);
    Map<String, Object> tags = span.getTags();

    assertEquals(method, span.getOperationName());
    assertEquals(Tags.SPAN_KIND_SERVER, tags.get(Tags.SPAN_KIND.getKey()));
    assertEquals(uri.toString(), tags.get(Tags.HTTP_URL.getKey()));
    assertEquals("localhost", tags.get(Tags.PEER_HOSTNAME.getKey()));


    // Exercise catch blocks on filter methods for code coverage
    undertest.filter(null);

    // For filter(requestContext, responseContext) we first need to make sure there's something in the traceContext
    undertest.filter(containerRequestContext);
    undertest.filter(containerRequestContext, null);
  }
}
