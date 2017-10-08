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
package com.uber.jaeger.crossdock.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class ObservedSpan {
  private String traceId;
  private boolean sampled;
  private String baggage;

  @JsonCreator
  public ObservedSpan(
      @JsonProperty("traceId") String traceId,
      @JsonProperty("sampled") boolean sampled,
      @JsonProperty("baggage") String baggage) {
    this.traceId = traceId;
    this.sampled = sampled;
    this.baggage = baggage;
  }

  static ObservedSpan fromThrift(com.uber.jaeger.crossdock.thrift.ObservedSpan span) {
    return new ObservedSpan(span.getTraceId(), span.isSampled(), span.getBaggage());
  }
}
