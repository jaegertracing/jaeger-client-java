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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.uber.jaeger.crossdock.deserializers.DownstreamDeserializer;
import com.uber.jaeger.crossdock.serializers.DownstreamSerializer;

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
    public Downstream(@JsonProperty("serviceName") String serviceName,
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
}
