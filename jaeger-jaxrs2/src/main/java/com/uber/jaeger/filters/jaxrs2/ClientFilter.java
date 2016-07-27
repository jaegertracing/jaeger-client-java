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
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.StatsFactory;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@ConstrainedTo(RuntimeType.CLIENT)
public class ClientFilter implements ClientRequestFilter, ClientResponseFilter {
    private final Tracer tracer;
    private final TraceContext traceContext;
    private final Metrics metrics;

    public ClientFilter(Tracer tracer, TraceContext traceContext, StatsFactory statsFactory) {
        this.tracer = tracer;
        this.traceContext = traceContext;
        this.metrics = new Metrics(statsFactory);
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext) throws IOException {
        try {
            Tracer.SpanBuilder clientSpanBuilder = tracer.buildSpan(clientRequestContext.getMethod());
            if (!traceContext.isEmpty()) {
                clientSpanBuilder.withParent(traceContext.getCurrentSpan());
            }
            Span clientSpan = clientSpanBuilder.start();

            Tags.SPAN_KIND.set(clientSpan, Tags.SPAN_KIND_CLIENT);
            Tags.HTTP_URL.set(clientSpan, clientRequestContext.getUri().toString());
            Tags.PEER_HOSTNAME.set(clientSpan, clientRequestContext.getUri().getHost());

            clientRequestContext.setProperty(Constants.CURRENT_SPAN_CONTEXT_KEY, clientSpan);
            tracer.inject(clientSpan, clientRequestContext);
        } catch (Exception e) {
            metrics.clientFilterExceptionRequest.inc(1);
            // TODO(oibe) add a real logger
            e.printStackTrace();
        }
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) throws IOException {
        try {
            Span clientSpan = (io.opentracing.Span) clientRequestContext.getProperty(Constants.CURRENT_SPAN_CONTEXT_KEY);
            if (clientSpan != null) {
                Tags.HTTP_STATUS.set(clientSpan, clientResponseContext.getStatus());
                clientSpan.finish();
            }
        } catch (Exception e) {
            metrics.clientFilterExceptionResponse.inc(1);
            // TODO(oibe) add a real logger
            e.printStackTrace();
        }
    }
}
