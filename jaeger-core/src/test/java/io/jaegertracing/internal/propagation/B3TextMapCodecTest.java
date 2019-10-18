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
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.Test;

/**
 * NOTE:
 * These tests are based on the ones from jaeger-b3, and included to improve the test
 * coverage. The main testing of the B3TextMapCodec is still performed via the tests
 * in the jaeger-b3 module.
 *
 */
public class B3TextMapCodecTest {
  static final byte SAMPLED = 1;

  B3TextMapCodec b3Codec = new B3TextMapCodec.Builder().build();

  @Test
  public void support128BitTraceIdExtraction() {
    String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
    String lower64Bits = "48485a3953bb6124";

    DelegatingTextMap textMap = new DelegatingTextMap();
    textMap.put(B3TextMapCodec.TRACE_ID_NAME, hex128Bits);
    textMap.put(B3TextMapCodec.SPAN_ID_NAME, lower64Bits);
    textMap.put(B3TextMapCodec.PARENT_SPAN_ID_NAME, "0");
    textMap.put(B3TextMapCodec.SAMPLED_NAME, "1");
    textMap.put(B3TextMapCodec.FLAGS_NAME, "1");
    textMap.put(B3TextMapCodec.BAGGAGE_PREFIX + "foo", "bar");
    textMap.put("random-foo", "bar");

    JaegerSpanContext context = b3Codec.extract(textMap);

    assertNotNull(HexCodec.lowerHexToUnsignedLong(lower64Bits));
    assertEquals(HexCodec.lowerHexToUnsignedLong(hex128Bits).longValue(), context.getTraceIdLow());
    assertEquals(HexCodec.higherHexToUnsignedLong(hex128Bits).longValue(), context.getTraceIdHigh());
    assertEquals(HexCodec.lowerHexToUnsignedLong(lower64Bits).longValue(), context.getSpanId());
    assertEquals(0, context.getParentId());
    assertEquals(B3TextMapCodec.SAMPLED_FLAG | B3TextMapCodec.DEBUG_FLAG, context.getFlags());
    assertEquals(1, ((Set<Entry<String, String>>)context.baggageItems()).size());
    assertEquals("bar", context.getBaggageItem("foo"));
  }

  @Test
  public void testDefault() {
    B3TextMapCodec b3Codec = new B3TextMapCodec.Builder()
        .build();

    DelegatingTextMap entries = new DelegatingTextMap();
    long traceIdLow = 1;
    long spanId = 2;
    long parentId = 3;
    JaegerSpanContext spanContext = new JaegerSpanContext(0L, traceIdLow, spanId, parentId, (byte)0)
        .withBaggageItem("foo", "bar");

    b3Codec.inject(spanContext, entries);
    assertEquals(5, entries.delegate.size());
    assertNotNull(entries.delegate.get(B3TextMapCodec.TRACE_ID_NAME));
    assertNotNull(entries.delegate.get(B3TextMapCodec.SPAN_ID_NAME));
    assertNotNull(entries.delegate.get(B3TextMapCodec.PARENT_SPAN_ID_NAME));
    assertNotNull(entries.delegate.get(B3TextMapCodec.SAMPLED_NAME));
    assertEquals("bar", entries.delegate.get("baggage-foo"));
  }

  @Test
  public void testChangeBaggagePrefix() {
    B3TextMapCodec b3Codec = new B3TextMapCodec.Builder()
        .withBaggagePrefix("foo")
        .build();

    DelegatingTextMap entries = new DelegatingTextMap();
    long traceIdLow = 1;
    long spanId = 2;
    long parentId = 3;
    JaegerSpanContext spanContext = new JaegerSpanContext(0L, traceIdLow, spanId, parentId, (byte)0)
        .withBaggageItem("foo", "bar");

    b3Codec.inject(spanContext, entries);
    assertEquals(5, entries.delegate.size());
    assertEquals("bar", entries.delegate.get("foofoo"));
  }

  @Test
  public void testInject() {
    DelegatingTextMap textMap = new DelegatingTextMap();
    long traceIdLow = 1;
    long spanId = 2;
    long parentId = 3;
    b3Codec.inject(new JaegerSpanContext(0L, traceIdLow, spanId, parentId, SAMPLED), textMap);

    assertTrue(textMap.containsKey(B3TextMapCodec.TRACE_ID_NAME));
    assertTrue(textMap.containsKey(B3TextMapCodec.SPAN_ID_NAME));
  }

  @Test
  public void testInject128BitTraceId() {
    DelegatingTextMap textMap = new DelegatingTextMap();
    long traceIdLow = 1;
    long traceIdHigh = 2;
    long spanId = 3;
    long parentId = 4;
    b3Codec.inject(new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, SAMPLED), textMap);

    final String traceId = textMap.get(B3TextMapCodec.TRACE_ID_NAME);
    assertEquals(traceIdLow, HexCodec.lowerHexToUnsignedLong(traceId).longValue());
    assertEquals(traceIdHigh, HexCodec.higherHexToUnsignedLong(traceId).longValue());
  }

  static class DelegatingTextMap implements TextMap {
    final Map<String, String> delegate = new LinkedHashMap<>();

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      return delegate.entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
      delegate.put(key, value);
    }

    public boolean containsKey(String key) {
      return delegate.containsKey(key);
    }

    public String get(String key) {
      return delegate.get(key);
    }
  }
}
