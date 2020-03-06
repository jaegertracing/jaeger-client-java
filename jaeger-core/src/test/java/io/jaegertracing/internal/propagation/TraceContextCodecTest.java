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

import static io.jaegertracing.internal.propagation.TraceContextCodec.TRACESTATE_MAX_MEMBERS;
import static io.jaegertracing.internal.propagation.TraceContextCodec.TRACE_PARENT;
import static io.jaegertracing.internal.propagation.TraceContextCodec.TRACE_STATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.propagation.TraceContextCodec.Builder;
import io.opentracing.propagation.TextMapAdapter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 *
 */
public class TraceContextCodecTest {

  private static final String TRACE_ID_BASE16 = "ff000000000000000000000000000041";
  private static final String SPAN_ID_BASE16 = "ff00000000000041";
  private static final JaegerSpanContext SPAN_CONTEXT =
      new JaegerSpanContext(0, 1, 2, 3, (byte)0);
  private static final String EXAMPLE_TRACE_PARENT = "00-00000000000000000000000000000001-0000000000000002-00";
  private static final String STRING_VALUE_630 = "11111111111111111111111111111111111111111111111111111111111111111"
      + "11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111"
      + "11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111"
      + "11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111"
      + "11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111"
      + "1111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111"
      + "1111111111111111111111111111111";

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
    Map<String, String> carrier = new HashMap<>();
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
    Map<String, String> carrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(carrier);

    traceContextCodec.inject(SPAN_CONTEXT, textMap);
    assertEquals(1, carrier.size());

    String traceParent = carrier.get(TRACE_PARENT);
    assertEquals(EXAMPLE_TRACE_PARENT, traceParent);
    JaegerSpanContext extractedContext = traceContextCodec.extract(textMap);
    assertEquals("1:2:0:0", extractedContext.toString());
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


  @Test
  public void testExtractTraceState() {
    JaegerSpanContext spanContext = SPAN_CONTEXT
        .withBaggageItem("foo", "bar");

    Map<String, String> carrier = new  HashMap<>();
    TraceContextCodec traceContextCodec = new TraceContextCodec.Builder().build();
    traceContextCodec.inject(spanContext, new TextMapAdapter(carrier));

    assertEquals(2, carrier.size());
    String traceState = carrier.get(TRACE_STATE);
    assertEquals("foo=bar", traceState);
  }

  @Test
  public void testExtractTraceStateInvalidDelimiter() {
    Map<String, String> invalidHeaders = new HashMap<>();
    invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
    invalidHeaders.put(TRACE_STATE, "foo=bar,test-test");
    TraceContextCodec traceContextCodec = new Builder().build();

    JaegerSpanContext spanContext = traceContextCodec.extract(new TextMapAdapter(invalidHeaders));
    assertEquals(0, spanContext.baggageCount());
  }

  @Test
  public void testExtractTraceStateLongerThan512() {
    Map<String, String> headers = new HashMap<>();
    String key = "item";
    headers.put(TRACE_STATE, String.format("%s=%s", key, STRING_VALUE_630));
    headers.put(TRACE_PARENT, EXAMPLE_TRACE_PARENT);
    TraceContextCodec traceContextCodec = new Builder().build();
    JaegerSpanContext extractedContext = traceContextCodec.extract(new TextMapAdapter(headers));
    assertEquals(1, extractedContext.baggageCount());
    assertEquals(STRING_VALUE_630, extractedContext.getBaggageItem(key));
  }

  @Test
  public void testExtractTraceState33Elements() {
    Map<String, String> invalidHeaders = new HashMap<>();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 33; i++) {
      if (sb.length() != 0) {
        sb.append(",");
      }
      sb.append(String.format("item%d=%d", i, i));
    }
    invalidHeaders.put(TRACE_STATE, sb.toString());
    invalidHeaders.put(TRACE_PARENT, EXAMPLE_TRACE_PARENT);
    TraceContextCodec traceContextCodec = new Builder().build();
    JaegerSpanContext extractedContext = traceContextCodec.extract(new TextMapAdapter(invalidHeaders));
    assertEquals(0, extractedContext.baggageCount());
  }

  @Test
  public void testInjectTraceState33Elements() {
    JaegerSpanContext jaegerSpanContext = SPAN_CONTEXT.withBaggageItem("item", STRING_VALUE_630);
    TraceContextCodec traceContextCodec = new Builder().build();
    Map<String, String> carrier = new HashMap<>();
    traceContextCodec.inject(jaegerSpanContext, new TextMapAdapter(carrier));
    assertEquals(2, carrier.size());
    assertEquals(String.format("%s=%s", "item", STRING_VALUE_630), carrier.get(TRACE_STATE));
  }
}
