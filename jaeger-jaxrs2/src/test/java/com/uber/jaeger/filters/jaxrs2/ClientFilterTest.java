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

import static com.uber.jaeger.filters.jaxrs2.Constants.CURRENT_SPAN_CONTEXT_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import com.uber.jaeger.Span;
import com.uber.jaeger.context.ActiveSpanSourceTraceContext;
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
import org.mockito.junit.MockitoJUnitRunner;

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
    traceContext = new ActiveSpanSourceTraceContext(tracer);
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
