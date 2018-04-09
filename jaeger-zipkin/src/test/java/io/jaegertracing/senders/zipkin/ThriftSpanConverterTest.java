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

package io.jaegertracing.senders.zipkin;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import com.twitter.zipkin.thriftjava.Annotation;
import com.twitter.zipkin.thriftjava.BinaryAnnotation;
import com.twitter.zipkin.thriftjava.zipkincoreConstants;
import io.jaegertracing.Span;
import io.jaegertracing.SpanContext;
import io.jaegertracing.Tracer;
import io.jaegertracing.reporters.InMemoryReporter;
import io.jaegertracing.samplers.ConstSampler;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class ThriftSpanConverterTest {
  Tracer tracer;

  @Before
  public void setUp() {
    tracer =
        new Tracer.Builder("test-service-name", new InMemoryReporter(), new ConstSampler(true))
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
    Tracer tracer = new Tracer.Builder("x", null, null).build();

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
    Tracer tracer = new Tracer.Builder("x", spanReporter, new ConstSampler(true))
        .withZipkinSharedRpcSpan()
        .withTag("tag.str", "y")
        .withTag("tag.bool", true)
        .withTag("tag.num", 1)
        .build();

    Span span = (Span) tracer.buildSpan("root").startManual();
    if (spanType == SpanType.CHILD) {
      span = (Span) tracer.buildSpan("child").asChildOf(span).startManual();
    } else if (spanType == SpanType.RPC_SERVER) {
      span =
          (Span)
              tracer
                  .buildSpan("rpc-server")
                  .asChildOf(span)
                  .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                  .startManual();
    }
    com.twitter.zipkin.thriftjava.Span zipkinSpan = ThriftSpanConverter.convertSpan(span);

    List<BinaryAnnotation> annotations = zipkinSpan.getBinary_annotations();
    for (Map.Entry<String, String> entry : expectedTags.entrySet()) {
      String key = entry.getKey();
      Object expectedValue = entry.getValue();
      BinaryAnnotation anno = findBinaryAnnotation(annotations, key);
      if (expectedValue.equals(UNDEF)) {
        assertNull("Not expecting " + key + " for " + spanType, anno);
      } else if (expectedValue.equals(ANY)) {
        assertEquals(key, anno.getKey());
      } else {
        String actualValue = new String(anno.getValue(), StandardCharsets.UTF_8);
        assertEquals("Expecting " + key + " for " + spanType, expectedValue, actualValue);
      }
    }
  }

  private BinaryAnnotation findBinaryAnnotation(List<BinaryAnnotation> annotations, String key) {
    for (BinaryAnnotation anno : annotations) {
      if (anno.getKey().equals(key)) {
        return anno;
      }
    }
    return null;
  }

  @Test
  public void testSpanKindServerCreatesAnnotations() {
    Span span = (io.jaegertracing.Span) tracer.buildSpan("operation-name").startManual();
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
    Span span = (io.jaegertracing.Span) tracer.buildSpan("operation-name").startManual();
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
  public void testExpectedLocalComponentNameUsed() {
    String expectedComponentName = "local-name";
    Span span = (io.jaegertracing.Span) tracer.buildSpan("operation-name").startManual();
    Tags.COMPONENT.set(span, expectedComponentName);

    com.twitter.zipkin.thriftjava.Span zipkinSpan = ThriftSpanConverter.convertSpan(span);
    String actualComponent =
        new String(zipkinSpan.getBinary_annotations().get(3).getValue(), StandardCharsets.UTF_8);
    assertEquals(expectedComponentName, actualComponent);
  }

  @Test
  public void testSpanDetectsEndpointTags() {
    int expectedIp = (127 << 24) | 1;
    int expectedPort = 8080;
    String expectedServiceName = "some-peer-service";
    Span span = (Span) tracer.buildSpan("test-service-operation").startManual();
    Tags.PEER_HOST_IPV4.set(span, expectedIp);
    Tags.PEER_PORT.set(span, expectedPort);
    Tags.PEER_SERVICE.set(span, expectedServiceName);

    assertEquals(expectedIp, ThriftSpanConverter.extractPeerEndpoint(span.getTags()).getIpv4());
    assertEquals(expectedPort, ThriftSpanConverter.extractPeerEndpoint(span.getTags()).getPort());
    assertEquals(expectedServiceName, ThriftSpanConverter.extractPeerEndpoint(span.getTags()).getService_name());
  }

  @Test
  public void testSpanDetectsIsClient() {
    Span span = (Span) tracer.buildSpan("test-service-operation").startManual();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);

    assertTrue(ThriftSpanConverter.isRpc(span));
    assertTrue(ThriftSpanConverter.isRpcClient(span));
  }

  @Test
  public void testSpanDetectsIsServer() {
    Span span = (Span) tracer.buildSpan("test-service-operation").startManual();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);

    assertTrue(ThriftSpanConverter.isRpc(span));
    assertFalse(ThriftSpanConverter.isRpcClient(span));
  }

  @Test
  public void testRpcChildSpanHasTheSameId() {
    String expectedOperation = "parent";
    Span client = (Span) tracer.buildSpan(expectedOperation)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .startManual();

    Map<String, String> map = new HashMap<>();
    TextMap carrier = new TextMapInjectAdapter(map);
    tracer.inject(client.context(), Format.Builtin.TEXT_MAP, carrier);

    carrier = new TextMapExtractAdapter(map);
    SpanContext ctx = (SpanContext) tracer.extract(Format.Builtin.TEXT_MAP, carrier);
    assertEquals(client.context().getSpanId(), ctx.getSpanId());

    Span server = (Span)tracer.buildSpan("child")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
        .asChildOf(ctx)
        .startManual();

    assertEquals("client and server must have the same span ID",
        client.context().getSpanId(), server.context().getSpanId());
  }

  @Test
  public void testSpanLogsCreateAnnotations() {
    Span span = (io.jaegertracing.Span) tracer.buildSpan("span-with-logs").startManual();

    span.log("event");

    // use sorted map for consistent ordering in test
    Map<String, Object> fields = new TreeMap<String, Object>();
    fields.put("event", "structured data");
    fields.put("string", "something");
    fields.put("number", 42);
    fields.put("boolean", true);
    span.log(fields);

    com.twitter.zipkin.thriftjava.Span zipkinSpan = ThriftSpanConverter.convertSpan(span);

    List<String> annotationValues = new ArrayList<String>();
    for (Annotation annotation : zipkinSpan.getAnnotations()) {
      annotationValues.add(annotation.getValue());
    }

    List<String> expectedValues = new ArrayList<String>();
    expectedValues.add("event");
    expectedValues.add("{\"boolean\":true,\"event\":\"structured data\",\"number\":42,\"string\":\"something\"}");

    assertEquals("zipkin span should contain matching annotations for span logs", expectedValues, annotationValues);
  }
}
