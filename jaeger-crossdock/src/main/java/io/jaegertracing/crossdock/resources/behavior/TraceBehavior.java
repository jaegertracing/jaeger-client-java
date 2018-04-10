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

package io.jaegertracing.crossdock.resources.behavior;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.tchannel.api.SubChannel;
import com.uber.tchannel.api.TFuture;
import com.uber.tchannel.messages.ThriftRequest;
import com.uber.tchannel.messages.ThriftResponse;
import io.jaegertracing.Span;
import io.jaegertracing.SpanContext;
import io.jaegertracing.crossdock.Constants;
import io.jaegertracing.crossdock.JerseyServer;
import io.jaegertracing.crossdock.api.Downstream;
import io.jaegertracing.crossdock.api.JoinTraceRequest;
import io.jaegertracing.crossdock.api.ObservedSpan;
import io.jaegertracing.crossdock.api.TraceResponse;
import io.jaegertracing.crossdock.resources.behavior.tchannel.TChannelServer;
import io.jaegertracing.crossdock.thrift.TracedService;
import io.opentracing.Tracer;
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
  private final Tracer tracer;

  public TraceBehavior(Tracer tracer) {
    this.tracer = tracer;
  }

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
    io.jaegertracing.crossdock.thrift.JoinTraceRequest joinTraceRequest =
        new io.jaegertracing.crossdock.thrift.JoinTraceRequest(downstream.getServerRole());
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
    Span span = (Span)tracer.activeSpan();
    if (tracer.activeSpan() == null) {
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
