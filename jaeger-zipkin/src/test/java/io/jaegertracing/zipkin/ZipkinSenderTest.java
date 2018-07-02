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

package io.jaegertracing.zipkin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.exceptions.SenderException;
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory;
import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.spi.Reporter;
import io.jaegertracing.zipkin.ZipkinSender;
import io.jaegertracing.zipkin.internal.ThriftSpanConverter;
import io.jaegertracing.zipkin.internal.ThriftSpanEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.codec.Encoding;
import zipkin2.internal.HexCodec;
import zipkin2.junit.ZipkinRule;
import zipkin2.reporter.urlconnection.URLConnectionSender;

public class ZipkinSenderTest {
  final int messageMaxBytes = 1000;

  @Rule public ZipkinRule zipkinRule = new ZipkinRule();

  ZipkinSender sender;
  Reporter reporter;
  ThriftSpanConverter converter;
  JaegerTracer tracer;

  @Before
  public void setUp() {
    reporter = new InMemoryReporter();
    tracer = new JaegerTracer.Builder("test-sender")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .withMetricsFactory(new InMemoryMetricsFactory())
            .build();
    sender = newSender(messageMaxBytes);
    converter = new ThriftSpanConverter();
  }

  @After
  public void tearDown() throws Exception {
    sender.close();
    reporter.close();
  }

  /** This sender uses jaeger thrifts, so json (default for v2 senders) isn't ok */
  @Test(expected = IllegalArgumentException.class)
  public void testJsonNotAllowed() {
    ZipkinSender.create(URLConnectionSender.create("http://localhost:9411/api/v2/spans"));
  }

  @Test
  public void testAppendSpanTooLarge() {
    JaegerSpan jaegerSpan = tracer.buildSpan("raza").start();
    String msg = "";
    for (int i = 0; i < 1001; i++) {
      msg += ".";
    }

    jaegerSpan.log(msg);
    try {
      sender.append(jaegerSpan);
      fail("The line above shoud throw");
    } catch (SenderException e) {
      assertEquals(e.getDroppedSpanCount(), 1);
    }
  }

  @Test
  public void testAppend() throws Exception {
    JaegerSpan jaegerSpan = tracer.buildSpan("raza").start();
    com.twitter.zipkin.thriftjava.Span span = ThriftSpanConverter.convertSpan(jaegerSpan);

    int expectedNumSpans = 11;
    List<byte[]> spansToSend = new ArrayList<>(expectedNumSpans);
    for (int i = 0; i < expectedNumSpans; i++) {
      spansToSend.add(new ThriftSpanEncoder().encode(ZipkinSender.backFillHostOnAnnotations(span)));
    }

    // create a sender that's a multiple of the span size (accounting for span overhead)
    // this allows us to test the boundary conditions of writing spans.
    int messageMaxBytes = sender.delegate.messageSizeInBytes(spansToSend);
    sender.close();
    sender = newSender(messageMaxBytes);

    // add enough spans to be under buffer limit
    for (int i = 0; i < expectedNumSpans - 1; i++) {
      assertEquals(0, sender.append(jaegerSpan));
    }

    // add a span that overflows the limit to hit the last branch
    int result = sender.append(jaegerSpan);
    assertEquals(expectedNumSpans, result);
  }

  @Test
  public void testFlushSendsSpan() throws Exception {
    JaegerSpan expectedSpan = tracer.buildSpan("raza").start();

    assertEquals(0, sender.append(expectedSpan));
    assertEquals(1, sender.flush());

    List<List<zipkin2.Span>> traces = zipkinRule.getTraces();
    assertEquals(traces.size(), 1);
    assertEquals(traces.get(0).size(), 1);

    zipkin2.Span actualSpan = traces.get(0).get(0);
    JaegerSpanContext context = expectedSpan.context();

    assertEquals(context.getTraceId(), HexCodec.lowerHexToUnsignedLong(actualSpan.traceId()));
    assertEquals(context.getSpanId(), HexCodec.lowerHexToUnsignedLong(actualSpan.id()));
    assertEquals(context.getParentId(), 0L);
    assertNull(actualSpan.parentId());
    assertEquals(expectedSpan.getOperationName(), actualSpan.name());
    assertEquals(tracer.getServiceName(), actualSpan.localServiceName());
  }

  @Test
  public void testAppendSpanWithLogs() throws Exception {
    JaegerSpan jaegerSpan = tracer.buildSpan("jaegerSpan-with-logs").start();

    jaegerSpan.log("event");

    // use sorted map for consistent ordering in test
    Map<String, Object> fields = new TreeMap<String, Object>();
    fields.put("event", "structured data");
    fields.put("string", "something");
    fields.put("number", 42);
    fields.put("boolean", true);
    jaegerSpan.log(fields);

    sender.append(jaegerSpan);
    sender.flush();

    List<List<zipkin2.Span>> traces = zipkinRule.getTraces();
    assertEquals(1, traces.size());
    assertEquals(1, traces.get(0).size());

    zipkin2.Span zipkinSpan = traces.get(0).get(0);
    assertEquals(2, zipkinSpan.annotations().size());

    // ignore order by using set
    Set<String> annotationValues = new HashSet<String>();
    for (Annotation annotation : zipkinSpan.annotations()) {
      annotationValues.add(annotation.value());
    }

    Set<String> expectedValues = new HashSet<String>();
    expectedValues.add("event");
    expectedValues.add("{\"boolean\":true,\"event\":\"structured data\",\"number\":42,\"string\":\"something\"}");

    assertEquals("zipkin span should contain matching annotations for span logs", expectedValues, annotationValues);
  }

  private ZipkinSender newSender(int messageMaxBytes) {
    return ZipkinSender.create(
        URLConnectionSender.newBuilder()
            .encoding(Encoding.THRIFT)
            .messageMaxBytes(messageMaxBytes)
            .endpoint(zipkinRule.httpUrl() + "/api/v1/spans")
            .build());
  }
}
