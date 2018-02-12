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

package com.uber.jaeger.crossdock.resources.behavior.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.Configuration.ReporterConfiguration;
import com.uber.jaeger.Configuration.SamplerConfiguration;
import com.uber.jaeger.Span;
import com.uber.jaeger.crossdock.Constants;
import com.uber.jaeger.crossdock.JerseyServer;
import com.uber.jaeger.crossdock.api.Downstream;
import com.uber.jaeger.crossdock.api.JoinTraceRequest;
import com.uber.jaeger.crossdock.api.ObservedSpan;
import com.uber.jaeger.crossdock.api.StartTraceRequest;
import com.uber.jaeger.crossdock.api.TraceResponse;
import com.uber.jaeger.crossdock.resources.behavior.TraceBehavior;
import com.uber.jaeger.crossdock.resources.behavior.tchannel.TChannelServer;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.tchannel.api.TChannel.Builder;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
  private String port;
  private String hostPort;

  private final boolean expectedSampled;

  public TraceBehaviorResourceTest(boolean sampled) {
    this.expectedSampled = sampled;
  }

  @Parameterized.Parameters(name = "{index}: sampled({0})")
  public static Collection<Object[]> data() {
    boolean[] sampling = new boolean[] {true, false};
    List<Object[]> data = new ArrayList<>();
    for (boolean sampled : sampling) {
      data.add(new Object[] {sampled});
    }
    return data;
  }

  @BeforeClass
  public static void setUpLogger() {
    BasicConfigurator.configure();
  }

  @Before
  public void setUp() throws Exception {
    Configuration config = getJaegerConfiguration();
    Tracer tracer = config.getTracer();
    server = new JerseyServer("127.0.0.1:0", config, Collections.singletonList(new TraceBehaviorResource(tracer)));
    port = String.valueOf(server.getNetworkListeners().get(0).getPort());
    hostPort = String.format("127.0.0.1:%s", port);
    behavior = new TraceBehavior(tracer);
  }

  private Configuration getJaegerConfiguration() {
    String samplingType = ConstSampler.TYPE;
    Number samplingParam = 0;
    return new Configuration(
        SERVICE_NAME,
        new SamplerConfiguration(samplingType, samplingParam),
        new ReporterConfiguration(true, null, null, null, null));
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
    Tracer tracer = server.getTracer();
    Span span = (Span) tracer.buildSpan("root").start();

    tracer.scopeManager().activate(span, false);

    String expectedTraceId = String.format("%x", span.context().getTraceId());
    String expectedBaggage = "baggage-example";

    Downstream downstream =
        new Downstream(SERVICE_NAME, "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", null);
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
  }

  @Test
  public void testJoinTraceHttp() throws Exception {
    Tracer tracer = server.getTracer();
    Span span = (Span) tracer.buildSpan("root").start();

    tracer.scopeManager().activate(span, false);

    String expectedBaggage = "baggage-example";
    span.setBaggageItem(Constants.BAGGAGE_KEY, expectedBaggage);
    if (expectedSampled) {
      Tags.SAMPLING_PRIORITY.set(span, 1);
    }

    Downstream bottomDownstream =
        new Downstream(SERVICE_NAME, "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", null);
    Downstream topDownstream =
        new Downstream(
            SERVICE_NAME, "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", bottomDownstream);

    JoinTraceRequest joinTraceRequest = new JoinTraceRequest("server-role", topDownstream);

    Response resp =
        JerseyServer.client
            .target(String.format("http://%s/join_trace", hostPort))
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(joinTraceRequest));

    TraceResponse traceResponse = resp.readEntity(TraceResponse.class);

    assertNotNull(traceResponse.getDownstream());
    validateTraceResponse(traceResponse, String.format("%x", span.context().getTraceId()), expectedBaggage, 2);
  }

  @Test
  public void testJoinTraceTChannel() throws Exception {
    Builder tchannelBuilder = new Builder("foo");
    tchannelBuilder.setServerPort(0);
    tchannelBuilder.setServerHost(InetAddress.getLoopbackAddress());
    TChannelServer tchannel = new TChannelServer(tchannelBuilder, behavior, server.getTracer());
    tchannel.start();

    Tracer tracer = server.getTracer();
    Span span = (Span) tracer.buildSpan("root").start();

    tracer.scopeManager().activate(span, false);

    String expectedBaggage = "baggage-example";
    span.setBaggageItem(Constants.BAGGAGE_KEY, expectedBaggage);
    if (expectedSampled) {
      Tags.SAMPLING_PRIORITY.set(span, 1);
    }

    TraceResponse response =
        behavior.callDownstreamTChannel(
            new Downstream(SERVICE_NAME,
                tchannel.getChannel().getListeningHost(),
                String.valueOf(tchannel.getChannel().getListeningPort()),
                Constants.TRANSPORT_TCHANNEL,
                "s2",
                new Downstream(
                    SERVICE_NAME,
                    tchannel.getChannel().getListeningHost(),
                    String.valueOf(tchannel.getChannel().getListeningPort()),
                    Constants.TRANSPORT_TCHANNEL,
                    "s3",
                    null)));
    assertNotNull(response);
    validateTraceResponse(response, String.format("%x", span.context().getTraceId()), expectedBaggage, 1);

    tchannel.shutdown();
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
