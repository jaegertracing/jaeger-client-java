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
package com.uber.jaeger.senders.zipkin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import static junit.framework.TestCase.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.thriftjava.Annotation;
import com.twitter.zipkin.thriftjava.zipkincoreConstants;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;

import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;

public class ThriftSpanConverterTest {
  Tracer tracer;

  @Before
  public void setUp() {
    tracer =
        new Tracer.Builder("test-service-name", new InMemoryReporter(), new ConstSampler(true))
                .withZipkinSharedRPCSpan()
            .build();
  }

  @Test
  public void testSpanKindServerCreatesAnnotations() {
    Span span = (com.uber.jaeger.Span) tracer.buildSpan("operation-name").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);

    com.twitter.zipkin.thriftjava.Span zipkinSpan = ThriftSpanConverter.convertSpan(span);

    List<Annotation> annotations = zipkinSpan.getAnnotations();
    boolean serverReceiveFound = false;
    boolean serverSendFound = false;
    for (Annotation anno : annotations) {
      if (anno.getValue().equals(zipkincoreConstants.SERVER_RECV)) {
        serverReceiveFound = true;
      }

      if (anno.getValue().equals(zipkincoreConstants.SERVER_SEND)) {
        serverSendFound = true;
      }
    }

    assertTrue(serverReceiveFound);
    assertTrue(serverSendFound);
  }

  @Test
  public void testSpanKindClientCreatesAnnotations() {
    Span span = (com.uber.jaeger.Span) tracer.buildSpan("operation-name").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);

    com.twitter.zipkin.thriftjava.Span zipkinSpan = ThriftSpanConverter.convertSpan(span);

    List<Annotation> annotations = zipkinSpan.getAnnotations();
    boolean clientReceiveFound = false;
    boolean clientSendFound = false;
    for (Annotation anno : annotations) {
      if (anno.getValue().equals(zipkincoreConstants.CLIENT_RECV)) {
        clientReceiveFound = true;
      }

      if (anno.getValue().equals(zipkincoreConstants.CLIENT_SEND)) {
        clientSendFound = true;
      }
    }

    assertTrue(clientReceiveFound);
    assertTrue(clientSendFound);
  }

  @Test
  public void testExpectdLocalComponentNameUsed() {
    String expectedCompnentName = "local-name";
    Span span = (com.uber.jaeger.Span) tracer.buildSpan("operation-name").start();
    Tags.COMPONENT.set(span, expectedCompnentName);

    com.twitter.zipkin.thriftjava.Span zipkinSpan = ThriftSpanConverter.convertSpan(span);
    String actualComponent =
        new String(zipkinSpan.getBinary_annotations().get(0).getValue(), StandardCharsets.UTF_8);
    assertEquals(expectedCompnentName, actualComponent);
  }

  @Test
  public void testSpanDetectsEndpointTags() {
    int expectedIp = (127 << 24) | 1;
    int expectedPort = 8080;
    String expectedServiceName = "some-peer-service";
    Span span = (Span) tracer.buildSpan("test-service-operation").start();
    Tags.PEER_HOST_IPV4.set(span, expectedIp);
    Tags.PEER_PORT.set(span, expectedPort);
    Tags.PEER_SERVICE.set(span, expectedServiceName);

    assertEquals(expectedIp, ThriftSpanConverter.extractPeerEndpoint(span.getTags()).getIpv4());
    assertEquals(expectedPort, ThriftSpanConverter.extractPeerEndpoint(span.getTags()).getPort());
    assertEquals(expectedServiceName, ThriftSpanConverter.extractPeerEndpoint(span.getTags()).getService_name());
  }

  @Test
  public void testSpanDetectsIsClient() {
    Span span = (Span) tracer.buildSpan("test-service-operation").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);

    assertTrue(ThriftSpanConverter.isRPC(span));
    assertTrue(ThriftSpanConverter.isRPCClient(span));
  }

  @Test
  public void testSpanDetectsIsServer() {
    Span span = (Span) tracer.buildSpan("test-service-operation").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);

    assertTrue(ThriftSpanConverter.isRPC(span));
    assertFalse(ThriftSpanConverter.isRPCClient(span));
  }

   @Test
  public void testRPCChildSpanHasTheSameID() {
    String expectedOperation = "parent";
     Span client = (Span) tracer.buildSpan(expectedOperation)
             .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
             .start();

     Map<String, String> map = new HashMap<>();
     TextMap carrier = new TextMapInjectAdapter(map);
     tracer.inject(client.context(), Format.Builtin.TEXT_MAP, carrier);

     carrier = new TextMapExtractAdapter(map);
     SpanContext ctx = (SpanContext) tracer.extract(Format.Builtin.TEXT_MAP, carrier);
     assertEquals(client.context().getSpanID(), ctx.getSpanID());

     Span server = (Span)tracer.buildSpan("child")
             .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
             .asChildOf(ctx)
             .start();

     assertEquals("client and server must have the same span ID",
             client.context().getSpanID(), server.context().getSpanID());
   }
}
