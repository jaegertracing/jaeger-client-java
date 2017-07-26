/*
 * Copyright (c) 2017, Uber Technologies, Inc
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
