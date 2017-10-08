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
package com.uber.jaeger.crossdock.resources.behavior.tchannel;

import com.uber.jaeger.crossdock.api.Downstream;
import com.uber.jaeger.crossdock.api.TraceResponse;
import com.uber.jaeger.crossdock.resources.behavior.TraceBehavior;
import com.uber.jaeger.crossdock.thrift.JoinTraceRequest;
import com.uber.jaeger.crossdock.thrift.TracedService;
import com.uber.tchannel.api.handlers.ThriftRequestHandler;
import com.uber.tchannel.messages.ThriftRequest;
import com.uber.tchannel.messages.ThriftResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class JoinTraceThriftHandler
    extends ThriftRequestHandler<TracedService.joinTrace_args, TracedService.joinTrace_result> {
  private final TraceBehavior behavior;

  JoinTraceThriftHandler(TraceBehavior behavior) {
    this.behavior = behavior;
  }

  @Override
  public ThriftResponse<TracedService.joinTrace_result> handleImpl(
      ThriftRequest<TracedService.joinTrace_args> thriftRequest) {
    JoinTraceRequest request =
        thriftRequest.getBody(TracedService.joinTrace_args.class).getRequest();
    log.info("thrift:join_trace request: {}", request);
    TraceResponse response;
    try {
      response = behavior.prepareResponse(Downstream.fromThrift(request.getDownstream()));
    } catch (Exception e) {
      log.error("Failed to call downstream", e);
      response = new TraceResponse(e.getMessage());
    }
    log.info("thrift:join_trace response: {}", response);
    return new ThriftResponse.Builder<TracedService.joinTrace_result>(thriftRequest)
        .setBody(new TracedService.joinTrace_result(TraceResponse.toThrift(response)))
        .build();
  }
}
