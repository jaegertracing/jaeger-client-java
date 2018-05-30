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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.crossdock.Constants;
import io.jaegertracing.crossdock.api.JoinTraceRequest;
import io.jaegertracing.crossdock.api.StartTraceRequest;
import io.jaegertracing.crossdock.api.TraceResponse;
import io.jaegertracing.crossdock.resources.behavior.TraceBehavior;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Path("")
@Provider
@Slf4j
public class TraceBehaviorResource {
  private final ObjectMapper mapper = new ObjectMapper();
  private final TraceBehavior behavior;
  private final Tracer tracer;

  public TraceBehaviorResource(Tracer tracer) {
    this.tracer = tracer;
    this.behavior = new TraceBehavior(tracer);
  }

  @POST
  @Path("start_trace")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public TraceResponse startTrace(StartTraceRequest startRequest) throws Exception {
    log.info("http:start_trace request: {}", mapper.writeValueAsString(startRequest));
    // TODO should be starting new root span
    String baggage = startRequest.getBaggage();
    Span span = tracer.activeSpan();
    span.setBaggageItem(Constants.BAGGAGE_KEY, baggage);
    if (startRequest.isSampled()) {
      Tags.SAMPLING_PRIORITY.set(span, 1);
    }
    TraceResponse response = behavior.prepareResponse(startRequest.getDownstream());
    log.info("http:start_trace response: {}", mapper.writeValueAsString(response));
    return response;
  }

  @POST
  @Path("join_trace")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public TraceResponse joinTrace(JoinTraceRequest joinRequest) throws Exception {
    log.info("http:join_trace request: {}", mapper.writeValueAsString(joinRequest));
    TraceResponse response = behavior.prepareResponse(joinRequest.getDownstream());
    log.info("http:join_trace response: {}", mapper.writeValueAsString(response));
    return response;
  }
}
