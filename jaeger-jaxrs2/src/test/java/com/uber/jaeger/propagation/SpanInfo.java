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

package com.uber.jaeger.propagation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;

class SpanInfo {
  @JsonProperty("trace_id")
  String traceId;

  @JsonProperty("baggage")
  String baggage;

  @JsonProperty("sampled")
  boolean sampled;

  @JsonCreator
  public SpanInfo(
      @JsonProperty("trace_id") String traceId,
      @JsonProperty("baggage") String baggage,
      @JsonProperty("sampled") boolean sampled) {
    this.traceId = traceId;
    this.baggage = baggage;
    this.sampled = sampled;
  }

  public SpanInfo(Span span) {
    SpanContext spanContext = span.context();
    traceId = Long.toHexString(spanContext.getTraceId());
    baggage = span.getBaggageItem(FilterIntegrationTest.BAGGAGE_KEY);
    sampled = spanContext.isSampled();
  }

  public String getTraceId() {
    return traceId;
  }

  public String getBaggage() {
    return baggage;
  }

  public boolean getSampled() {
    return sampled;
  }
}
