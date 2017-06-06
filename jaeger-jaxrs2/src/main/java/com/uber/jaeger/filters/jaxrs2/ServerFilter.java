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

package com.uber.jaeger.filters.jaxrs2;

import com.uber.jaeger.context.TraceContext;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class ServerFilter implements ContainerRequestFilter, ContainerResponseFilter {
  private final Tracer tracer;
  private final TraceContext traceContext;

  @Context
  protected ResourceInfo resourceInfo;

  public ServerFilter(Tracer tracer, TraceContext traceContext) {
    this.tracer = tracer;
    this.traceContext = traceContext;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    //TODO: Add a @DisableTracing annotation and use the resourceInfo to check if it's applied
    try {
      Tracer.SpanBuilder builder =
          tracer
              .buildSpan(getOperationName(containerRequestContext))
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              //TODO(oibe) figure out how to get remote addr from grizzly HttpServletRequest
              .withTag(
                  Tags.PEER_HOSTNAME.getKey(),
                  containerRequestContext.getUriInfo().getBaseUri().getHost())
              .withTag(
                  Tags.HTTP_URL.getKey(),
                  containerRequestContext.getUriInfo().getAbsolutePath().toString());

      // TODO(oibe) make X_UBER_SOURCE configurable for open source projects
      MultivaluedMap<String, String> headers = containerRequestContext.getHeaders();
      if (headers.containsKey(com.uber.jaeger.Constants.X_UBER_SOURCE)) {
        builder =
            builder.withTag(
                Tags.PEER_SERVICE.getKey(),
                headers.getFirst(com.uber.jaeger.Constants.X_UBER_SOURCE));
      }

      ServerRequestCarrier carrier = new ServerRequestCarrier(containerRequestContext);
      SpanContext spanContext = tracer.extract(Format.Builtin.HTTP_HEADERS, carrier);
      if (spanContext != null) {
        builder = builder.asChildOf(spanContext);
      }
      Span serverSpan = builder.startManual();

      traceContext.push(serverSpan);
    } catch (Exception e) {
      log.error("Server Filter Request:", e);
    }
  }

  @Override
  public void filter(
      ContainerRequestContext containerRequestContext,
      ContainerResponseContext containerResponseContext)
      throws IOException {
    try {
      Span serverSpan;
      if (traceContext.isEmpty()) {
        // hitting this case means previous filter was not called
        return;
      }
      serverSpan = traceContext.pop();

      Tags.HTTP_STATUS.set(serverSpan, containerResponseContext.getStatus());
      serverSpan.finish();
    } catch (Exception e) {
      log.error("Server Filter Response:", e);
    }
  }

  /**
   * An operation name to use for the span being reported. Uses the http method name by default.
   * @param containerRequestContext A class that holds request specific information
   * @return the operation name
   */
  protected String getOperationName(ContainerRequestContext containerRequestContext) {
    return containerRequestContext.getMethod();
  }
}
