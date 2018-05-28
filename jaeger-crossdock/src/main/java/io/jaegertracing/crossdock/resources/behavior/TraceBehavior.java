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
import io.jaegertracing.crossdock.Constants;
import io.jaegertracing.crossdock.JerseyServer;
import io.jaegertracing.crossdock.api.Downstream;
import io.jaegertracing.crossdock.api.JoinTraceRequest;
import io.jaegertracing.crossdock.api.ObservedSpan;
import io.jaegertracing.crossdock.api.TraceResponse;
import io.jaegertracing.internal.Span;
import io.jaegertracing.internal.SpanContext;
import io.opentracing.Tracer;
import java.io.IOException;
import java.net.InetAddress;
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
}
