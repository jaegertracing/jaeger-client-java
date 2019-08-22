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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.utils.Utils;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 *
 */
public class TraceContextCodecTest {

  private TraceContextCodec traceContextCodec = new TraceContextCodec.Builder().build();

  @Test
  public void support128BitTraceIdExtraction() {
    String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
    String parentSpan = "d1595c6ec91668af";

    String tracecontext = "00-463ac35c9f6413ad48485a3953bb6124-d1595c6ec91668af-01";

    DelegatingTextMap textMap = new DelegatingTextMap();
    textMap.put(TraceContextCodec.TRACE_CONTEXT_NAME, tracecontext);

    JaegerSpanContext context = traceContextCodec.extract(textMap);

    assertNotNull(HexCodec.lowerHexToUnsignedLong(parentSpan));
    assertEquals(HexCodec.lowerHexToUnsignedLong(hex128Bits).longValue(), context.getTraceIdLow());
    assertEquals(HexCodec.higherHexToUnsignedLong(hex128Bits).longValue(), context.getTraceIdHigh());
    assertEquals(HexCodec.lowerHexToUnsignedLong(parentSpan).longValue(), context.getParentId());
    assertTrue(context.isSampled());
  }

  @Test
  public void testInject() {
    TraceContextCodec traceContextCodec = new TraceContextCodec.Builder()
        .build();

    DelegatingTextMap entries = new DelegatingTextMap();
    long traceIdLow = 1;
    long spanId = 2;
    long parentId = 3;
    long traceIdHigh = HexCodec.hexToUnsignedLong("c281c27976c85681", 0, 16);
    JaegerSpanContext spanContext = new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, (byte) 0);

    traceContextCodec.inject(spanContext, entries);

    String expectedTraceContextHeader = "00-c281c27976c856810000000000000001-0000000000000002-00";
    assertEquals(1, entries.delegate.size());
    assertNotNull(entries.delegate.get(TraceContextCodec.TRACE_CONTEXT_NAME));
    assertEquals(expectedTraceContextHeader, entries.delegate.get(TraceContextCodec.TRACE_CONTEXT_NAME));
  }

  @Test
  public void testInjectWith64bit() {
    TraceContextCodec traceContextCodec = new TraceContextCodec.Builder()
        .build();

    DelegatingTextMap entries = new DelegatingTextMap();
    long traceIdLow = 1;
    long spanId = 2;
    long parentId = 3;
    long traceIdHigh = 0L;
    JaegerSpanContext spanContext = new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, (byte) 0);

    traceContextCodec.inject(spanContext, entries);

    assertEquals(1, entries.delegate.size());

    String traceContextHeader = entries.delegate.get(TraceContextCodec.TRACE_CONTEXT_NAME);
    assertNotNull(traceContextHeader);
    assertTrue(traceContextHeader.contains("0000000000000001"));
    //For 64 bit traces, we need to pad the left side with a random number to conform with the specification.
    //It should not contain all zeros.
    assertFalse(traceContextHeader.contains("00000000000000000000000000000001"));
  }

  @Test
  public void testInvalidTraceId() {
    TraceContextCodec traceContextCodec = new TraceContextCodec.Builder()
        .build();

    DelegatingTextMap textMap = new DelegatingTextMap();
    textMap.put(TraceContextCodec.TRACE_CONTEXT_NAME, "00-00000000000000000000000000000000-0000000000000002-00");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
  }

  @Test
  public void testNoTraceHeader() {
    TraceContextCodec traceContextCodec = new TraceContextCodec.Builder()
        .build();

    DelegatingTextMap textMap = new DelegatingTextMap();
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
  }


  @Test
  public void testInvalidParentId() {
    TraceContextCodec traceContextCodec = new TraceContextCodec.Builder()
        .build();

    DelegatingTextMap textMap = new DelegatingTextMap();
    textMap.put(TraceContextCodec.TRACE_CONTEXT_NAME, "00-00000000000000000000000000000001-0000000000000000-00");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
  }

  @Test
  public void testWithVersion() {
    TraceContextCodec traceContextCodec = new TraceContextCodec.Builder()
        .withVersion(1L)
        .build();

    DelegatingTextMap entries = new DelegatingTextMap();
    long traceIdLow = 1;
    long spanId = 2;
    long parentId = 3;
    long traceIdHigh = 1L;
    JaegerSpanContext spanContext = new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, (byte) 0);

    traceContextCodec.inject(spanContext, entries);

    assertEquals(1, entries.delegate.size());

    String traceContextHeader = entries.delegate.get(TraceContextCodec.TRACE_CONTEXT_NAME);
    assertNotNull(traceContextHeader);
    assertTrue(traceContextHeader.substring(0,2).equals("01"));
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

    public String get(String key) {
      return delegate.get(key);
    }
  }
}
