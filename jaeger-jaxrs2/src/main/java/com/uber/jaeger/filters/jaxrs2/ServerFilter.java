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
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class ServerFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private final Tracer tracer;
    private final TraceContext traceContext;
    private final Logger logger = LoggerFactory.getLogger(ServerFilter.class);

    public ServerFilter(Tracer tracer, TraceContext traceContext) {
        this.tracer = tracer;
        this.traceContext = traceContext;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        try {
            Tracer.SpanBuilder spanBuilder = tracer.join(containerRequestContext);
            Span serverSpan = spanBuilder.withOperationName(containerRequestContext.getMethod()).start();

            Tags.SPAN_KIND.set(serverSpan, Tags.SPAN_KIND_SERVER);

            //TODO(oibe) figure out how to get remote addr from grizzly HttpServletRequest
            Tags.HTTP_URL.set(serverSpan, containerRequestContext.getUriInfo().toString());

            MultivaluedMap<String, String> headers = containerRequestContext.getHeaders();
            // TODO(oibe) make X_UBER_SOURCE configurable for open source projects
            if (headers.containsKey(com.uber.jaeger.Constants.X_UBER_SOURCE)) {
                Tags.PEER_SERVICE.set(serverSpan, headers.getFirst(com.uber.jaeger.Constants.X_UBER_SOURCE));
            }

            traceContext.push(serverSpan);
        } catch (Exception e) {
            logger.error("Server Filter Request:", e);
        }
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) throws IOException {
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
            logger.error("Server Filter Response:", e);
        }
    }
}
