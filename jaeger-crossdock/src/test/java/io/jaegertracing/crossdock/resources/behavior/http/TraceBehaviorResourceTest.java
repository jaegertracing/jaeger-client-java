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

package io.jaegertracing.crossdock.resources.behavior.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.jaegertracing.Configuration;
import io.jaegertracing.Configuration.ReporterConfiguration;
import io.jaegertracing.Configuration.SamplerConfiguration;
import io.jaegertracing.Span;
import io.jaegertracing.crossdock.Constants;
import io.jaegertracing.crossdock.JerseyServer;
import io.jaegertracing.crossdock.api.Downstream;
import io.jaegertracing.crossdock.api.JoinTraceRequest;
import io.jaegertracing.crossdock.api.ObservedSpan;
import io.jaegertracing.crossdock.api.StartTraceRequest;
import io.jaegertracing.crossdock.api.TraceResponse;
import io.jaegertracing.crossdock.resources.behavior.TraceBehavior;
import io.jaegertracing.samplers.ConstSampler;
import io.opentracing.Scope;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TraceBehaviorResourceTest {
  private static final String SERVICE_NAME = "foo";

  private JerseyServer server;
  private TraceBehavior behavior;
  private int port;
  private String hostPort;

  private final boolean expectedSampled;

  public TraceBehaviorResourceTest(boolean sampled, int port) {
    this.expectedSampled = sampled;
    this.port = port;
  }

  @Parameterized.Parameters(name = "{index}: sampled({0})")
  public static Collection<Object[]> data() throws IOException {
    return Arrays.asList(new Object[][] {
        {true, new ServerSocket(0).getLocalPort()},
        {false, new ServerSocket(0).getLocalPort()}
    });
  }

  @BeforeClass
  public static void setUpLogger() {
    BasicConfigurator.configure();
  }

  @Before
  public void setUp() throws Exception {
    Configuration configuration = new Configuration(SERVICE_NAME).withSampler(
        new SamplerConfiguration().withType(ConstSampler.TYPE).withParam(0))
        .withReporter(new ReporterConfiguration().withLogSpans(true));
    server = new JerseyServer("127.0.0.1", port, configuration,
        Collections.singletonList(new TraceBehaviorResource(configuration.getTracer())));
    hostPort = String.format("127.0.0.1:%d", port);
    behavior = new TraceBehavior(server.getTracer());
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
    // Reset opentracing's global tracer
    Field field = GlobalTracer.class.getDeclaredField("tracer");
    field.setAccessible(true);
    field.set(null, NoopTracerFactory.create());
  }

  @Test
  public void testStartTraceHttp() throws Exception {
    Scope scope = server.getTracer().buildSpan("root").startActive(true);
    String expectedTraceId = String.format("%x", ((Span)scope.span()).context().getTraceId());
    String expectedBaggage = "baggage-example";

    Downstream downstream =
        new Downstream(SERVICE_NAME, "127.0.0.1", String.valueOf(port), Constants.TRANSPORT_HTTP, "server", null);
    StartTraceRequest startTraceRequest =
        new StartTraceRequest("server-role", expectedSampled, expectedBaggage, downstream);

    Response resp =
        JerseyServer.client
            .target(String.format("http://%s/start_trace", hostPort))
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(startTraceRequest));

    TraceResponse traceResponse = resp.readEntity(TraceResponse.class);

    assertNotNull(traceResponse.getDownstream());
    validateTraceResponse(traceResponse, expectedTraceId, expectedBaggage, 1);
    scope.close();
  }

  @Test
  public void testJoinTraceHttp() throws Exception {
    Scope scope = server.getTracer().buildSpan("root").startActive(true);

    String expectedBaggage = "baggage-example";
    scope.span().setBaggageItem(Constants.BAGGAGE_KEY, expectedBaggage);
    if (expectedSampled) {
      Tags.SAMPLING_PRIORITY.set(scope.span(), 1);
    }

    Downstream bottomDownstream =
        new Downstream(SERVICE_NAME, "127.0.0.1", String.valueOf(port), Constants.TRANSPORT_HTTP, "server", null);
    Downstream topDownstream =
        new Downstream(
            SERVICE_NAME, "127.0.0.1", String.valueOf(port), Constants.TRANSPORT_HTTP, "server", bottomDownstream);

    JoinTraceRequest joinTraceRequest = new JoinTraceRequest("server-role", topDownstream);

    Response resp =
        JerseyServer.client
            .target(String.format("http://%s/join_trace", hostPort))
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(joinTraceRequest));

    TraceResponse traceResponse = resp.readEntity(TraceResponse.class);

    assertNotNull(traceResponse.getDownstream());
    validateTraceResponse(traceResponse, String.format("%x",
        ((Span)scope.span()).context().getTraceId()), expectedBaggage, 2);
    scope.close();
  }

  private void validateTraceResponse(
      TraceResponse response,
      String expectedTraceId,
      String expectedBaggage,
      int expectedDownstream) {
    ObservedSpan span = response.getSpan();
    assertEquals(expectedTraceId, span.getTraceId());
    assertEquals(expectedBaggage, span.getBaggage());
    assertEquals(expectedSampled, span.isSampled());

    TraceResponse downstream = response.getDownstream();
    if (expectedDownstream > 0) {
      assertNotNull(downstream);
      validateTraceResponse(downstream, expectedTraceId, expectedBaggage, expectedDownstream - 1);
    } else {
      assertNull(downstream);
    }
  }
}
