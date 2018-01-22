/*
 * Copyright (c) 2018, Uber Technologies, Inc
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
import static org.mockito.Mockito.when;

import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.util.Map;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClientSpanInjectionFilterTest {

  private final Tracer tracer = new com.uber.jaeger.Tracer
      .Builder("Angry Machine", new InMemoryReporter(), new ConstSampler(true))
      .build();

  @Mock
  private ClientRequestContext clientRequestContext;

  @Mock
  private ClientResponseContext clientResponseContext;

  @Test
  public void testFilter() throws Exception {
    when(clientResponseContext.getStatus()).thenReturn(200);

    io.opentracing.Span currentSpan = tracer.buildSpan("test").start();
    tracer.scopeManager().activate(currentSpan, true);
    when(clientRequestContext.getProperty(CURRENT_SPAN_CONTEXT_KEY)).thenReturn(currentSpan);

    // FIXME (debo): remove to use {@link ClientSpanInjectionFilter(Tracer)}
    ClientSpanInjectionFilter filter = new ClientSpanInjectionFilter(tracer, null);
    filter.filter(clientRequestContext, clientResponseContext);

    Map<String, Object> tags = ((com.uber.jaeger.Span)currentSpan).getTags();
    assertEquals(200, tags.get(Tags.HTTP_STATUS.getKey()));
  }
}