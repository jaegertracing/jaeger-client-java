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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.filters.jaxrs2.ClientFilter;
import io.opentracing.Tracer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.jackson.JacksonFeature;

@Path("jersey")
public class JerseyHandler {
  @Inject Tracer tracer;

  private Client client;
  private ObjectMapper mapper = new ObjectMapper();

  private Client getClient() {
    if (client == null) {
      client =
          ClientBuilder.newClient()
              .register(new ClientFilter(tracer))
              .register(JacksonFeature.class);
    }
    return client;
  }

  private CallTreeNode makeRequest(String endpoint) throws IOException {
    client = getClient();

    WebTarget target = client.target(JerseyServer.BASE_URI).path(endpoint);
    Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON_TYPE);

    Response resp = builder.get();
    String callTreeNodeAsString = resp.readEntity(String.class);
    return mapper.readValue(callTreeNodeAsString, CallTreeNode.class);
  }

  @GET
  @Path("hop1")
  @Produces(MediaType.APPLICATION_JSON)
  public CallTreeNode clientEndpoint(@Context UriInfo uriInfo) throws IOException {
    final CallTreeNode endpointOneResponse = makeRequest("jersey/hop2");
    final CallTreeNode endpointTwoResponse = makeRequest("jersey/hop3");

    List<CallTreeNode> callTreeNodeList =
        new ArrayList<CallTreeNode>() {
          {
            add(endpointOneResponse);
            add(endpointTwoResponse);
          }
        };

    SpanInfo spanInfo = new SpanInfo((com.uber.jaeger.Span) tracer.activeSpan());
    return new CallTreeNode(uriInfo.getPath(), spanInfo, callTreeNodeList);
  }

  @GET
  @Path("hop2")
  @Produces(MediaType.APPLICATION_JSON)
  public CallTreeNode endpointOne(@Context UriInfo uriInfo) throws IOException {
    SpanInfo spanInfo = new SpanInfo((com.uber.jaeger.Span) tracer.activeSpan());
    return new CallTreeNode(uriInfo.getPath(), spanInfo);
  }

  @GET
  @Path("hop3")
  @Produces(MediaType.APPLICATION_JSON)
  public CallTreeNode endpointTwo(@Context UriInfo uriInfo) {
    // TODO(oibe) turn this into a tchannel server
    SpanInfo spanInfo = new SpanInfo((com.uber.jaeger.Span) tracer.activeSpan());
    return new CallTreeNode(uriInfo.getPath(), spanInfo);
  }

  @GET
  @Path("exception")
  @Produces(MediaType.APPLICATION_JSON)
  public CallTreeNode exception() {
    throw new RuntimeException("TestExceptionMapper");
  }
}
