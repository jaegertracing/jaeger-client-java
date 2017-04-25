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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.context.TraceContext;
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
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;

@Path("jersey")
public class JerseyHandler {
  @Inject Tracer tracer;

  @Inject TraceContext traceContext;

  private Client client;
  private ObjectMapper mapper = new ObjectMapper();

  private Client getClient() {
    if (client == null) {
      client =
          ClientBuilder.newClient()
              .register(new ClientFilter(tracer, traceContext))
              .register(
                  new AbstractBinder() {
                    @Override
                    protected void configure() {
                      bind(traceContext).to(TraceContext.class);
                    }
                  })
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

    SpanInfo spanInfo = new SpanInfo((com.uber.jaeger.Span) traceContext.getCurrentSpan());
    return new CallTreeNode(uriInfo.getPath(), spanInfo, callTreeNodeList);
  }

  @GET
  @Path("hop2")
  @Produces(MediaType.APPLICATION_JSON)
  public CallTreeNode endpointOne(@Context UriInfo uriInfo) throws IOException {
    SpanInfo spanInfo = new SpanInfo((com.uber.jaeger.Span) traceContext.getCurrentSpan());
    return new CallTreeNode(uriInfo.getPath(), spanInfo);
  }

  @GET
  @Path("hop3")
  @Produces(MediaType.APPLICATION_JSON)
  public CallTreeNode endpointTwo(@Context UriInfo uriInfo) {
    // TODO(oibe) turn this into a tchannel server
    SpanInfo spanInfo = new SpanInfo((com.uber.jaeger.Span) traceContext.getCurrentSpan());
    return new CallTreeNode(uriInfo.getPath(), spanInfo);
  }

  @GET
  @Path("exception")
  @Produces(MediaType.APPLICATION_JSON)
  public CallTreeNode exception() {
    throw new RuntimeException("TestExceptionMapper");
  }
}
