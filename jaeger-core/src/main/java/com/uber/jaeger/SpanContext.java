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

import com.uber.jaeger.exceptions.EmptyTracerStateStringException;
import com.uber.jaeger.exceptions.MalformedTracerStateStringException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SpanContext implements io.opentracing.SpanContext {
  protected static final byte flagSampled = 1;
  protected static final byte flagDebug = 2;

  private final long traceID;
  private final long spanID;
  private final long parentID;
  private final byte flags;
  private final Map<String, String> baggage;
  private final String debugID;

  public SpanContext(long traceID, long spanID, long parentID, byte flags) {
    this(traceID, spanID, parentID, flags, Collections.<String, String>emptyMap(), null);
  }

  SpanContext(
      long traceID,
      long spanID,
      long parentID,
      byte flags,
      Map<String, String> baggage,
      String debugID) {
    if (baggage == null) {
      throw new NullPointerException();
    }
    this.traceID = traceID;
    this.spanID = spanID;
    this.parentID = parentID;
    this.flags = flags;
    this.baggage = baggage;
    this.debugID = debugID;
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return baggage.entrySet();
  }

  public String getBaggageItem(String key) {
    return this.baggage.get(key);
  }

  Map<String, String> baggage() {
    return this.baggage;
  }

  public long getTraceID() {
    return traceID;
  }

  public long getSpanID() {
    return spanID;
  }

  public long getParentID() {
    return parentID;
  }

  public byte getFlags() {
    return flags;
  }

  public boolean isSampled() {
    return (flags & flagSampled) == flagSampled;
  }

  public boolean isDebug() {
    return (flags & flagDebug) == flagDebug;
  }

  public String contextAsString() {
    // TODO(oibe) profile, and make sure this is fast enough
    return String.format("%x:%x:%x:%x", traceID, spanID, parentID, flags);
  }

  @Override
  public String toString() {
    return contextAsString();
  }

  public static SpanContext contextFromString(String value)
      throws MalformedTracerStateStringException, EmptyTracerStateStringException {
    if (value == null || value.equals("")) {
      throw new EmptyTracerStateStringException();
    }

    String[] parts = value.split(":");
    if (parts.length != 4) {
      throw new MalformedTracerStateStringException(value);
    }

    /*
      TODO(oibe) because java doesn't like to convert large hex strings to longs
      we should write this manually instead of using BigInteger.
    */
    return new SpanContext(
        new BigInteger(parts[0], 16).longValue(),
        new BigInteger(parts[1], 16).longValue(),
        new BigInteger(parts[2], 16).longValue(),
        new BigInteger(parts[3], 16).byteValue());
  }

  public SpanContext withBaggageItem(String key, String val) {
    Map<String, String> newBaggage = new HashMap<String, String>(this.baggage);
    newBaggage.put(key, val);
    return new SpanContext(traceID, spanID, parentID, flags, newBaggage, debugID);
  }

  public SpanContext withBaggage(Map<String, String> newBaggage) {
    return new SpanContext(traceID, spanID, parentID, flags, newBaggage, debugID);
  }

  public SpanContext withFlags(byte flags) {
    return new SpanContext(traceID, spanID, parentID, flags, baggage, debugID);
  }

  /**
   * @return true when the instance of the context is only used to return the debug/correlation ID
   *     from extract() method. This happens in the situation when "jaeger-debug-id" header is
   *     passed in the carrier to the extract() method, but the request otherwise has no span
   *     context in it. Previously this would've returned null from the extract method, but now it
   *     returns a dummy context with only debugID filled in.
   * @see Constants#DEBUG_ID_HEADER_KEY
   */
  boolean isDebugIDContainerOnly() {
    return traceID == 0 && debugID != null;
  }

  /**
   * Create a new dummy SpanContext as a container for debugID string. This is used when
   * "jaeger-debug-id" header is passed in the request headers and forces the trace to be sampled as
   * debug trace, and the value of header recorded as a span tag to serve as a searchable
   * correlation ID.
   *
   * @param debugID arbitrary string used as correlation ID
   * @return new dummy SpanContext that serves as a container for debugID only.
   * @see Constants#DEBUG_ID_HEADER_KEY
   */
  public static SpanContext withDebugID(String debugID) {
    return new SpanContext(0, 0, 0, (byte) 0, Collections.<String, String>emptyMap(), debugID);
  }

  String getDebugID() {
    return debugID;
  }
}
