/*
 * Copyright (c) 2017, Uber Technologies, Inc
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
import com.uber.jaeger.crossdock.api.CreateTracesRequest;
import com.uber.jaeger.crossdock.resources.behavior.EndToEndBehavior;
import io.opentracing.Tracer;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles creating traces from a http request.
 *
 * <p>json: { "type": "remote" "operation": "operationName", "count": 2, "tags": { "key": "value" }
 * }
 *
 * <p>Given the above json payload, the resource will use a tracer with the RemoteControlledSampler
 * to create 2 traces for the "operationName" operation with the tags: {"key":"value"}. These traces
 * are reported to the agent with the hostname "test_driver".
 */
@Path("")
@Provider
@Slf4j
public class EndToEndBehaviorResource {
  private final ObjectMapper mapper = new ObjectMapper();
  private final EndToEndBehavior behavior;
  private String agentHost = "test_driver";

  public EndToEndBehaviorResource() {
    this.behavior = new EndToEndBehavior(agentHost);
  }

  public EndToEndBehaviorResource(String agentHost) {
    this.behavior = new EndToEndBehavior(agentHost);
  }

  public EndToEndBehaviorResource(Map<String, Tracer> tracers) {
    this.behavior = new EndToEndBehavior(agentHost, tracers);
  }

  @POST
  @Path("create_traces")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createTraces(CreateTracesRequest request) throws Exception {
    log.info("http:create_traces request: {}", mapper.writeValueAsString(request));
    behavior.GenerateTraces(request);
    return Response.ok("OK").build();
  }
}
