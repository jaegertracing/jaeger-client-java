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

import com.uber.jaeger.context.TracingUtils;
import com.uber.jaeger.crossdock.JerseyServer;
import com.uber.jaeger.crossdock.resources.behavior.TraceBehavior;
import com.uber.tchannel.api.TChannel;
import com.uber.tchannel.tracing.TracingContext;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.net.InetAddress;
import java.util.EmptyStackException;

public class TChannelServer {
  // TODO should not be static
  public static TChannel server;

  public TChannelServer(int port, TraceBehavior behavior, Tracer tracer, boolean useLoopback) {
    TChannel.Builder builder = new TChannel.Builder(JerseyServer.SERVICE_NAME);
    if (useLoopback) {
      builder.setServerHost(InetAddress.getLoopbackAddress());
    }
    server =
        builder
            .setServerPort(port)
            .setTracer(tracer)
            .setTracingContext(new TracingContextAdapter())
            .build();

    server
        .makeSubChannel(JerseyServer.SERVICE_NAME)
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
