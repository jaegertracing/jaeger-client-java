/*
 * Copyright (c) 2017, Uber Technologies, Inc
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
package com.uber.jaeger.senders;

import com.uber.jaeger.thriftjava.Process;
import com.uber.jaeger.thriftjava.Span;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import org.apache.thrift.TException;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

/**
 * This class tests that HttpSender can be configured to make requests
 * and that exceptions are handled correctly.
 * See  {@link com.uber.jaeger.crossdock.JerseyServer} for integration tests.
 */
public class HttpSenderTest extends JerseyTest {

  @Override
  protected Application configure() {
    return new ResourceConfig(TraceAccepter.class);
  }

  @Test
  public void sendHappy() throws Exception {
    HttpSender sender = new HttpSender(target("/api/traces").getUri().toString());
    sender.send(new Process("robotrock"), generateSpans());
  }

  @Test(expected = TException.class)
  public void sendServerError() throws Exception {
    HttpSender sender = new HttpSender(target("/api/tracesErr").getUri().toString());
    sender.send(new Process("robotrock"), generateSpans());
  }

  @Test(expected = IllegalArgumentException.class)
  public void misconfiguredUrl() throws Exception {
    new HttpSender("misconfiguredUrl");
  }

  @Test(expected = TException.class)
  public void serverDoesntExist() throws Exception {
    HttpSender sender = new HttpSender("http://some-server/api/traces");
    sender.send(new Process("robotrock"), generateSpans());
  }

  private List<Span> generateSpans() {
    ArrayList<Span> spans = new ArrayList<>();
    Span span = new Span();
    span.setOperationName("boomerang");
    spans.add(span);
    return spans;
  }

  @Path("api")
  public static class TraceAccepter {

    @Path("traces")
    @POST()
    public void postHappy(@QueryParam("format") String format, String data) {
    }

    @Path("tracesErr")
    @POST()
    public Response postErr(@QueryParam("format") String format, String data) {
      return Response.serverError().build();
    }
  }

}
