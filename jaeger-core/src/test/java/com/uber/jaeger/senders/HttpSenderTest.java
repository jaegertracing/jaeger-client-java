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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.thriftjava.Process;
import com.uber.jaeger.thriftjava.Span;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import okhttp3.OkHttpClient;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Before;
import org.junit.Test;

/**
 * This class tests that HttpSender can be configured to make requests
 * and that exceptions are handled correctly.
 * See  {@link com.uber.jaeger.crossdock.JerseyServer} for integration tests.
 */
public class HttpSenderTest extends JerseyTest {

  @Before
  public void reset() {
    System.clearProperty(Configuration.JAEGER_AGENT_HOST);
    System.clearProperty(Configuration.JAEGER_AGENT_PORT);
    System.clearProperty(Configuration.JAEGER_ENDPOINT);
    System.clearProperty(Configuration.JAEGER_AUTH_TOKEN);
    System.clearProperty(Configuration.JAEGER_USER);
    System.clearProperty(Configuration.JAEGER_PASSWORD);
  }

  @Override
  protected Application configure() {
    return new ResourceConfig().register(new TraceAccepter());
  }

  @Test
  public void sendHappy() throws Exception {
    new HttpSender(target("/api/traces").getUri().toString())
        .send(new Process("robotrock"), generateSpans());
    new HttpSender(target("/api/traces").getUri().toString(), 6500)
        .send(new Process("name"), generateSpans());
    new HttpSender(target("/api/traces").getUri().toString(), 6500, new OkHttpClient())
        .send(new Process("name"), generateSpans());
  }

  @Test(expected = Exception.class)
  public void sendServerError() throws Exception {
    HttpSender sender = new HttpSender(target("/api/tracesErr").getUri().toString());
    sender.send(new Process("robotrock"), generateSpans());
  }

  @Test(expected = IllegalArgumentException.class)
  public void misconfiguredUrl() throws Exception {
    new HttpSender("misconfiguredUrl");
  }

  @Test(expected = Exception.class)
  public void serverDoesntExist() throws Exception {
    HttpSender sender = new HttpSender("http://some-server/api/traces");
    sender.send(new Process("robotrock"), generateSpans());
  }

  @Test(expected = SenderException.class)
  public void senderFail() throws Exception {
    HttpSender sender = new HttpSender("http://some-server/api/traces");
    sender.send(null, generateSpans());
  }

  @Test
  public void sendWithoutAuthData() throws Exception {
    System.setProperty(Configuration.JAEGER_ENDPOINT, target("/api/traces").getUri().toString());

    HttpSender sender = (HttpSender) Configuration.SenderConfiguration.fromEnv().getSender();
    sender.send(new Process("robotrock"), generateSpans());
  }

  @Test
  public void sendWithBasicAuth() throws Exception {
    System.setProperty(Configuration.JAEGER_ENDPOINT, target("/api/basic-auth").getUri().toString());
    System.setProperty(Configuration.JAEGER_USER, "jdoe");
    System.setProperty(Configuration.JAEGER_PASSWORD, "password");

    HttpSender sender = (HttpSender) Configuration.SenderConfiguration.fromEnv().getSender();
    sender.send(new Process("robotrock"), generateSpans());
  }

  @Test
  public void sendWithTokenAuth() throws Exception {
    System.setProperty(Configuration.JAEGER_ENDPOINT, target("/api/bearer").getUri().toString());
    System.setProperty(Configuration.JAEGER_AUTH_TOKEN, "thetoken");

    HttpSender sender = (HttpSender) Configuration.SenderConfiguration.fromEnv().getSender();
    sender.send(new Process("robotrock"), generateSpans());
  }

  @Test
  public void sanityTestForTokenAuthTest() throws Exception {
    System.setProperty(Configuration.JAEGER_ENDPOINT, target("/api/bearer").getUri().toString());
    System.setProperty(Configuration.JAEGER_AUTH_TOKEN, "invalid-token");

    HttpSender sender = (HttpSender) Configuration.SenderConfiguration.fromEnv().getSender();

    try {
      sender.send(new Process("robotrock"), generateSpans());
      fail("expecting exception");
    } catch (Exception te) {
      assertTrue(te.getMessage().contains("response 401"));
    }
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

    @Path("basic-auth")
    @POST
    public Response basicAuth(@HeaderParam("Authorization") String authHeader) {
      if (!authHeader.startsWith("Basic")) {
        return Response.status(Response.Status.BAD_REQUEST).build();
      }

      String userAndPass = new String(
          Base64.getDecoder().decode(authHeader.split("\\s+")[1]),
          StandardCharsets.US_ASCII);
      if (!userAndPass.equals("jdoe:password")) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }

      return Response.ok().build();
    }

    @Path("bearer")
    @POST
    public Response tokenAuth(@HeaderParam("Authorization") String authHeader) {
      if (!authHeader.startsWith("Bearer")) {
        return Response.status(Response.Status.BAD_REQUEST).build();
      }

      String token = authHeader.split("\\s+")[1];
      if (!token.equals("thetoken")) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }

      return Response.ok().build();
    }

    @Path("traces")
    @POST
    public void postHappy(@QueryParam("format") String format, String data) {
    }

    @Path("tracesErr")
    @POST()
    public Response postErr(@QueryParam("format") String format, String data) {
      return Response.serverError().build();
    }
  }

}
