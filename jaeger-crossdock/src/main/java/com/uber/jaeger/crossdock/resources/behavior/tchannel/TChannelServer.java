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

import com.uber.jaeger.context.TracingUtils;
import com.uber.jaeger.crossdock.resources.behavior.TraceBehavior;
import com.uber.tchannel.api.TChannel;
import com.uber.tchannel.tracing.TracingContext;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.EmptyStackException;

public class TChannelServer {
  // TODO should not be static
  public static TChannel server;

  public TChannelServer(TChannel.Builder tchannelBuilder, TraceBehavior behavior, Tracer tracer) {
    server =
        tchannelBuilder
            .setTracer(tracer)
            .setTracingContext(new TracingContextAdapter())
            .build();

    server
        .makeSubChannel(server.getServiceName())
        .registerHealthHandler()
        .register("TracedService::joinTrace", new JoinTraceThriftHandler(behavior));
  }

  public TChannel getChannel() {
    return server;
  }

  public void start() throws InterruptedException {
    // listen for incoming connections
    server.listen().channel().closeFuture();
  }

  public void shutdown() {
    server.shutdown(true);
  }

  private static class TracingContextAdapter implements TracingContext {
    @Override
    public void pushSpan(Span span) {
      TracingUtils.getTraceContext().push(span);
    }

    @Override
    public boolean hasSpan() {
      return !TracingUtils.getTraceContext().isEmpty();
    }

    @Override
    public Span currentSpan() throws EmptyStackException {
      return TracingUtils.getTraceContext().getCurrentSpan();
    }

    @Override
    public Span popSpan() throws EmptyStackException {
      return TracingUtils.getTraceContext().pop();
    }

    @Override
    public void clear() {}
  }
}
