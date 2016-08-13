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

import java.util.HashMap;
import java.util.Map;

import static com.uber.jaeger.Constants.TRACER_BAGGAGE_HEADER_PREFIX;
import static com.uber.jaeger.Constants.TRACER_STATE_HEADER_NAME;
import static com.uber.jaeger.propagation.PrefixedKeys.prefixedKey;
import static com.uber.jaeger.propagation.PrefixedKeys.unprefixedKey;

public class TextMapCodec implements Injector<TextMap>, Extractor<TextMap> {
    @Override
    public void inject(SpanContext spanContext, TextMap carrier) {
        carrier.put(TRACER_STATE_HEADER_NAME, spanContext.contextAsString());
        for (Map.Entry<String, String> entry: spanContext.baggageItems()) {
            carrier.put(prefixedKey(entry.getKey(), TRACER_BAGGAGE_HEADER_PREFIX), entry.getValue());
        }
    }

    @Override
    public SpanContext extract(TextMap carrier) {
        SpanContext context = null;
        Map<String, String> baggage = null;
        for (Map.Entry<String, String> entry : carrier) {
            // TODO there should be no lower-case here
            String key = entry.getKey().toLowerCase();
            if (key.equals(TRACER_STATE_HEADER_NAME)) {
                context = SpanContext.contextFromString(entry.getValue());
            } else if (key.startsWith(TRACER_BAGGAGE_HEADER_PREFIX)) {
                if (baggage == null) {
                    baggage = new HashMap<>();
                }
                baggage.put(unprefixedKey(key, TRACER_BAGGAGE_HEADER_PREFIX), entry.getValue());
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
}
