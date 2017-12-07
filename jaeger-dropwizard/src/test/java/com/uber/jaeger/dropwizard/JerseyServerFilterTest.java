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

package com.uber.jaeger.dropwizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.context.TracingUtils;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.NoopTracerFactory;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Test;

public class JerseyServerFilterTest extends JerseyTest {

  private InMemoryReporter reporter;
  private JerseyServerFilter undertest;

  @Override
  protected Application configure() {
    reporter = new InMemoryReporter();
    Tracer tracer = new Tracer.Builder("world service", reporter, new ConstSampler(true)).build();

    ResourceConfig resourceConfig = new ResourceConfig(HelloResource.class,
                                                       StormlordResource.class);
    TracingUtils.setTracer(tracer);
    undertest = new JerseyServerFilter(tracer, TracingUtils.getTraceContext());
    resourceConfig.register(undertest);
    return resourceConfig;
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();

    // clean up tracing utils tracer instance
    Field field = TracingUtils.class.getDeclaredField("tracer");
    field.setAccessible(true);
    field.set(null, NoopTracerFactory.create());
  }

  @Path("hello")
  public static class HelloResource {
    @GET
    @Path("world/{worldId}")
    public String getHello(@QueryParam("worldId") String val) {
      return "Hello " + val;
    }
  }

  @Path("stormlord")
  public static class StormlordResource {
    @GET
    public String nakedGet() {
      return "Gwaihir";
    }
  }

  @Test
  public void testOperationNameSuccess() throws Exception {
    Response response = target("hello/world/middle-earth").request().get();
    assertEquals(200, response.getStatus());
    Span span = reporter.getSpans().get(0);
    assertEquals("GET:/hello/world/{worldId}", span.getOperationName());
    assertCache("getHello");
  }

  @Test
  public void testOperationNameWithNakedGet() throws Exception {
    Response response = target("stormlord/").request().get();
    assertEquals(200, response.getStatus());
    Span span = reporter.getSpans().get(0);
    assertEquals("GET:/stormlord", span.getOperationName());
    assertCache("nakedGet");
  }

  @Test
  public void testCacheForBothOperations() throws Exception {
    Response r1 = target("stormlord/").request().get();
    assertEquals(200, r1.getStatus());
    Response r2 = target("hello/world/middle-earth").request().get();
    assertEquals(200, r2.getStatus());

    Set<Map.Entry<JerseyServerFilter.CacheKey, String>> entries = undertest.getCache().entrySet();
    Set<String> valueSet = new HashSet<>(2);
    for (Map.Entry<JerseyServerFilter.CacheKey, String> entry: entries) {
      valueSet.add(entry.getValue());
    }

    assertTrue(valueSet.contains("GET:/stormlord"));
    assertTrue(valueSet.contains("GET:/hello/world/{worldId}"));
  }

  private void assertCache(String methodName) {
    Map<JerseyServerFilter.CacheKey, String> cache = undertest.getCache();
    assertEquals(1, cache.size());
    JerseyServerFilter.CacheKey key = cache.keySet().iterator().next();
    assertEquals("GET", key.getHttpMethod());
    assertEquals(methodName, key.getResourceMethod().getName());
  }

}
