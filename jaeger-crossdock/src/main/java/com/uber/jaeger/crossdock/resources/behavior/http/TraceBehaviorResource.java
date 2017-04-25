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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.Span;
import com.uber.jaeger.context.TracingUtils;
import com.uber.jaeger.crossdock.Constants;
import com.uber.jaeger.crossdock.api.JoinTraceRequest;
import com.uber.jaeger.crossdock.api.StartTraceRequest;
import com.uber.jaeger.crossdock.api.TraceResponse;
import com.uber.jaeger.crossdock.resources.behavior.TraceBehavior;
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

  public TraceBehaviorResource() {
    this.behavior = new TraceBehavior();
  }

  @POST
  @Path("start_trace")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public TraceResponse startTrace(StartTraceRequest startRequest) throws Exception {
    log.info("http:start_trace request: {}", mapper.writeValueAsString(startRequest));
    // TODO should be starting new root span
    Span span = (Span) TracingUtils.getTraceContext().getCurrentSpan();
    String baggage = startRequest.getBaggage();
    span.setBaggageItem(Constants.BAGGAGE_KEY, baggage);
    if (startRequest.isSampled()) {
      Tags.SAMPLING_PRIORITY.set(span, (short) 1);
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
