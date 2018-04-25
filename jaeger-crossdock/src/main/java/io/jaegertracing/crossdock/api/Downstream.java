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

package io.jaegertracing.crossdock.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jaegertracing.crossdock.Constants;
import io.jaegertracing.crossdock.thrift.Transport;
import lombok.Getter;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
@Getter
public class Downstream {
  private String serviceName;
  private String host;
  private String port;
  private String transport;
  private String serverRole;
  private Downstream downstream;

  @JsonCreator
  public Downstream(
      @JsonProperty("serviceName") String serviceName,
      @JsonProperty("host") String host,
      @JsonProperty("port") String port,
      @JsonProperty("transport") String transport,
      @JsonProperty("serverRole") String serverRole,
      @JsonProperty("downstream") Downstream downstream) {
    this.serviceName = serviceName;
    this.host = host;
    this.port = port;
    this.transport = transport;
    this.serverRole = serverRole;
    this.downstream = downstream;
  }

  public static Downstream fromThrift(io.jaegertracing.crossdock.thrift.Downstream downstream) {
    if (downstream == null) {
      return null;
    }
    return new Downstream(
        downstream.getServiceName(),
        downstream.getHost(),
        downstream.getPort(),
        fromThrift(downstream.getTransport()),
        downstream.getServerRole(),
        fromThrift(downstream.getDownstream()));
  }

  private static String fromThrift(Transport transport) {
    switch (transport) {
      case HTTP:
        return Constants.TRANSPORT_HTTP;
      default:
        throw new IllegalArgumentException("Unknown transport " + transport);
    }
  }

  public static io.jaegertracing.crossdock.thrift.Downstream toThrift(Downstream downstream) {
    if (downstream == null) {
      return null;
    }
    return new io.jaegertracing.crossdock.thrift.Downstream(
        downstream.getServiceName(),
        downstream.getServerRole(),
        downstream.getHost(),
        downstream.getPort(),
        toThrift(downstream.getTransport()));
  }

  private static Transport toThrift(String transport) {
    if (Constants.TRANSPORT_HTTP.equals(transport)) {
      return Transport.HTTP;
    }
    throw new IllegalArgumentException("Unknown transport " + transport);
  }
}
