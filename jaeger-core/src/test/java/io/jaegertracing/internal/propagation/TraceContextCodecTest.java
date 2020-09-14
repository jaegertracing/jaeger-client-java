/*
 * Copyright (c) 2020, Uber Technologies, Inc
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
import static io.jaegertracing.internal.propagation.TraceContextCodec.TRACE_STATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.jaegertracing.internal.Constants;
import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.opentracing.propagation.TextMapAdapter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class TraceContextCodecTest {

  private static final JaegerSpanContext SPAN_CONTEXT =
      new JaegerSpanContext(0, 1, 2, 3, (byte)0);
  private static final String EXAMPLE_TRACE_PARENT = "00-00000000000000000000000000000001-0000000000000002-00";
  private static final String EXAMPLE_DEBUG_ID = "123456789";
  private static PrintStream sysout;

  private TraceContextCodec traceContextCodec = new TraceContextCodec.Builder().build();

  @BeforeClass
  public static void setup() {
    sysout = System.out;
    System.setOut(Mockito.spy(sysout));
  }

  @AfterClass
  public static void cleanup() {
    System.setOut(sysout);
  }

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
    assertEquals("0000000000000001:0000000000000002:0000000000000000:0", extractedContext.toString());
  }

  @Test
  public void testExtractWithCapitalizedTraceHeaders() {
    Map<String, String> extractCarrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(extractCarrier);
    textMap.put("Traceparent", EXAMPLE_TRACE_PARENT);
    textMap.put("Tracestate", "whatever");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertEquals("0000000000000001:0000000000000002:0000000000000000:0", spanContext.toString());
    assertEquals("whatever", spanContext.getTraceState());
  }

  @Test
  public void testInvalidTraceId() {
    TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
    textMap.put(TRACE_PARENT, "00-00000000000000000000000000000000-0000000000000002-00");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
    verifyWarningPresent();
  }

  @Test
  public void testNoTraceHeader() {
    TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
    verifyWarningNotPresent();
  }

  @Test
  public void testInvalidParentId() {
    TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
    textMap.put(TRACE_PARENT, "00-00000000000000000000000000000001-0000000000000000-00");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    assertNull(spanContext);
    verifyWarningPresent();
  }

  @Test
  public void testTraceStatePropagation() {
    Map<String, String> extractCarrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(extractCarrier);
    textMap.put(TRACE_PARENT, EXAMPLE_TRACE_PARENT);
    textMap.put(TRACE_STATE, "whatever");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);

    Map<String, String> injectCarrier = new HashMap<>();
    traceContextCodec.inject(spanContext, new TextMapAdapter(injectCarrier));
    assertEquals(extractCarrier, injectCarrier);
  }

  @Test
  public void testEmptyTraceStateNotPropagated() {
    Map<String, String> extractCarrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(extractCarrier);
    textMap.put(TRACE_PARENT, EXAMPLE_TRACE_PARENT);
    textMap.put(TRACE_STATE, "");
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);

    Map<String, String> injectCarrier = new HashMap<>();
    traceContextCodec.inject(spanContext, new TextMapAdapter(injectCarrier));
    assertEquals(1, injectCarrier.size());
    assertEquals(EXAMPLE_TRACE_PARENT, injectCarrier.get(TRACE_PARENT));
  }

  @Test
  public void testDebugIdWithoutTraceHeader() {
    Map<String, String> extractCarrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(extractCarrier);
    textMap.put(Constants.DEBUG_ID_HEADER_KEY, EXAMPLE_DEBUG_ID);
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    JaegerTracer tracer = new JaegerTracer.Builder("service").withReporter(new InMemoryReporter()).build();
    JaegerSpan child = tracer.buildSpan("span").asChildOf(spanContext).start();
    assertTrue(child.context().isDebug());
    child.finish();
    tracer.close();
  }

  @Test
  public void testDebugIdWithTraceHeader() {
    Map<String, String> extractCarrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(extractCarrier);
    textMap.put(TRACE_PARENT, EXAMPLE_TRACE_PARENT);
    textMap.put(Constants.DEBUG_ID_HEADER_KEY, EXAMPLE_DEBUG_ID);
    JaegerSpanContext spanContext = traceContextCodec.extract(textMap);
    JaegerTracer tracer = new JaegerTracer.Builder("service").withReporter(new InMemoryReporter()).build();
    assertEquals("0000000000000001", spanContext.getTraceId());
    JaegerSpan child = tracer.buildSpan("span").asChildOf(spanContext).start();
    assertFalse(child.context().isDebug());
    child.finish();
    tracer.close();
  }

  private void verifyWarningPresent() {
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    try {
      verify(System.out, atLeast(0)).write(captor.capture(), anyInt(), anyInt());
      // gradle will call write(byte[]) instead of write(byte[],off,len)
      verify(System.out, atLeast(0)).write(captor.capture());
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      reset(System.out);
    }
    String message = new String(captor.getValue(), StandardCharsets.UTF_8);
    assertTrue(message.contains("Unparseable traceparent header."));
  }

  private void verifyWarningNotPresent() {
    try {
      verify(System.out, times(0)).write(any(), anyInt(), anyInt());
      verify(System.out, times(0)).write(any());
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      reset(System.out);
    }
  }
}
