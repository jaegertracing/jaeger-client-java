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

import com.uber.jaeger.SpanContext;
import io.opentracing.propagation.TextMap;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static com.uber.jaeger.Constants.TRACER_BAGGAGE_HEADER_PREFIX;
import static com.uber.jaeger.Constants.TRACER_STATE_HEADER_NAME;
import static com.uber.jaeger.propagation.PrefixedKeys.prefixedKey;
import static com.uber.jaeger.propagation.PrefixedKeys.unprefixedKey;

public class TextMapCodec implements Injector<TextMap>, Extractor<TextMap> {

    private final String contextKey = TRACER_STATE_HEADER_NAME;

    private final String baggagePrefix = TRACER_BAGGAGE_HEADER_PREFIX;

    private final boolean urlEncoding;

    public TextMapCodec(boolean urlEncoding) {
        this.urlEncoding = urlEncoding;
    }

    @Override
    public void inject(SpanContext spanContext, TextMap carrier) {
        carrier.put(contextKey, encodedValue(spanContext.contextAsString()));
        for (Map.Entry<String, String> entry: spanContext.baggageItems()) {
            carrier.put(
                    prefixedKey(entry.getKey(), baggagePrefix),
                    encodedValue(entry.getValue()));
        }
    }

    @Override
    public SpanContext extract(TextMap carrier) {
        SpanContext context = null;
        Map<String, String> baggage = null;
        for (Map.Entry<String, String> entry : carrier) {
            // TODO there should be no lower-case here
            String key = entry.getKey().toLowerCase();
            if (key.equals(contextKey)) {
                context = SpanContext.contextFromString(
                        decodedValue(entry.getValue()));
            } else if (key.startsWith(baggagePrefix)) {
                if (baggage == null) {
                    baggage = new HashMap<>();
                }
                baggage.put(
                        unprefixedKey(key, baggagePrefix),
                        decodedValue(entry.getValue()));
            }
        }
        if (context == null) {
            return null;
        }
        if (baggage == null) {
            return context;
        }
        return context.withBaggage(baggage);
    }

    private String encodedValue(String value) {
        if (!urlEncoding) {
            return value;
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // not much we can do, try raw value
            return value;
        }
    }

    private String decodedValue(String value) {
        if (!urlEncoding) {
            return value;
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // not much we can do, try raw value
            return value;
        }
    }

}
