/*
 * Copyright (c) 2019, The Jaeger Authors
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
import static org.junit.Assert.fail;

import io.jaegertracing.internal.JaegerSpanContext;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class BinaryCodecTest {

  @Test
  public void testBuilder()  {
    BinaryCodec codec = BinaryCodec.builder()
        .build();
    assertNotNull(codec);
  }

  @Test
  public void testWithoutBuilder() {
    BinaryCodec codec = new BinaryCodec();
    String str = codec.toString();
    assertTrue(str.contains("BinaryCodec"));
  }

  /**
   * Tests that the codec will include baggage from header "jaeger-baggage".
   */
  @Test
  public void testContextFieldsWithNoBaggage() {
    final long traceIdLow = 42;
    final long traceIdHigh = 2;
    final long spanId = 1;
    final long parentId = 22;
    final byte flags = (byte)1;
    BinaryCodec codec = new BinaryCodec();
    TestBinaryCarrier carrier = new TestBinaryCarrier();
    codec.inject(new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, flags), carrier);
    JaegerSpanContext context = codec.extract(carrier);
    assertTrue(carrier.buffer.remaining() == 0);
    assertEquals("must have trace ID low", traceIdLow, context.getTraceIdLow());
    assertEquals("must have trace ID high", traceIdHigh, context.getTraceIdHigh());
    assertEquals("must have span ID", spanId, context.getSpanId());
    assertEquals("must have parent ID", parentId, context.getParentId());
    assertEquals("must have flags", flags, context.getFlags());
  }

  @Test
  public void testInvalidByteOrder() {
    BinaryCodec codec = new BinaryCodec();
    ByteBuffer buf = ByteBuffer.allocate(128);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    TestBinaryCarrier carrier = new TestBinaryCarrier(buf);
    try {
      codec.inject(new JaegerSpanContext(0L, 0L, 0L, 0L, (byte) 0), carrier);
      fail("Exception not thrown.");
    } catch (IllegalStateException expected) {
      assertEquals("Carrier byte order must be big endian.", expected.getMessage());
    }
    try  {
      codec.extract(carrier);
      fail("Exception not thrown.");
    } catch (IllegalStateException expected) {
      assertEquals("Carrier byte order must be big endian.", expected.getMessage());
    }
  }

  /**
   * Tests that the codec will return non-null SpanContext even if the only header
   * present is "jaeger-baggage".
   */
  @Test
  public void testBaggage() {
    Map<String, String> baggage = new HashMap<String, String>();
    for (int i = 0; i < 200; i++) {
      baggage.put("k" + i, "v" + i);
    }

    BinaryCodec codec = new BinaryCodec();
    JaegerSpanContext inContext = new JaegerSpanContext(0L, 42L, 1L, 1L, (byte)1)
        .withBaggage(baggage);

    TestBinaryCarrier carrier = new TestBinaryCarrier();
    codec.inject(inContext, carrier);

    // check with a new carrier just to make sure testing is accurate.
    byte[] raw = new byte[carrier.buffer.capacity()];
    carrier.buffer.rewind();
    carrier.buffer.get(raw);
    TestBinaryCarrier carrier2 = new TestBinaryCarrier(ByteBuffer.wrap(raw));

    JaegerSpanContext outContext = codec.extract(carrier2);
    assertTrue(carrier2.buffer.remaining() == 0);
    for (int i = 0; i < 200; i++) {
      assertEquals("v" + i, outContext.getBaggageItem("k" + i));
    }
  }

  @Test
  public void testBaggageWithLargeValues() {
    String key1 = "";
    String val1 = "";
    for (int i = 0; i < 256; i++) {
      key1 += "A";
      val1 += "B";
    }

    String key2 = "";
    String val2 = "";
    for (int i = 0; i < 1024; i++) {
      key2 += "C";
      val2 += "D";
    }

    Map<String, String> baggage = new HashMap<String, String>();
    baggage.put(key1, val1);
    baggage.put(key2, val2);

    JaegerSpanContext inContext = new JaegerSpanContext(0L, 1L, 1L, 1L, (byte)1)
        .withBaggage(baggage);

    BinaryCodec codec = new BinaryCodec();
    TestBinaryCarrier carrier = new TestBinaryCarrier();
    codec.inject(inContext, carrier);

    JaegerSpanContext outContext = codec.extract(carrier);
    assertEquals(val1, outContext.getBaggageItem(key1));
    assertEquals(val2, outContext.getBaggageItem(key2));
  }
}
