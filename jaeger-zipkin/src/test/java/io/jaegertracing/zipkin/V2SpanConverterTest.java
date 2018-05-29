/*
 * Copyright (c) 2018, The Jaeger Authors
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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import io.jaegertracing.JaegerTracer;
import io.jaegertracing.internal.Span;
import io.jaegertracing.internal.SpanContext;
import io.jaegertracing.reporter.InMemoryReporter;
import io.jaegertracing.sampler.ConstSampler;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Annotation;

@RunWith(DataProviderRunner.class)
public class V2SpanConverterTest {
  JaegerTracer tracer;

  @Before
  public void setUp() {
    tracer = new JaegerTracer.Builder("test-service-name")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .withZipkinSharedRpcSpan()
            .build();
  }

  // undef value is used to mark tags that should *not* be present
  private static final String UNDEF = "undef";

  // ANY value is used to mark tags that should be present but we don't care what it's value is
  private static final String ANY = "any";

  private enum SpanType {
    ROOT,
    CHILD,
    RPC_SERVER
  }

  @DataProvider
  public static Object[][] dataProviderTracerTags() {
    JaegerTracer tracer = new JaegerTracer.Builder("x").build();

    Map<String, String> rootTags = new HashMap<>();
    rootTags.put("tracer.jaeger.version", tracer.getVersion());
    rootTags.put("tracer.hostname", ANY);
    rootTags.put("tracer.tag.str", "y");
    rootTags.put("tracer.tag.bool", "true");
    rootTags.put("tracer.tag.num", "1");
    rootTags.put("sampler.type", "const");
    rootTags.put("sampler.param", "true");

    Map<String, String> childTags = new HashMap<>();
    childTags.put("tracer.jaeger.version", UNDEF);
    childTags.put("tracer.hostname", UNDEF);
    childTags.put("tracer.tag.str", UNDEF);
    childTags.put("tracer.tag.bool", UNDEF);
    childTags.put("tracer.tag.num", UNDEF);
    childTags.put("sampler.type", UNDEF);
    childTags.put("sampler.param", UNDEF);

    Map<String, String> rpcTags = new HashMap<>();
    rpcTags.put("tracer.jaeger.version", tracer.getVersion());
    rpcTags.put("tracer.hostname", ANY);
    rpcTags.put("tracer.tag.str", "y");
    rpcTags.put("tracer.tag.bool", "true");
    rpcTags.put("tracer.tag.num", "1");
    rpcTags.put("sampler.type", UNDEF);
    rpcTags.put("sampler.param", UNDEF);

    return new Object[][] {
        { SpanType.ROOT, rootTags },
        { SpanType.CHILD, childTags },
        { SpanType.RPC_SERVER, rpcTags },
    };
  }

  @Test
  @UseDataProvider("dataProviderTracerTags")
  public void testTracerTags(SpanType spanType, Map<String, String> expectedTags) throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    JaegerTracer tracer = new JaegerTracer.Builder("x")
        .withReporter(spanReporter)
        .withSampler(new ConstSampler(true))
        .withZipkinSharedRpcSpan()
        .withTag("tag.str", "y")
        .withTag("tag.bool", true)
        .withTag("tag.num", 1)
        .build();

    Span span = (Span) tracer.buildSpan("root").start();
    if (spanType == SpanType.CHILD) {
      span = (Span) tracer.buildSpan("child").asChildOf(span).start();
    } else if (spanType == SpanType.RPC_SERVER) {
      span =
          (Span)
              tracer
                  .buildSpan("rpc-server")
                  .asChildOf(span)
                  .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                  .start();
    }
    zipkin2.Span zipkinSpan = V2SpanConverter.convertSpan(span);

    Map<String, String> zipkinTags = zipkinSpan.tags();
    for (Map.Entry<String, String> entry : expectedTags.entrySet()) {
      String key = entry.getKey();
      Object expectedValue = entry.getValue();
      String tagValue = zipkinTags.get(key);
      if (expectedValue.equals(UNDEF)) {
        assertNull("Not expecting " + key + " for " + spanType, tagValue);
      } else if (expectedValue.equals(ANY)) {
        assertNotNull(key, tagValue);
      } else {
        assertEquals("Expecting " + key + " for " + spanType, expectedValue, tagValue);
      }
    }
  }

  @Test
  public void testSpanKindConsumerHasCorrectKind() {
    Span span = (Span) tracer.buildSpan("operation-name").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CONSUMER);

    zipkin2.Span zipkinSpan = V2SpanConverter.convertSpan(span);

    assertEquals(zipkinSpan.kind(), zipkin2.Span.Kind.CONSUMER);
  }

  @Test
  public void testSpanKindProducerHasCorrectKind() {
    Span span = (Span) tracer.buildSpan("operation-name").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);

    zipkin2.Span zipkinSpan = V2SpanConverter.convertSpan(span);

    assertEquals(zipkinSpan.kind(), zipkin2.Span.Kind.PRODUCER);
  }

  @Test
  public void testExpectedLocalComponentNameUsed() {
    String expectedComponentName = "local-name";
    Span span = (Span) tracer.buildSpan("operation-name").start();
    Tags.COMPONENT.set(span, expectedComponentName);

    zipkin2.Span zipkinSpan = V2SpanConverter.convertSpan(span);

    String actualComponent = zipkinSpan.tags().get(Tags.COMPONENT.getKey());
    assertEquals(expectedComponentName, actualComponent);
  }

  @Test
  public void testSpanDetectsEndpointTags() {
    String expectedIp = "127.0.0.1";
    Integer expectedPort = 8080;
    String expectedServiceName = "some-peer-service";
    Span span = (Span) tracer.buildSpan("test-service-operation").start();
    Tags.PEER_HOST_IPV4.set(span, expectedIp);
    Tags.PEER_PORT.set(span, expectedPort);
    Tags.PEER_SERVICE.set(span, expectedServiceName);

    assertEquals(expectedIp, V2SpanConverter.extractPeerEndpoint(span.getTags()).ipv4());
    assertEquals(expectedPort, V2SpanConverter.extractPeerEndpoint(span.getTags()).port());
    assertEquals(expectedServiceName, V2SpanConverter.extractPeerEndpoint(span.getTags()).serviceName());
  }

  @Test
  public void testSpanDetectsIsClient() {
    Span span = (Span) tracer.buildSpan("test-service-operation").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);

    assertTrue(ConverterUtil.isRpc(span));
    assertTrue(ConverterUtil.isRpcClient(span));
  }

  @Test
  public void testSpanDetectsIsServer() {
    Span span = (Span) tracer.buildSpan("test-service-operation").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);

    assertTrue(ConverterUtil.isRpc(span));
    assertFalse(ConverterUtil.isRpcClient(span));
  }

  @Test
  public void testRpcChildSpanHasTheSameId() {
    String expectedOperation = "parent";
    Span client = (Span) tracer.buildSpan(expectedOperation)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start();

    Map<String, String> map = new HashMap<>();
    TextMap carrier = new TextMapInjectAdapter(map);
    tracer.inject(client.context(), Format.Builtin.TEXT_MAP, carrier);

    carrier = new TextMapExtractAdapter(map);
    SpanContext ctx = (SpanContext) tracer.extract(Format.Builtin.TEXT_MAP, carrier);
    assertEquals(client.context().getSpanId(), ctx.getSpanId());

    Span server = (Span)tracer.buildSpan("child")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .asChildOf(ctx)
        .start();

    assertEquals("client and server must have the same span ID",
        client.context().getSpanId(), server.context().getSpanId());
  }

  @Test
  public void testSpanLogsCreateAnnotations() {
    Span span = (Span) tracer.buildSpan("span-with-logs").start();

    span.log("event");

    // use sorted map for consistent ordering in test
    Map<String, Object> fields = new TreeMap<String, Object>();
    fields.put("event", "structured data");
    fields.put("string", "something");
    fields.put("number", 42);
    fields.put("boolean", true);
    span.log(fields);

    zipkin2.Span zipkinSpan = V2SpanConverter.convertSpan(span);

    List<String> annotationValues = new ArrayList<String>();
    for (Annotation annotation : zipkinSpan.annotations()) {
      annotationValues.add(annotation.value());
    }

    List<String> expectedValues = new ArrayList<String>();
    expectedValues.add("event");
    expectedValues.add("{\"boolean\":true,\"event\":\"structured data\",\"number\":42,\"string\":\"something\"}");

    assertEquals("zipkin span should contain matching annotations for span logs", expectedValues, annotationValues);
  }
}
