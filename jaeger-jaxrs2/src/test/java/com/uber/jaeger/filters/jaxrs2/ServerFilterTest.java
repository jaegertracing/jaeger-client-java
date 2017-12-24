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

import com.uber.jaeger.Constants;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.propagation.FilterIntegrationTest;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.utils.TestUtils;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that {@link ServerFilter} produces a span and sets tags correctly See also:
 * {@link FilterIntegrationTest} for a complete Client/Server filter integration test.
 */
public class ServerFilterTest extends JerseyTest {
  private InMemoryReporter reporter;
  private static Tracer tracer;

  @Override
  protected Application configure() {
    reporter = new InMemoryReporter();
    tracer = new Tracer.Builder("Angry Machine", reporter, new ConstSampler(true))
            .build();
    GlobalTracer.register(tracer);

    ResourceConfig resourceConfig = new ResourceConfig(HelloResource.class, EagleResource.class);
    ServerFilter filter = new ServerFilter(tracer, TracingUtils.getTraceContext());
    resourceConfig.register(filter);
    return resourceConfig;
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    TestUtils.resetGlobalTracer();
  }

  @Path("heliosphan")
  public static class HelloResource {
    @GET
    public String getHello() {
      tracer.buildSpan("nested-span").startActive().close();
      return "Twinning";
    }
  }

  @Path("monsoon")
  public static class EagleResource {
    @GET
    public String getEagle() {
      return "Thorondor";
    }
  }

  @Test
  public void testOperationName() throws Exception {
    Response response = target("monsoon").request().get();
    Assert.assertEquals(200,response.getStatus());

    List<Span> spans = reporter.getSpans();
    Assert.assertFalse(spans.isEmpty());

    Assert.assertEquals("GET", spans.get(0).getOperationName());
  }

  @Test
  public void testPropagationThroughNestedSpan() throws Exception {
    Response response = target("heliosphan").request().get();
    Assert.assertEquals(200,response.getStatus());

    List<Span> spans = reporter.getSpans();

    Assert.assertEquals(2, spans.size());

    Assert.assertEquals(spans.get(0).context().getTraceId(),
                 spans.get(1).context().getTraceId());

    Assert.assertEquals(spans.get(0).context().getParentId(),
                 spans.get(1).context().getSpanId());
  }

  @Test
  public void testInject() throws Exception {
    HeaderTextMap headers = new HeaderTextMap();
    tracer.inject(SpanContext.contextFromString("4:3:2:1"), Format.Builtin.HTTP_HEADERS, headers);
    Response response = target("monsoon").request().headers(headers.getMap()).get();
    Assert.assertEquals(200,response.getStatus());

    List<Span> spans = reporter.getSpans();
    Assert.assertFalse(spans.isEmpty());

    Assert.assertEquals(4, spans.get(0).context().getTraceId());
  }

  @Test
  public void testUberHeader() throws Exception {
    Response response = target("monsoon").request().header(Constants.X_UBER_SOURCE, "origin").get();
    Assert.assertEquals(200,response.getStatus());

    List<Span> spans = reporter.getSpans();
    Assert.assertFalse(spans.isEmpty());

    Assert.assertEquals("origin", spans.get(0).getTags().get(Tags.PEER_SERVICE.getKey()));
  }

  private static class HeaderTextMap implements TextMap {

    private MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      return null;
    }

    @Override
    public void put(String k, String v) {
      List<Object> valueList = new ArrayList<>();
      valueList.add(v);
      headers.put(k, valueList);
    }

    MultivaluedHashMap<String, Object> getMap() {
      return headers;
    }
  }

}
