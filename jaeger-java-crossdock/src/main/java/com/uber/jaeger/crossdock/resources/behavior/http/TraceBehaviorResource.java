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

import java.io.IOException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.crossdock.Constants;
import com.uber.jaeger.crossdock.JerseyServer;
import com.uber.jaeger.crossdock.tracetest_manual.Downstream;
import com.uber.jaeger.crossdock.tracetest_manual.JoinTraceRequest;
import com.uber.jaeger.crossdock.tracetest_manual.ObservedSpan;
import com.uber.jaeger.crossdock.tracetest_manual.StartTraceRequest;
import com.uber.jaeger.crossdock.tracetest_manual.TraceResponse;
import com.uber.jaeger.filters.jaxrs2.TracingUtils;
import io.opentracing.tag.Tags;

@Path("")
@Provider
public class TraceBehaviorResource {
    private ObjectMapper mapper = new ObjectMapper();

    @POST
    @Path("start_trace")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TraceResponse startTrace(StartTraceRequest startRequest) throws IOException {
        Span span = (Span) TracingUtils.getTraceContext().getCurrentSpan();
        String baggage = startRequest.getBaggage();
        span.setBaggageItem(Constants.BAGGAGE_KEY, baggage);
        if (startRequest.getSampled()) {
            Tags.SAMPLING_PRIORITY.set(span, (short) 1);
        }

        TraceResponse response = prepareResponse(startRequest.getDownstream());

        System.out.println("start_trace response: " + mapper.writeValueAsString(response));

        return response;
    }

    @POST
    @Path("join_trace")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TraceResponse joinTrace(JoinTraceRequest joinRequest) throws IOException {
        TraceResponse response = prepareResponse(joinRequest.getDownstream());
        System.out.println("join_trace response: " + mapper.writeValueAsString(response));
        return response;
    }

    private TraceResponse prepareResponse(Downstream downstream) throws IOException {
        TraceResponse response = new TraceResponse(observeSpan());

        if (downstream != null) {
            TraceResponse downstreamResponse = callDownstream(downstream);
            response.setDownstream(downstreamResponse);
        }

        return response;
    }

    private TraceResponse callDownstream(Downstream downstream) throws IOException {
        String transport = downstream.getTransport();
        switch (transport) {
            case Constants.TRANSPORT_HTTP:
                return callDownstreamHTTP(downstream);
            case Constants.TRANSPORT_TCHANNEL:
                return new TraceResponse("TChannel support not implemented for java");
            default:
                throw new IllegalArgumentException("Unrecognized transport received: %s" + transport);
        }
    }

    private TraceResponse callDownstreamHTTP(Downstream downstream) throws IOException {
        String downstreamURL = String.format("http://%s:%s/join_trace", downstream.getHost(), downstream.getPort());

        System.out.println(String.format("Calling downstream service %s at %s", downstream.getServiceName(), downstreamURL));

        Response resp = JerseyServer.client.target(downstreamURL)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(new JoinTraceRequest(downstream.getServerRole(), downstream.getDownstream())));

        String respStr = resp.readEntity(String.class);
        return mapper.readValue(respStr, TraceResponse.class);
    }

    private ObservedSpan observeSpan() {
        com.uber.jaeger.context.TraceContext traceContext = TracingUtils.getTraceContext();
        Span span = (Span) traceContext.getCurrentSpan();
        if (span == null) {
            throw new IllegalStateException("null span received in observeSpan");
        }

        SpanContext context = span.getContext();
        String traceID = String.format("%x", context.getTraceID());
        boolean sampled = context.isSampled();
        String baggage = span.getBaggageItem(Constants.BAGGAGE_KEY);
        return new ObservedSpan(traceID, sampled, baggage);
    }
}
