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

import com.uber.jaeger.Span;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import java.lang.reflect.Field;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

public class JerseyServerFilterTest extends JerseyTest {

  private InMemoryReporter reporter;

  @Override
  protected Application configure() {
    Configuration config = new Configuration("world service", false,
                                             new com.uber.jaeger.Configuration.SamplerConfiguration(ConstSampler.TYPE,
                                                                                                    1),
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
    resourceConfig.register(new JaegerFeature(config));
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
    assertEquals("GET - /hello/world/{worldId}", span.getOperationName());
  }

  @Test
  public void testOperationNameWithNakedGet() throws Exception {
    Response response = target("stormlord/").request().get();
    assertEquals(200, response.getStatus());
    Span span = reporter.getSpans().get(0);
    assertEquals("GET - /stormlord", span.getOperationName());
  }

}
