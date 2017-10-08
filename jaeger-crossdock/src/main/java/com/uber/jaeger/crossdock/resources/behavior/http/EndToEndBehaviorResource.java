/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.crossdock.api.CreateTracesRequest;
import com.uber.jaeger.crossdock.resources.behavior.EndToEndBehavior;
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
 * json: {
 *   "type": "remote"
 *   "operation": "operationName",
 *   "count": 2,
 *   "tags": {
 *     "key": "value"
 *   }
 * }
 *
 * Given the above json payload, the resource will use a tracer with the RemoteControlledSampler
 * to create 2 traces for the "operationName" operation with the tags: {"key":"value"}. These
 * traces are reported to the agent with the hostname "test_driver".
 */
@Path("")
@Provider
@Slf4j
public class EndToEndBehaviorResource {
  private final ObjectMapper mapper = new ObjectMapper();
  private final EndToEndBehavior behavior;

  public EndToEndBehaviorResource(EndToEndBehavior endToEndBehavior) {
    this.behavior = endToEndBehavior;
  }

  @POST
  @Path("create_traces")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createTraces(CreateTracesRequest request) throws Exception {
    log.info("http:create_traces request: {}", mapper.writeValueAsString(request));
    behavior.generateTraces(request);
    return Response.ok("OK").build();
  }
}
