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
package com.uber.jaeger.crossdock.tracetest_manual;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.uber.jaeger.crossdock.deserializers.TraceResponseDeserializer;
import com.uber.jaeger.crossdock.serializers.TraceResponseSerializer;

@JsonSerialize(using = TraceResponseSerializer.class)
@JsonDeserialize(using = TraceResponseDeserializer.class)
public class TraceResponse {
    private String notImplementedError = "";
    private ObservedSpan span;
    private TraceResponse downstream;

    public TraceResponse(ObservedSpan span) {
        this.span = span;
    }

    @JsonCreator
    public TraceResponse(@JsonProperty("notImplementedError") String notImplementedError,
                         @JsonProperty("span") ObservedSpan span,
                         @JsonProperty("downstream") TraceResponse downstream) {
        this.notImplementedError = notImplementedError;
        this.span = span;
        this.downstream = downstream;
    }

    public TraceResponse(String notImplementedError) {
        this.notImplementedError = notImplementedError;
    }

    public TraceResponse setDownstream(TraceResponse downstream) {
        this.downstream = downstream;
        return this;
    }

    public String getNotImplementedError() {
        return notImplementedError;
    }

    public ObservedSpan getObservedSpan() {
        return span;
    }

    public TraceResponse getDownstream() {
        return downstream;
    }
}