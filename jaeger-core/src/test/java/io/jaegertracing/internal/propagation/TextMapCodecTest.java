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

package io.jaegertracing.internal.propagation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.exceptions.EmptyTracerStateStringException;
import io.jaegertracing.internal.exceptions.MalformedTracerStateStringException;
import io.jaegertracing.internal.exceptions.TraceIdOutOfBoundException;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;

public class TextMapCodecTest {

  @Test
  public void testBuilder()  {
    TextMapCodec codec = TextMapCodec.builder()
        .withUrlEncoding(true)
        .withSpanContextKey("jaeger-trace-id")
        .withBaggagePrefix("jaeger-baggage-")
        .build();
    assertNotNull(codec);
    String str = codec.toString();
    assertTrue(str.contains("contextKey=jaeger-trace-id"));
    assertTrue(str.contains("baggagePrefix=jaeger-baggage-"));
    assertTrue(str.contains("urlEncoding=true"));
  }

  @Test
  public void testWithoutBuilder() {
    TextMapCodec codec = new TextMapCodec(false);
    String str = codec.toString();
    assertTrue(str.contains("contextKey=uber-trace-id"));
    assertTrue(str.contains("baggagePrefix=uberctx-"));
    assertTrue(str.contains("urlEncoding=false"));
  }

  @Test(expected = MalformedTracerStateStringException.class)
  public void testContextFromStringMalformedException() throws Exception {
    TextMapCodec.contextFromString("ff:ff:ff");
  }

  @Test(expected = EmptyTracerStateStringException.class)
  public void testContextFromStringEmptyException() throws Exception {
    TextMapCodec.contextFromString("");
  }

  @Test(expected = TraceIdOutOfBoundException.class)
  public void testContextFromStringTraceIdOutOfBoundExceptionTooBig() {
    TextMapCodec.contextFromString("123456789012345678901234567890123:ff:ff:ff");
  }

  @Test(expected = TraceIdOutOfBoundException.class)
  public void testContextFromStringTraceIdOutOfBoundExceptionToSmall() {
    TextMapCodec.contextFromString(":ff:ff:ff");
  }

  @Test
  public void testContextFromString() throws Exception {
    JaegerSpanContext context = TextMapCodec.contextFromString("ff:dd:cc:4");
    assertEquals(context.getTraceIdLow(), 255L);
    assertEquals(context.getTraceIdHigh(), 0L);
    assertEquals(context.getSpanId(), 221L);
    assertEquals(context.getParentId(), 204L);
    assertEquals(context.getFlags(), 4);
  }

  @Test
  public void testContextFromStringWith128BitTraceId() {
    JaegerSpanContext context = TextMapCodec.contextFromString("100000000000000ff:dd:cc:4");
    assertEquals(context.getTraceIdLow(), 255L);
    assertEquals(context.getTraceIdHigh(), 1L);
    assertEquals(context.getSpanId(), 221L);
    assertEquals(context.getParentId(), 204L);
    assertEquals(context.getFlags(), 4);
  }

  @Test
  public void testContextAsStringWith128BitTraceId() {
    long traceIdLow = 1L;
    long traceIdHigh = 2L;
    long spanId = 3L;
    long parentId = 4L;
    byte flags = (byte) 5;

    JaegerSpanContext context = new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, flags);
    assertEquals(
            "20000000000000001:3:4:5", TextMapCodec.contextAsString(context));
  }

  @Test
  public void testContextAsStringFormatsPositiveFields() {
    long traceIdLow = -10L;
    long spanId = -10L;
    long parentId = -10L;
    byte flags = (byte) 129;

    JaegerSpanContext context = new JaegerSpanContext(0L, traceIdLow, spanId, parentId, flags);
    assertEquals(
        "fffffffffffffff6:fffffffffffffff6:fffffffffffffff6:81", TextMapCodec.contextAsString(context));
    JaegerSpanContext contextFromStr = TextMapCodec.contextFromString(context.toString());
    assertEquals(traceIdLow, contextFromStr.getTraceIdLow());
    assertEquals(0L, contextFromStr.getTraceIdHigh());
    assertEquals(spanId, contextFromStr.getSpanId());
    assertEquals(parentId, contextFromStr.getParentId());
    assertEquals(flags, contextFromStr.getFlags());
  }

  /**
   * Tests that the codec will include baggage from header "jaeger-baggage".
   */
  @Test
  public void testAdhocBaggageWithTraceId() {
    TextMapCodec codec = new TextMapCodec(false);
    Map<String, String> headers = new HashMap<>();
    long traceIdLow = 42;
    long spanId = 1;
    long parentId = 0;
    TextMap textMap = new TestTextMap(headers);
    codec.inject(new JaegerSpanContext(0L, traceIdLow, spanId, parentId, (byte)1), textMap);
    headers.put("jaeger-baggage", "k1=v1, k2 = v2");
    JaegerSpanContext context = codec.extract(textMap);
    assertEquals("must have trace ID", 42, context.getTraceIdLow());
    assertEquals("must have trace ID", 0L, context.getTraceIdHigh());
    assertEquals("must have bagggae", "v1", context.getBaggageItem("k1"));
    assertEquals("must have bagggae", "v2", context.getBaggageItem("k2"));
  }

  /**
   * Tests that the codec will return non-null SpanContext even if the only header
   * present is "jaeger-baggage".
   */
  @Test
  public void testAdhocBaggageWithoutTraceId() {
    Map<String, String> headers = new HashMap<String, String>();
    TextMap textMap = new TestTextMap(headers);
    headers.put("jaeger-baggage", "k1=v1, k2 = v2, k3=v3=d3");
    TextMapCodec codec = new TextMapCodec(false);
    JaegerSpanContext context = codec.extract(textMap);
    assertEquals("v1", context.getBaggageItem("k1"));
    assertEquals("v2", context.getBaggageItem("k2"));
    assertEquals(null, context.getBaggageItem("k3"));
  }

  private static class TestTextMap implements TextMap {

    private final Map<String,String> values;

    public TestTextMap(Map<String, String> values) {
      this.values = values;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      return values.entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
      values.put(key, value);
    }

    public String get(String key) {
      return values.get(key);
    }
  }
}
