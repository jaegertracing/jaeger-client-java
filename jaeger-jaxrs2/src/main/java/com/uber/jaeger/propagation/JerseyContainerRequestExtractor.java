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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;

import com.uber.jaeger.Constants;
import com.uber.jaeger.TraceContext;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.exceptions.EmptyTracerStateStringException;
import com.uber.jaeger.exceptions.MalformedTracerStateStringException;
import com.uber.jaeger.Extractor;
import io.opentracing.Tracer.SpanBuilder;

public class JerseyContainerRequestExtractor implements Extractor<ContainerRequestContext> {
    private final Tracer tracer;
    public JerseyContainerRequestExtractor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public SpanBuilder join(ContainerRequestContext carrier) {
        String operationName = carrier.getMethod();
        MultivaluedMap<String, String> headers = carrier.getHeaders();
        String tracerStateHeader = headers.getFirst(Constants.TRACER_STATE_HEADER_NAME);

        if (tracerStateHeader != null) {
            try {
                tracerStateHeader = URLDecoder.decode(tracerStateHeader, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                tracerStateHeader = null;
            }
        }

        TraceContext parentContext;
        try {
            parentContext = TraceContext.contextFromString(tracerStateHeader);
        } catch (MalformedTracerStateStringException | EmptyTracerStateStringException e) {
            // right now a null parentContext will create a new context in ExtractedSpanBuilder
            return tracer.buildSpan(operationName);
        }

        // add baggage from headers into parent span
        HashMap<String, String> baggage = new HashMap<String, String>();
        for (String keyWithPrefix : headers.keySet()) {
            if (keyWithPrefix.toLowerCase().startsWith(Constants.TRACER_BAGGAGE_HEADER_PREFIX)) {
                String keyWithoutPrefix = keyWithPrefix.substring(Constants.TRACER_BAGGAGE_HEADER_PREFIX.length());
                String value = headers.getFirst(keyWithPrefix);
                baggage.put(keyWithoutPrefix, value);
            }
        }

        //TODO(oibe) rewrite using standard TEXTMAP carrier once it's implemented in OT.
        return tracer.getExtractedSpanBuilder(operationName, parentContext, baggage);
    }
}
