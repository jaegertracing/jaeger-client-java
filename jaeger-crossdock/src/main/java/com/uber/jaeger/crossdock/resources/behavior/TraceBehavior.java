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

package com.uber.jaeger.crossdock.resources.behavior;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.context.TracingUtils;
import com.uber.jaeger.crossdock.Constants;
import com.uber.jaeger.crossdock.JerseyServer;
import com.uber.jaeger.crossdock.api.Downstream;
import com.uber.jaeger.crossdock.api.JoinTraceRequest;
import com.uber.jaeger.crossdock.api.ObservedSpan;
import com.uber.jaeger.crossdock.api.TraceResponse;
import com.uber.jaeger.crossdock.resources.behavior.tchannel.TChannelServer;
import com.uber.jaeger.crossdock.thrift.TracedService;
import com.uber.tchannel.api.SubChannel;
import com.uber.tchannel.api.TFuture;
import com.uber.tchannel.messages.ThriftRequest;
import com.uber.tchannel.messages.ThriftResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TraceBehavior {
  private static final ObjectMapper mapper = new ObjectMapper();

  public TraceResponse prepareResponse(Downstream downstream) throws Exception {
    TraceResponse response = new TraceResponse(observeSpan());

    if (downstream != null) {
      TraceResponse downstreamResponse = callDownstream(downstream);
      response.setDownstream(downstreamResponse);
    }

    return response;
  }

  private TraceResponse callDownstream(Downstream downstream) throws Exception {
    log.info("Calling downstream {}", downstream);
    log.info(
        "Downstream service {} -> {}:{}",
        downstream.getServiceName(),
        InetAddress.getByName(downstream.getHost()),
        downstream.getPort());
    String transport = downstream.getTransport();
    switch (transport) {
      case Constants.TRANSPORT_HTTP:
        return callDownstreamHttp(downstream);
      case Constants.TRANSPORT_TCHANNEL:
        return callDownstreamTChannel(downstream);
      default:
        return new TraceResponse("Unrecognized transport received: %s" + transport);
    }
  }

  private TraceResponse callDownstreamHttp(Downstream downstream) throws IOException {
    String downstreamUrl =
        String.format("http://%s:%s/join_trace", downstream.getHost(), downstream.getPort());
    log.info("Calling downstream http {} at {}", downstream.getServiceName(), downstreamUrl);

    Response resp =
        JerseyServer.client
            .target(downstreamUrl)
            .request(MediaType.APPLICATION_JSON)
            .post(
                Entity.json(
                    new JoinTraceRequest(downstream.getServerRole(), downstream.getDownstream())));

    String respStr = resp.readEntity(String.class);
    TraceResponse response = mapper.readValue(respStr, TraceResponse.class);
    log.info("Received response {}", response);
    return response;
  }

  public TraceResponse callDownstreamTChannel(Downstream downstream) throws Exception {
    com.uber.jaeger.crossdock.thrift.JoinTraceRequest joinTraceRequest =
        new com.uber.jaeger.crossdock.thrift.JoinTraceRequest(downstream.getServerRole());
    joinTraceRequest.setDownstream(Downstream.toThrift(downstream.getDownstream()));

    SubChannel subChannel = TChannelServer.server.makeSubChannel(downstream.getServiceName());

    log.info("Calling downstream tchannel {}", joinTraceRequest);
    ThriftRequest<TracedService.joinTrace_args> thriftRequest =
        new ThriftRequest.Builder<TracedService.joinTrace_args>(
                downstream.getServiceName(), "TracedService::joinTrace")
            .setTimeout(2000)
            .setBody(new TracedService.joinTrace_args(joinTraceRequest))
            .build();
    TFuture<ThriftResponse<TracedService.joinTrace_result>> future =
        subChannel.send(thriftRequest, host(downstream), port(downstream));

    try (ThriftResponse<TracedService.joinTrace_result> thriftResponse = future.get()) {
      log.info("Received tchannel response {}", thriftResponse);
      if (thriftResponse.isError()) {
        throw new Exception(thriftResponse.getError().getMessage());
      }
      return TraceResponse.fromThrift(
          thriftResponse.getBody(TracedService.joinTrace_result.class).getSuccess());
    }
  }

  private ObservedSpan observeSpan() {
    com.uber.jaeger.context.TraceContext traceContext = TracingUtils.getTraceContext();
    if (traceContext.isEmpty()) {
      log.error("No span found");
      return new ObservedSpan("no span found", false, "no span found");
    }
    Span span = (Span) traceContext.getCurrentSpan();
    if (span == null) {
      log.error("No span found");
      return new ObservedSpan("no span found", false, "no span found");
    }

    SpanContext context = span.context();
    String traceId = String.format("%x", context.getTraceId());
    boolean sampled = context.isSampled();
    String baggage = span.getBaggageItem(Constants.BAGGAGE_KEY);
    return new ObservedSpan(traceId, sampled, baggage);
  }

  private InetAddress host(Downstream downstream) {
    try {
      return InetAddress.getByName(downstream.getHost());
    } catch (UnknownHostException e) {
      throw new RuntimeException("Cannot resolve host address for " + downstream.getHost(), e);
    }
  }

  private int port(Downstream downstream) {
    return Integer.parseInt(downstream.getPort());
  }
}
