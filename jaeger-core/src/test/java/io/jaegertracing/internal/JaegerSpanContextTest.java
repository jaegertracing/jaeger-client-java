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

package io.jaegertracing.internal;

import static org.junit.Assert.assertEquals;

import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.exceptions.EmptyTracerStateStringException;
import io.jaegertracing.internal.exceptions.MalformedTracerStateStringException;
import org.junit.Test;

public class JaegerSpanContextTest {

  @Test(expected = MalformedTracerStateStringException.class)
  public void testContextFromStringMalformedException() throws Exception {
    JaegerSpanContext.contextFromString("ff:ff:ff");
  }

  @Test(expected = EmptyTracerStateStringException.class)
  public void testContextFromStringEmptyException() throws Exception {
    JaegerSpanContext.contextFromString("");
  }

  @Test
  public void testContextFromString() throws Exception {
    JaegerSpanContext context = JaegerSpanContext.contextFromString("ff:dd:cc:4");
    assertEquals(context.getTraceId(), 255);
    assertEquals(context.getSpanId(), 221);
    assertEquals(context.getParentId(), 204);
    assertEquals(context.getFlags(), 4);
  }

  @Test
  public void testToStringFormatsPositiveFields() {
    long traceId = -10L;
    long spanId = -10L;
    long parentId = -10L;
    byte flags = (byte) 129;

    // I use MIN_VALUE because the most significant bit, and thats when
    // we want to make sure the hex number is positive.
    JaegerSpanContext context = new JaegerSpanContext(traceId, spanId, parentId, flags);

    context.contextAsString().split(":");

    assertEquals(
        "fffffffffffffff6:fffffffffffffff6:fffffffffffffff6:81", context.contextAsString());
    JaegerSpanContext contextFromStr = JaegerSpanContext.contextFromString(context.contextAsString());
    assertEquals(traceId, contextFromStr.getTraceId());
    assertEquals(spanId, contextFromStr.getSpanId());
    assertEquals(parentId, contextFromStr.getParentId());
    assertEquals(flags, contextFromStr.getFlags());
  }
}
