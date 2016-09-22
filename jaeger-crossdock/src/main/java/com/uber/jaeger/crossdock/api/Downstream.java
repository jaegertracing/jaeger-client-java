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
package com.uber.jaeger.crossdock.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.uber.jaeger.crossdock.Constants;
import com.uber.jaeger.crossdock.deserializers.DownstreamDeserializer;
import com.uber.jaeger.crossdock.serializers.DownstreamSerializer;
import com.uber.jaeger.crossdock.thrift.Transport;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSerialize(using = DownstreamSerializer.class)
@JsonDeserialize(using = DownstreamDeserializer.class)
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

  public String getServiceName() {
    return serviceName;
  }

  public String getHost() {
    return host;
  }

  public String getPort() {
    return port;
  }

  public String getTransport() {
    return transport;
  }

  public String getServerRole() {
    return serverRole;
  }

  public Downstream getDownstream() {
    return downstream;
  }

  @Override
  public String toString() {
    return "Downstream{"
        + "serviceName='"
        + serviceName
        + '\''
        + ", host='"
        + host
        + '\''
        + ", port='"
        + port
        + '\''
        + ", transport='"
        + transport
        + '\''
        + ", serverRole='"
        + serverRole
        + '\''
        + ", downstream="
        + downstream
        + '}';
  }

  public static Downstream fromThrift(com.uber.jaeger.crossdock.thrift.Downstream downstream) {
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
      case TCHANNEL:
        return Constants.TRANSPORT_TCHANNEL;
      default:
        throw new IllegalArgumentException("Unknown transport " + transport);
    }
  }

  public static com.uber.jaeger.crossdock.thrift.Downstream toThrift(Downstream downstream) {
    if (downstream == null) {
      return null;
    }
    return new com.uber.jaeger.crossdock.thrift.Downstream(
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
    if (Constants.TRANSPORT_TCHANNEL.equals(transport)) {
      return Transport.TCHANNEL;
    }
    throw new IllegalArgumentException("Unknown transport " + transport);
  }
}
