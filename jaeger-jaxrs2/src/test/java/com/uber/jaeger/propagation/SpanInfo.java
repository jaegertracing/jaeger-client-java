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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;

class SpanInfo {
  @JsonProperty("trace_id")
  String traceID;

  @JsonProperty("baggage")
  String baggage;

  @JsonProperty("sampled")
  boolean sampled;

  @JsonCreator
  public SpanInfo(
      @JsonProperty("trace_id") String traceID,
      @JsonProperty("baggage") String baggage,
      @JsonProperty("sampled") boolean sampled) {
    this.traceID = traceID;
    this.baggage = baggage;
    this.sampled = sampled;
  }

  public SpanInfo(Span span) {
    SpanContext spanContext = span.context();
    traceID = Long.toHexString(spanContext.getTraceId());
    baggage = span.getBaggageItem(FilterIntegrationTest.BAGGAGE_KEY);
    sampled = spanContext.isSampled();
  }

  public String getTraceID() {
    return traceID;
  }

  public String getBaggage() {
    return baggage;
  }

  public boolean getSampled() {
    return sampled;
  }
}
