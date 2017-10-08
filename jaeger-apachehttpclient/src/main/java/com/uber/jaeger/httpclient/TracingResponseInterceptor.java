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
