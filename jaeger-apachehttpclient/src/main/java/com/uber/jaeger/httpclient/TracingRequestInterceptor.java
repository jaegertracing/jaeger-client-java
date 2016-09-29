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
package com.uber.jaeger.httpclient;

import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.context.TracingUtils;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.RequestLine;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Apache http client request interceptor This is designed to be used along with
 * {@link TracingResponseInterceptor} to report tracing information.
 *
 * In most cases you shouldn't be using this directly. Use the appropriate client builder from
 * {@link TracingInterceptors}
 *
 */
public class TracingRequestInterceptor implements HttpRequestInterceptor {
  private final Tracer tracer;
  private final Logger logger = LoggerFactory.getLogger(TracingRequestInterceptor.class);

  public TracingRequestInterceptor(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public void process(HttpRequest httpRequest, HttpContext httpContext)
      throws HttpException, IOException {
    try {
      TraceContext parentContext = TracingUtils.getTraceContext();

      RequestLine requestLine = httpRequest.getRequestLine();
      Tracer.SpanBuilder clientSpanBuilder = tracer.buildSpan(getOperationName(httpRequest));
      if (!parentContext.isEmpty()) {
        clientSpanBuilder.asChildOf(parentContext.getCurrentSpan());
      }

      Span clientSpan = clientSpanBuilder.start();
      Tags.SPAN_KIND.set(clientSpan, Tags.SPAN_KIND_CLIENT);
      Tags.HTTP_URL.set(clientSpan, requestLine.getUri());

      if (httpContext instanceof HttpClientContext) {
        HttpHost host = ((HttpClientContext) httpContext).getTargetHost();
        Tags.PEER_HOSTNAME.set(clientSpan, host.getHostName());
        Tags.PEER_PORT.set(clientSpan, (short) host.getPort());
      }

      onSpanStart(clientSpan, httpRequest);

      tracer.inject(
          clientSpan.context(), Format.Builtin.HTTP_HEADERS, new ClientRequestCarrier(httpRequest));

      httpContext.setAttribute(Constants.CURRENT_SPAN_CONTEXT_KEY, clientSpan);
    } catch (Exception e) {
      logger.error("Could not start client tracing span.", e);
    }
  }

  protected void onSpanStart(Span clientSpan, HttpRequest httpRequest) {

  }

  protected String getOperationName(HttpRequest httpRequest) {
    return httpRequest.getRequestLine().getMethod();
  }


}
