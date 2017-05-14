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

package com.uber.jaeger.dropwizard;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import com.uber.jaeger.Span;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
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
import org.junit.Test;

public class JerseyServerFilterTest extends JerseyTest {

  private InMemoryReporter reporter;
  private JerseyServerFilter undertest;

  @Override
  protected Application configure() {
    Configuration config = new Configuration("world service", false,
        new com.uber.jaeger.Configuration.SamplerConfiguration(ConstSampler.TYPE, 1),
        null);
    reporter = new InMemoryReporter();
    com.uber.jaeger.Tracer tracer = (com.uber.jaeger.Tracer) config.getTracer();
    try {
      Field reporter = com.uber.jaeger.Tracer.class.getDeclaredField("reporter");
      reporter.setAccessible(true);
      reporter.set(tracer, this.reporter);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    ResourceConfig resourceConfig = new ResourceConfig(HelloResource.class, StormlordResource.class);
    undertest = new JerseyServerFilter(tracer, com.uber.jaeger.context.TracingUtils.getTraceContext());
    resourceConfig.register(undertest);
    return resourceConfig;
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
