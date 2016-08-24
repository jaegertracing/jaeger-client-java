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
package com.uber.jaeger.crossdock.resources.behavior.http;

import com.uber.jaeger.Span;
import com.uber.jaeger.context.TracingUtils;
import com.uber.jaeger.crossdock.Constants;
import com.uber.jaeger.crossdock.JerseyServer;
import com.uber.jaeger.crossdock.resources.behavior.TraceBehavior;
import com.uber.jaeger.crossdock.resources.behavior.tchannel.TChannelServer;
import com.uber.jaeger.crossdock.tracetest_manual.Downstream;
import com.uber.jaeger.crossdock.tracetest_manual.JoinTraceRequest;
import com.uber.jaeger.crossdock.tracetest_manual.ObservedSpan;
import com.uber.jaeger.crossdock.tracetest_manual.StartTraceRequest;
import com.uber.jaeger.crossdock.tracetest_manual.TraceResponse;
import io.opentracing.tag.Tags;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class TraceBehaviorResourceTest {
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
        boolean[] sampling = new boolean[]{true, false};
        List<Object[]> data = new ArrayList<>();
        for (boolean sampled : sampling) {
            data.add(new Object[]{sampled});
        }
        return data;
    }

    @BeforeClass
    public static void setUpLogger() {
        BasicConfigurator.configure();
    }

    @Before
    public void setUp() throws Exception {
        port = System.getenv("TRACE_TEST_BEHAVIOR_PORT");
        if (port == null) {
            port = "55555";
        }
        hostPort = String.format("127.0.0.1:%s", port);
        server = new JerseyServer(hostPort, TraceBehaviorResource.class);
        behavior = new TraceBehavior();
    }

    @After
    public void tearDown() {
        server.shutdown();
    }

    @Test
    public void testStartTraceHttp() throws Exception {
        Span span = (Span) server.getTracer().buildSpan("root").start();
        TracingUtils.getTraceContext().push(span);

        String expectedTraceId = String.format("%x", span.getContext().getTraceID());
        String expectedBaggage = "baggage-example";

        Downstream downstream = new Downstream("java", "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", null);
        StartTraceRequest startTraceRequest = new StartTraceRequest("server-role", expectedSampled, expectedBaggage, downstream);

        Response resp = JerseyServer.client.target(String.format("http://%s/start_trace", hostPort))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(startTraceRequest));

        TraceResponse traceResponse = resp.readEntity(TraceResponse.class);

        assertNotNull(traceResponse.getDownstream());
        validateTraceResponse(
                traceResponse,
                expectedTraceId,
                expectedBaggage,
                1);
    }

    @Test
    public void testJoinTraceHttp() throws Exception {
        Span span = (Span) server.getTracer().buildSpan("root").start();
        TracingUtils.getTraceContext().push(span);

        String expectedTraceId = String.format("%x", span.getContext().getTraceID());
        String expectedBaggage = "baggage-example";
        span.setBaggageItem(Constants.BAGGAGE_KEY, expectedBaggage);
        if (expectedSampled) {
            Tags.SAMPLING_PRIORITY.set(span, (short) 1);
        }

        Downstream bottomDownstream = new Downstream("java", "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", null);
        Downstream topDownstream = new Downstream("java", "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", bottomDownstream);

        JoinTraceRequest joinTraceRequest = new JoinTraceRequest("server-role", topDownstream);

        Response resp = JerseyServer.client
                .target(String.format("http://%s/join_trace", hostPort))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(joinTraceRequest));

        TraceResponse traceResponse = resp.readEntity(TraceResponse.class);

        assertNotNull(traceResponse.getDownstream());
        validateTraceResponse(
                traceResponse,
                expectedTraceId,
                expectedBaggage, 2);
    }

    @Test
    public void testJoinTraceTChannel() throws Exception {
        TChannelServer tchannel = new TChannelServer(8081, behavior, server.getTracer());
        tchannel.start();

        Span span = (Span) server.getTracer().buildSpan("root").start();
        TracingUtils.getTraceContext().push(span);

        String expectedTraceId = String.format("%x", span.getContext().getTraceID());
        String expectedBaggage = "baggage-example";
        span.setBaggageItem(Constants.BAGGAGE_KEY, expectedBaggage);
        if (expectedSampled) {
            Tags.SAMPLING_PRIORITY.set(span, (short) 1);
        }

        TraceResponse response = behavior.callDownstreamTChannel(new Downstream(
                JerseyServer.SERVICE_NAME,
                tchannel.getChannel().getListeningHost(),
                String.valueOf(tchannel.getChannel().getListeningPort()),
                Constants.TRANSPORT_TCHANNEL,
                "s2",
                new Downstream(
                        JerseyServer.SERVICE_NAME,
                        tchannel.getChannel().getListeningHost(),
                        String.valueOf(tchannel.getChannel().getListeningPort()),
                        Constants.TRANSPORT_TCHANNEL,
                        "s3",
                        null)));
        assertNotNull(response);
        validateTraceResponse(
                response,
                expectedTraceId,
                expectedBaggage,
                1);

        tchannel.shutdown();
    }

    private void validateTraceResponse(
            TraceResponse response,
            String expectedTraceId,
            String expectedBaggage,
            int expectedDownstream
    ) {
        ObservedSpan span = response.getObservedSpan();
        assertEquals(expectedTraceId, span.getTraceID());
        assertEquals(expectedBaggage, span.getBaggage());
        assertEquals(expectedSampled, span.getSampled());

        TraceResponse downstream = response.getDownstream();
        if (expectedDownstream > 0) {
            assertNotNull(downstream);
            validateTraceResponse(
                    downstream,
                    expectedTraceId,
                    expectedBaggage,
                    expectedDownstream - 1);
        } else {
            assertNull(downstream);
        }
    }
}
