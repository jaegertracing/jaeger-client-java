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

package io.jaegertracing.propagation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.SpanContext;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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

  B3TextMapCodec b3Codec = new B3TextMapCodec();

  @Test
  public void downgrades128BitTraceIdToLower64Bits() throws Exception {
    String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
    String lower64Bits = "48485a3953bb6124";

    DelegatingTextMap textMap = new DelegatingTextMap();
    textMap.put(B3TextMapCodec.TRACE_ID_NAME, hex128Bits);
    textMap.put(B3TextMapCodec.SPAN_ID_NAME, lower64Bits);
    textMap.put(B3TextMapCodec.PARENT_SPAN_ID_NAME, "0");
    textMap.put(B3TextMapCodec.SAMPLED_NAME, "1");
    textMap.put(B3TextMapCodec.FLAGS_NAME, "1");

    SpanContext context = b3Codec.extract(textMap);

    assertNotNull(HexCodec.lowerHexToUnsignedLong(lower64Bits));
    assertEquals(HexCodec.lowerHexToUnsignedLong(lower64Bits).longValue(), context.getTraceId());
    assertEquals(HexCodec.lowerHexToUnsignedLong(lower64Bits).longValue(), context.getSpanId());
    assertEquals(0, context.getParentId());
    assertEquals(B3TextMapCodec.SAMPLED_FLAG | B3TextMapCodec.DEBUG_FLAG, context.getFlags());
  }

  @Test
  public void testInject() throws Exception {
    DelegatingTextMap textMap = new DelegatingTextMap();
    b3Codec.inject(new SpanContext(1, 1, 1, SAMPLED), textMap);

    assertTrue(textMap.containsKey(B3TextMapCodec.TRACE_ID_NAME));
    assertTrue(textMap.containsKey(B3TextMapCodec.SPAN_ID_NAME));
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
  }
}
