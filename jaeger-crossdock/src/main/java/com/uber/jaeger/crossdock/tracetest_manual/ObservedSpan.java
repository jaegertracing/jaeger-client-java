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
import com.uber.jaeger.crossdock.deserializers.ObservedSpanDeserializer;
import com.uber.jaeger.crossdock.serializers.ObservedSpanSerializer;

@JsonSerialize(using = ObservedSpanSerializer.class)
@JsonDeserialize(using = ObservedSpanDeserializer.class)
public class ObservedSpan {
    private String traceID;
    private boolean sampled;
    private String baggage;

    @JsonCreator
    public ObservedSpan(@JsonProperty("traceId") String traceID,
                        @JsonProperty("sampled") boolean sampled,
                        @JsonProperty("baggage") String baggage) {
        this.traceID = traceID;
        this.sampled = sampled;
        this.baggage = baggage;
    }

    public String getTraceID() {
        return traceID;
    }

    public boolean getSampled() {
        return sampled;
    }

    public String getBaggage() {
        return baggage;
    }

    static ObservedSpan fromThrift(com.uber.jaeger.crossdock.thrift.ObservedSpan span) {
        return new ObservedSpan(
                span.getTraceId(),
                span.isSampled(),
                span.getBaggage()
        );
    }

    @Override
    public String toString() {
        return "ObservedSpan{" +
                "traceID='" + traceID + '\'' +
                ", sampled=" + sampled +
                ", baggage='" + baggage + '\'' +
                '}';
    }
}