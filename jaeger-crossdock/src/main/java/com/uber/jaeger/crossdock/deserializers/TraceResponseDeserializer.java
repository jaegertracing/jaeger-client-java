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
package com.uber.jaeger.crossdock.deserializers;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.crossdock.tracetest_manual.ObservedSpan;
import com.uber.jaeger.crossdock.tracetest_manual.TraceResponse;

public class TraceResponseDeserializer extends JsonDeserializer<TraceResponse> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public TraceResponse deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);

        System.out.println("Trace Response: " + node);

        String notImplementedError = node.get("notImplementedError").asText();

        ObservedSpan span = null;
        if (notImplementedError.equals("")) {
            span = mapper.reader(ObservedSpan.class).readValue(node.get("span"));
        }

        TraceResponse downstream = null;
        JsonNode downstreamNode = node.get("downstream");
        if (downstreamNode != null) {
            downstream = mapper.reader(TraceResponse.class).readValue(downstreamNode);
        }

        return new TraceResponse(notImplementedError, span, downstream);
    }
}
