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
import com.uber.jaeger.crossdock.resources.behavior.tchannel.TChannelServer;
import com.uber.jaeger.crossdock.tracetest.TracedService;
import com.uber.jaeger.crossdock.tracetest_manual.Downstream;
import com.uber.jaeger.crossdock.tracetest_manual.JoinTraceRequest;
import com.uber.jaeger.crossdock.tracetest_manual.ObservedSpan;
import com.uber.jaeger.crossdock.tracetest_manual.StartTraceRequest;
import com.uber.jaeger.crossdock.tracetest_manual.TraceResponse;
import com.uber.tchannel.api.SubChannel;
import com.uber.tchannel.api.TChannel;
import com.uber.tchannel.api.TFuture;
import com.uber.tchannel.messages.ThriftRequest;
import com.uber.tchannel.messages.ThriftResponse;
import com.uber.tchannel.utils.TChannelUtilities;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.junit.Assert.*;

public class TraceBehaviorResourceTest {
    private JerseyServer server;

    private String port;
    private String hostPort;

    @BeforeClass
    public static void setUpLogger() {
        BasicConfigurator.configure();
    }

    @Before
    public void setUp() throws Exception {
        port = System.getenv("TRACE_TEST_BEHAVIOR_PORT");
        if (port == null) {
            port = "32654";
        }
        hostPort = String.format("127.0.0.1:%s", port);
        server = new JerseyServer(hostPort, TraceBehaviorResource.class);
    }

    @After
    public void tearDown() {
        server.shutdown();
    }

    void validateTraceResponse(
            TraceResponse response,
            String expectedTraceId,
            String expectedBaggage,
            boolean expectedSampled,
            int expectedDownstream
    ) {
        ObservedSpan span = response.getObservedSpan();
        assertEquals(expectedTraceId, span.getTraceID());
        assertEquals(expectedBaggage, span.getBaggage());
        assertEquals(expectedSampled, span.getSampled());

        if (expectedDownstream > 0) {
            TraceResponse downstream = response.getDownstream();
            assertNotNull(downstream);
            validateTraceResponse(
                    downstream,
                    expectedTraceId,
                    expectedBaggage,
                    expectedSampled,
                    expectedDownstream - 1);
        }
    }

    @Test
    public void testStartTraceHttp() throws Exception {
        Span span = (Span) server.getTracer().buildSpan("root").start();
        TracingUtils.getTraceContext().push(span);

        String expectedTraceId = String.format("%x", span.getContext().getTraceID());
        String expectedBaggage = "baggage-example";
        boolean expectedSampled = true;

        Downstream downstream = new Downstream("java", "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", null);
        StartTraceRequest startTraceRequest = new StartTraceRequest("server-role", expectedSampled, expectedBaggage, downstream);

        Response resp = JerseyServer.client.target(String.format("http://%s/start_trace", hostPort))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(startTraceRequest));

        TraceResponse traceResponse = resp.readEntity(TraceResponse.class);

        assertNotNull(traceResponse.getDownstream());
        validateTraceResponse(traceResponse, expectedTraceId, expectedBaggage, expectedSampled, 1);
    }

    @Test
    public void testJoinTraceHttp() throws Exception {
        Span span = (Span) server.getTracer().buildSpan("root").start();
        TracingUtils.getTraceContext().push(span);

        String expectedTraceId = String.format("%x", span.getContext().getTraceID());
        String expectedBaggage = "baggage-example";
        boolean expectedSampled = false;
        span.setBaggageItem(Constants.BAGGAGE_KEY, expectedBaggage);

        Downstream bottomDownstream = new Downstream("java", "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", null);
        Downstream topDownstream = new Downstream("java", "127.0.0.1", port, Constants.TRANSPORT_HTTP, "server", bottomDownstream);

        JoinTraceRequest joinTraceRequest = new JoinTraceRequest("server-role", topDownstream);

        Response resp = JerseyServer.client
                .target(String.format("http://%s/join_trace", hostPort))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(joinTraceRequest));

        TraceResponse traceResponse = resp.readEntity(TraceResponse.class);

        assertNotNull(traceResponse.getDownstream());
        validateTraceResponse(traceResponse, expectedTraceId, expectedBaggage, expectedSampled, 2);
    }

    @Test
    public void testJoinTraceTChannel() throws Exception {
        // Start server
        TChannelServer server = new TChannelServer(Integer.parseInt(port));
        server.start();

        // Setup test data used in assertions and parameters
        com.uber.jaeger.crossdock.tracetest.JoinTraceRequest joinTraceRequest = new com.uber.jaeger.crossdock.tracetest.JoinTraceRequest("server-role");

        TChannel tchannel = new TChannel.Builder("java-client").build();
        SubChannel subChannel = tchannel.makeSubChannel(JerseyServer.SERVICE_NAME);

        TFuture<ThriftResponse<TracedService.joinTrace_result>> future = subChannel.send(
                new ThriftRequest.Builder<TracedService.joinTrace_args>(JerseyServer.SERVICE_NAME, "TracedService::joinTrace")
                        .setTimeout(1000)
                        .setBody(new TracedService.joinTrace_args(joinTraceRequest))
                        .build(),
                TChannelUtilities.getCurrentIp(),
                Integer.parseInt(port)
        );

        try (ThriftResponse<TracedService.joinTrace_result> traceResponse = future.get()) {
            com.uber.jaeger.crossdock.tracetest.TraceResponse response = traceResponse.getBody(TracedService.joinTrace_result.class).getSuccess();
            assertEquals(response.getNotImplementedError(), "TChannel not implemented for java.");
        }

        server.shutdown();
    }
}
