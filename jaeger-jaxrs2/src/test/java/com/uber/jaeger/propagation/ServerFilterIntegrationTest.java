package com.uber.jaeger.propagation;

import static com.uber.jaeger.Constants.DEBUG_ID_HEADER_KEY;
import static com.uber.jaeger.Constants.DEBUG_JAEGER_SPAN_BAGGAGE_HEADER;
import static org.junit.Assert.assertEquals;

import com.uber.jaeger.context.ActiveSpanSourceTraceContext;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.filters.jaxrs2.ClientFilter;
import com.uber.jaeger.filters.jaxrs2.ServerFilter;
import com.uber.jaeger.reporters.NoopReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.io.IOException;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ServerFilterIntegrationTest extends JerseyTest {

  // Base URI the Grizzly HTTP server will listen on
  private static final String BASE_URI = "http://localhost:8080/";

  private Tracer clientTracer =
      new com.uber.jaeger.Tracer
          .Builder("client-tracer", new NoopReporter(), new ConstSampler(true))
          .build();

  private TraceContext clientTraceContext = new ActiveSpanSourceTraceContext(clientTracer);

  private HttpServer server;

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
      server.stop();
    }
  }

  @Override
  protected Application configure() {

    io.opentracing.Tracer serverTracer =
        new com.uber.jaeger.Tracer
            .Builder("server-tracer", new NoopReporter(), new ConstSampler(true))
            .build();

    TraceContext serverTraceContext = new ActiveSpanSourceTraceContext(serverTracer);
    
    // Our 'Application'
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .register(new ServerFilter(serverTracer, serverTraceContext), Priorities.USER + 10)
            .register(new TestHookFilter(serverTraceContext), Priorities.USER + 20)
            .register(TestResource.class);

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
    com.uber.jaeger.Span span = (com.uber.jaeger.Span) clientTracer
        .buildSpan("root-span")
        .startManual();
    span.setBaggageItem("x-test", "testing");
    clientTraceContext.push(span);

    Response resp = target()
        .register(new ClientFilter(clientTracer, clientTraceContext))
        .path("test")
        .request()
        .header(DEBUG_JAEGER_SPAN_BAGGAGE_HEADER, "foo=bar")
        .get();
    assertEquals(resp.getStatus(), 200);
  }

  @Test
  public void testTracingUsingInstrumentedClientWithFakeBaggageHeader() {
    Response resp = target()
        .register(new ClientFilter(clientTracer, clientTraceContext))
        .path("test")
        .request()
        .header(DEBUG_JAEGER_SPAN_BAGGAGE_HEADER, "foo=bar")
        .header("uberctx-x-test", "testing")
        .get();
    assertEquals(resp.getStatus(), 200);
  }

  @Test
  public void testTracingUsingNonInstrumentedClient() {
    Response resp = target()
        .path("test")
        .request()
        .header(DEBUG_ID_HEADER_KEY, "12345")
        .header(DEBUG_JAEGER_SPAN_BAGGAGE_HEADER, "foo=bar,x-test=testing")
        .get();
    assertEquals(resp.getStatus(), 200);
  }

  @Test
  public void testTracingUsingNonInstrumentedClientWithFakeBaggageHeader() {
    Response resp = target()
        .path("test")
        .request()
        .header(DEBUG_ID_HEADER_KEY, "12345")
        .header(DEBUG_JAEGER_SPAN_BAGGAGE_HEADER, "foo=bar")
        .header("uberctx-x-test", "testing")
        .get();
    assertEquals(resp.getStatus(), 200);
  }

  @Test
  public void testTracingUsingNonInstrumentedClientAndNoDebugId() {
    /**
     * this test shows that in case where there is no uber-trace-id (non-instrumented client),
     * only way to force baggage to get populated is to include the ``"jaeger-debug-id"`` header.
     */
    Response resp = target()
        .path("test")
        .request()
        .header(DEBUG_JAEGER_SPAN_BAGGAGE_HEADER, "foo=bar")
        .header("uberctx-x-test", "testing")
        .get();
    assertEquals(resp.getStatus(), 500);
  }

  class TestHookFilter implements ContainerRequestFilter {

    private final TraceContext serverTraceContext;

    public TestHookFilter(TraceContext serverTraceContext) {
      this.serverTraceContext = serverTraceContext;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
      Span curSpan = serverTraceContext.getCurrentSpan();

      if (curSpan == null) {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity("Whoops! No span; make sure that the ServerFilter is in place")
                .build()
        );
        return;
      }

      assertEquals(curSpan.getBaggageItem("x-test"), "testing");
      assertEquals(curSpan.getBaggageItem("foo"), "bar");
    }
  }

  @Path("test")
  public static class TestResource {

    @GET
    public Response test(@Context ContainerRequestContext ctx) {
      return Response.ok().build();
    }
  }
}
