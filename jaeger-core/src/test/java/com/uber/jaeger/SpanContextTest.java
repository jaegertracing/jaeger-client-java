/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.uber.jaeger;

import static org.junit.Assert.assertEquals;

import com.uber.jaeger.exceptions.EmptyTracerStateStringException;
import com.uber.jaeger.exceptions.MalformedTracerStateStringException;
import org.junit.Test;

public class SpanContextTest {

  @Test(expected = MalformedTracerStateStringException.class)
  public void testContextFromStringMalformedException() throws Exception {
    SpanContext.contextFromString("ff:ff:ff");
  }

  @Test(expected = EmptyTracerStateStringException.class)
  public void testContextFromStringEmptyException() throws Exception {
    SpanContext.contextFromString("");
  }

  @Test
  public void testContextFromString() throws Exception {
    SpanContext context = SpanContext.contextFromString("ff:dd:cc:4");
    assertEquals(context.getTraceId(), 255);
    assertEquals(context.getSpanId(), 221);
    assertEquals(context.getParentId(), 204);
    assertEquals(context.getFlags(), 4);
  }

  @Test
  public void testToStringFormatsPositiveFields() {
    long traceID = -10L;
    long spanID = -10L;
    long parentID = -10L;
    byte flags = (byte) 129;

    // I use MIN_VALUE because the most significant bit, and thats when
    // we want to make sure the hex number is positive.
    SpanContext context = new SpanContext(traceID, spanID, parentID, flags);

    context.contextAsString().split(":");

    assertEquals(
        "fffffffffffffff6:fffffffffffffff6:fffffffffffffff6:81", context.contextAsString());
    SpanContext contextFromStr = SpanContext.contextFromString(context.contextAsString());
    assertEquals(traceID, contextFromStr.getTraceId());
    assertEquals(spanID, contextFromStr.getSpanId());
    assertEquals(parentID, contextFromStr.getParentId());
    assertEquals(flags, contextFromStr.getFlags());
  }
}
