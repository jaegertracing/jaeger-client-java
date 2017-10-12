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
      serverSpan = traceContext.getCurrentSpan();

      Tags.HTTP_STATUS.set(serverSpan, containerResponseContext.getStatus());

      // We are relying on the ActiveSpanSource implementation to `close` the span, and
      // hence don't need to call `finish`.
      traceContext.pop();
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
