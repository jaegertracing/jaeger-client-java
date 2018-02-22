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

package com.uber.jaeger.propagation;

import static com.uber.jaeger.Constants.DEBUG_ID_HEADER_KEY;
import static com.uber.jaeger.Constants.MANUAL_BAGGAGE_HEADER_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.uber.jaeger.context.ScopeManagerTraceContext;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.filters.jaxrs2.ClientFilter;
import com.uber.jaeger.filters.jaxrs2.ServerFilter;
import com.uber.jaeger.reporters.NoopReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Priorities;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import jersey.repackaged.com.google.common.collect.ImmutableMap;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ServerFilterIntegrationTest extends JerseyTest {

  // Base URI the Grizzly HTTP server will listen on
  private static final String BASE_URI = "http://localhost:0/";

  private Tracer clientTracer =
      new com.uber.jaeger.Tracer
          .Builder("client-tracer", new NoopReporter(), new ConstSampler(true))
          .build();

  private TraceContext clientTraceContext =
      new ScopeManagerTraceContext(clientTracer.scopeManager());

  private HttpServer server;

  private final Gson gson = new Gson();

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    // start the server
    final ResourceConfig rc = (ResourceConfig) configure();
    server = GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();

    // stop the server
    if (server.isStarted()) {
      server.shutdownNow();
    }

    // clean the client side trace context and close out the tracer
    while (!clientTraceContext.isEmpty()) {
      clientTraceContext.pop();
    }
    ((com.uber.jaeger.Tracer) clientTracer).close();
  }

  @Override
  protected Application configure() {

    io.opentracing.Tracer serverTracer =
        new com.uber.jaeger.Tracer
            .Builder("server-tracer", new NoopReporter(), new ConstSampler(true))
            .build();

    // Our 'Application'
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .register(new ServerFilter(serverTracer), Priorities.USER + 10)
            .register(new TestResource(serverTracer));

    return resourceConfig;
  }

  @After
  public void teardownTestingInfra() {
    // stop server
    if (server.isStarted()) {
      server.stop();
    }
  }

  @Test
  public void testTracingUsingInstrumentedClient() {
    /**
     * Goal:
     * When trace ID is present
     *    - baggage from uberctx- is respected
     *    - baggage from jaeger-baggage is not respected
     * Debug ID is always respected regardless of other data
     */
    com.uber.jaeger.Span span = (com.uber.jaeger.Span) clientTracer
        .buildSpan("root-span")
        .start();
    span.setBaggageItem("x-test", "testing");  // uberctx- baggage
    clientTraceContext.push(span);

    Response response = target()
        .register(new ClientFilter(clientTracer))
        .path("test")
        .request()
        .header(MANUAL_BAGGAGE_HEADER_KEY, "foo=bar")  // debug jaeger baggage
        .get();
    assertEquals(response.getStatus(), 200);

    String responseString = response.readEntity(String.class);
    JsonElement jsonElement =
        gson.fromJson(responseString, JsonElement.class);
    JsonObject jsonResponse = jsonElement.getAsJsonObject();

    // baggage from uberctx- is respected
    assertEquals("testing", jsonResponse.get("x-test").getAsString());

    // baggage from jaeger-baggage is not respected
    assertNotEquals("bar", jsonResponse.get("foo").getAsString());
  }

  @Test
  public void testTracingUsingInstrumentedClientWithFakeBaggageHeaderButNoDebugTraceId() {
    /**
     * Goal:
     * When trace ID is present
     *    - baggage from uberctx- is respected
     *    - baggage from jaeger-baggage is not respected
     */
    Response response = target()
        .register(new ClientFilter(clientTracer))
        .path("test")
        .request()
        .header(MANUAL_BAGGAGE_HEADER_KEY, "foo=bar")  // debug jaeger baggage
        .header("uberctx-x-test", "testing")  // uberctx- baggage
        .get();
    assertEquals(response.getStatus(), 200);

    String responseString = response.readEntity(String.class);
    JsonElement jsonElement =
        gson.fromJson(responseString, JsonElement.class);
    JsonObject jsonResponse = jsonElement.getAsJsonObject();

    assertEquals("testing", jsonResponse.get("x-test").getAsString());
    assertNotEquals("bar", jsonResponse.get("foo").getAsString());
  }

  @Test
  public void testTracingUsingInstrumentedClientWithFakeBaggageHeaderAndDebugTraceId() {
    /**
     * Goal:
     * When both trace ID and debug ID is present
     *    - baggage from uberctx- is not respected
     *    - baggage from jaeger-baggage is respected
     */
    Response response = target()
        .register(new ClientFilter(clientTracer))
        .path("test")
        .request()
        .header(DEBUG_ID_HEADER_KEY, "")
        .header(MANUAL_BAGGAGE_HEADER_KEY, "foo=bar")  // debug jaeger baggage
        .header("uberctx-x-test", "testing")  // uberctx- baggage
        .get();
    assertEquals(response.getStatus(), 200);

    String responseString = response.readEntity(String.class);
    JsonElement jsonElement =
        gson.fromJson(responseString, JsonElement.class);
    JsonObject jsonResponse = jsonElement.getAsJsonObject();

    assertNotEquals("testing", jsonResponse.get("x-test").getAsString());
    assertEquals("bar", jsonResponse.get("foo").getAsString());
  }

  @Test
  public void testTracingUsingNonInstrumentedClient() {
    /**
     * Goal:
     * When trace ID is not present, and debug ID is present
     *    - baggage from jaeger-baggage is respected
     */
    Response response = target()
        .path("test")
        .request()
        .header(DEBUG_ID_HEADER_KEY, "12345")
        .header(MANUAL_BAGGAGE_HEADER_KEY, "foo=bar,x-test=testing")
        .get();
    assertEquals(response.getStatus(), 200);

    String responseString = response.readEntity(String.class);
    JsonElement jsonElement =
        gson.fromJson(responseString, JsonElement.class);
    JsonObject jsonResponse = jsonElement.getAsJsonObject();
    assertEquals(jsonResponse.get("x-test").getAsString(), "testing");
    assertEquals(jsonResponse.get("foo").getAsString(), "bar");
  }

  @Test
  public void testTracingUsingNonInstrumentedClientWithFakeBaggageHeader() {
    /**
     * Goal:
     * When trace ID is not present, and debug ID is present
     *    - baggage from uberctx- is not respected
     *    - baggage from jaeger-baggage is respected
     */
    Response response = target()
        .path("test")
        .request()
        .header(DEBUG_ID_HEADER_KEY, "12345")
        .header(MANUAL_BAGGAGE_HEADER_KEY, "foo=bar")
        .header("uberctx-x-test", "testing")
        .get();
    assertEquals(response.getStatus(), 200);

    String responseString = response.readEntity(String.class);
    JsonElement jsonElement =
        gson.fromJson(responseString, JsonElement.class);
    JsonObject jsonResponse = jsonElement.getAsJsonObject();
    assertNotEquals("testing", jsonResponse.get("x-test").getAsString());
    assertEquals("bar", jsonResponse.get("foo").getAsString());
  }

  @Test
  public void testTracingUsingNonInstrumentedClientAndNoDebugId() {
    /**
     * Goal:
     * When neither trace ID, nor debug ID is present, no baggage population takes place.
     */
    Response response = target()
        .path("test")
        .request()
        .header(MANUAL_BAGGAGE_HEADER_KEY, "foo=bar")
        .header("uberctx-x-test", "testing")
        .get();
    assertEquals(response.getStatus(), 200);

    String responseString = response.readEntity(String.class);
    JsonElement jsonElement =
        gson.fromJson(responseString, JsonElement.class);
    JsonObject jsonResponse = jsonElement.getAsJsonObject();
    assertNotEquals("testing", jsonResponse.get("x-test").getAsString());
    assertNotEquals("bar", jsonResponse.get("foo").getAsString());
  }

  @Path("test")
  public static class TestResource {

    private final Tracer serverTracer;

    public TestResource(Tracer serverTracer) {
      this.serverTracer = serverTracer;
    }

    @GET
    @Produces({"application/json"})
    public Response test(@Context ContainerRequestContext requestContext) {
      Span curSpan = serverTracer.activeSpan();

      if (curSpan == null) {
        String response = new GsonBuilder()
            .create()
            .toJson(ImmutableMap
              .of("error", "Whoops! No span; make sure that the ServerFilter is in place")
            );

        return Response
            .status(Status.UNAUTHORIZED)
            .entity(response)
            .build();
      }

      String response = new GsonBuilder()
          .create()
          .toJson(ImmutableMap
              .builder()
              .put("x-test", String.valueOf(curSpan.getBaggageItem("x-test")))
              .put("foo", String.valueOf(curSpan.getBaggageItem("foo")))
              .build());

      return Response
          .ok()
          .entity(response)
          .build();
    }
  }
}
