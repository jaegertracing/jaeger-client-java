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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles creating traces from a http request.
 *
 * json: {
 *   "operation": "operationName",
 *   "count": 2,
 *   "tags": {
 *     "key": "value"
 *   }
 * }
 *
 * Given the above json payload, the resource will create 2 traces for the "operationName"
 * operation with the tags: {"key":"value"}. These traces are reported to the agent with
 * the hostname "test_driver".
 */
@Path("")
@Provider
@Slf4j
public class EndToEndBehaviorResource {
  private static final Logger logger = LoggerFactory.getLogger(TraceBehaviorResource.class);

  private final ObjectMapper mapper = new ObjectMapper();
  private final EndToEndBehavior behavior;

  public EndToEndBehaviorResource() {
    this.behavior = new EndToEndBehavior(null);
  }

  public EndToEndBehaviorResource(io.opentracing.Tracer tracer) {
    this.behavior = new EndToEndBehavior(tracer);
  }

  @POST
  @Path("create_traces")
  @Consumes(MediaType.APPLICATION_JSON)
  public void createTraces(CreateTracesRequest request) throws Exception {
    logger.info("http:create_traces request: {}", mapper.writeValueAsString(request));
    behavior.GenerateTraces(request);
  }
}
