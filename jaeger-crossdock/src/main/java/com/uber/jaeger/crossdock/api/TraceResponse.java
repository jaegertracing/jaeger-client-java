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
public class TraceResponse {
  private String notImplementedError = "";
  private ObservedSpan span;
  private TraceResponse downstream;

  public TraceResponse(ObservedSpan span) {
    this.span = span;
  }

  @JsonCreator
  public TraceResponse(
      @JsonProperty("notImplementedError") String notImplementedError,
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

  public static com.uber.jaeger.crossdock.thrift.TraceResponse toThrift(TraceResponse resp) {
    if (resp == null) {
      return null;
    }
    com.uber.jaeger.crossdock.thrift.TraceResponse response =
        new com.uber.jaeger.crossdock.thrift.TraceResponse();
    response.setSpan(toThrift(resp.getSpan()));
    response.setDownstream(toThrift(resp.getDownstream()));
    response.setNotImplementedError(resp.getNotImplementedError());
    return response;
  }

  private static com.uber.jaeger.crossdock.thrift.ObservedSpan toThrift(ObservedSpan observedSpan) {
    com.uber.jaeger.crossdock.thrift.ObservedSpan res =
        new com.uber.jaeger.crossdock.thrift.ObservedSpan();
    res.setTraceId(observedSpan.getTraceId());
    res.setBaggage(observedSpan.getBaggage());
    res.setSampled(observedSpan.isSampled());
    return res;
  }

  public static TraceResponse fromThrift(com.uber.jaeger.crossdock.thrift.TraceResponse thrift) {
    if (thrift == null) {
      return null;
    }
    return new TraceResponse(
        thrift.getNotImplementedError(),
        ObservedSpan.fromThrift(thrift.getSpan()),
        TraceResponse.fromThrift(thrift.getDownstream()));
  }
}
