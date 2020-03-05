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

import static io.jaegertracing.internal.propagation.TraceContextCodec.TRACE_PARENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.internal.JaegerSpanContext;
import io.opentracing.propagation.TextMapAdapter;
import java.util.HashMap;
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
    String tracecontext = String.format("00-%s-%s-01", hex128Bits, parentSpan);

    TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
    textMap.put(TRACE_PARENT, tracecontext);
    JaegerSpanContext context = traceContextCodec.extract(textMap);

    assertNotNull(HexCodec.lowerHexToUnsignedLong(parentSpan));
    assertEquals(HexCodec.lowerHexToUnsignedLong(hex128Bits).longValue(), context.getTraceIdLow());
    assertEquals(HexCodec.higherHexToUnsignedLong(hex128Bits).longValue(), context.getTraceIdHigh());
    assertEquals(HexCodec.lowerHexToUnsignedLong(parentSpan).longValue(), context.getSpanId());
    assertTrue(context.isSampled());
  }

  @Test
  public void testInject() {
    HashMap<String, String> carrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(carrier);
    long traceIdLow = 1;
    long spanId = 2;
    long parentId = 3;
    long traceIdHigh = HexCodec.hexToUnsignedLong("c281c27976c85681", 0, 16);
    JaegerSpanContext spanContext = new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, (byte) 0);

    traceContextCodec.inject(spanContext, textMap);

    String expectedTraceContextHeader = "00-c281c27976c856810000000000000001-0000000000000002-00";
    assertEquals(1, carrier.size());
    assertNotNull(carrier.get(TRACE_PARENT));
    assertEquals(expectedTraceContextHeader, carrier.get(TRACE_PARENT));
  }

  @Test
  public void testInjectWith64bit() {
    HashMap<String, String> carrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(carrier);
    long traceIdLow = 1;
    long spanId = 2;
    long parentId = 3;
    long traceIdHigh = 0L;
    JaegerSpanContext spanContext = new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, (byte) 0);

    traceContextCodec.inject(spanContext, textMap);

    assertEquals(1, carrier.size());

    String traceContextHeader = carrier.get(TRACE_PARENT);
    assertNotNull(traceContextHeader);
    assertTrue(traceContextHeader.contains("0000000000000001"));
    //For 64 bit traces, we need to pad the left side with a random number to conform with the specification.
    //It should not contain all zeros.
    assertTrue(traceContextHeader.contains("00000000000000000000000000000001"));
  }

  @Test
  public void testInvalidTraceId() {
    TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
    textMap.put(TRACE_PARENT, "00-00000000000000000000000000000000-0000000000000002-00");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
  }

  @Test
  public void testNoTraceHeader() {
    TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
  }


  @Test
  public void testInvalidParentId() {
    TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
    textMap.put(TRACE_PARENT, "00-00000000000000000000000000000001-0000000000000000-00");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
  }
}