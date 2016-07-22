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
package com.uber.jaeger.propagation;

import com.uber.jaeger.Constants;
import com.uber.jaeger.Injector;
import io.opentracing.Span;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedMap;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public class JerseyClientRequestInjector implements Injector<ClientRequestContext> {
    @Override
    public void inject(Span span, ClientRequestContext carrier) {
        MultivaluedMap<String, Object> headers = carrier.getHeaders();
        com.uber.jaeger.Span jaegerSpan = (com.uber.jaeger.Span) span;
        try {
            headers.putSingle(Constants.TRACER_STATE_HEADER_NAME, URLEncoder.encode(jaegerSpan.getContext().contextAsString(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        /* TODO(oibe) not ready for opensource.
          this whole thing needs refactoring. The "text map" codec must be encapsulated in the core module, and the header
           constants should not be exposed to framework-specific handlers.
         */
        Map<String, String> baggage = jaegerSpan.getBaggage();
        if (baggage != null) {
            for (String keyWithoutPrefix : baggage.keySet()) {
                String value = jaegerSpan.getBaggageItem(keyWithoutPrefix);
                String keyWithPrefix = Constants.TRACER_BAGGAGE_HEADER_PREFIX + keyWithoutPrefix;
                headers.add(keyWithPrefix, value);
            }
        }
    }
}