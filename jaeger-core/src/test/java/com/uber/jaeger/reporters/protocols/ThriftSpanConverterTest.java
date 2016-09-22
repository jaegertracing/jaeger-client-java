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
package com.uber.jaeger.reporters.protocols;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import com.twitter.zipkin.thriftjava.Annotation;
import com.twitter.zipkin.thriftjava.zipkincoreConstants;
import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.tag.Tags;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ThriftSpanConverterTest {
  Tracer tracer;

  @Before
  public void setUp() {
    tracer =
        new Tracer.Builder("test-service-name", new InMemoryReporter(), new ConstSampler(true))
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
}
