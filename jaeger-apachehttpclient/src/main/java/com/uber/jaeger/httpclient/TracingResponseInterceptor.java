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

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

/**
 * Apache http client response interceptor that works in conjunction with
 * {@link TracingRequestInterceptor} to report spans
 */
@Slf4j
public class TracingResponseInterceptor implements HttpResponseInterceptor {

  @Override
  public void process(HttpResponse httpResponse, HttpContext httpContext)
      throws HttpException, IOException {
    try {
      Span clientSpan = (Span) httpContext.getAttribute(Constants.CURRENT_SPAN_CONTEXT_KEY);
      if (clientSpan != null) {
        Tags.HTTP_STATUS.set(clientSpan, httpResponse.getStatusLine().getStatusCode());
        beforeSpanFinish(clientSpan, httpResponse, httpContext);
        clientSpan.finish();
      } else {
        log.warn(
            "The ResponseInterceptor did not find a clientSpan. "
                + "Verify that the RequestInterceptor is correctly set up.");
      }
    } catch (Exception e) {
      log.error("Could not finish client tracing span.", e);
    }
  }

  /**
   * beforeSpanFinish will be called just before the span is closed.
   * The subclasses can override this method to add additional information
   * to the span, including tags and logs.
   *
   * @param clientSpan the span that's going to be closed
   * @param httpResponse the http response returned from the http operation
   * @param httpContext the http context that the operation run on
   */
  protected void beforeSpanFinish(Span clientSpan, HttpResponse httpResponse, HttpContext httpContext) {
    // no-op, can be overriden by subclasses
  }
}
