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
