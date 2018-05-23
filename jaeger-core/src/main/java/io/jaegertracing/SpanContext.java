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

package io.jaegertracing;

import io.jaegertracing.exceptions.EmptyTracerStateStringException;
import io.jaegertracing.exceptions.MalformedTracerStateStringException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SpanContext implements io.opentracing.SpanContext {
  protected static final byte flagSampled = 1;
  protected static final byte flagDebug = 2;

  private final long traceId;
  private final long spanId;
  private final long parentId;
  private final byte flags;
  private final Map<String, String> baggage;
  private final String debugId;

  public SpanContext(long traceId, long spanId, long parentId, byte flags) {
    this(traceId, spanId, parentId, flags, Collections.<String, String>emptyMap(), null);
  }

  SpanContext(
      long traceId,
      long spanId,
      long parentId,
      byte flags,
      Map<String, String> baggage,
      String debugId) {
    if (baggage == null) {
      throw new NullPointerException();
    }
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentId = parentId;
    this.flags = flags;
    this.baggage = baggage;
    this.debugId = debugId;
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return new HashMap<String, String>(baggage).entrySet();
  }

  public String getBaggageItem(String key) {
    return this.baggage.get(key);
  }

  Map<String, String> baggage() {
    return this.baggage;
  }

  public long getTraceId() {
    return traceId;
  }

  public long getSpanId() {
    return spanId;
  }

  public long getParentId() {
    return parentId;
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
    int intFlag = flags & 0xFF;
    return new StringBuilder()
        .append(Long.toHexString(traceId)).append(":")
        .append(Long.toHexString(spanId)).append(":")
        .append(Long.toHexString(parentId)).append(":")
        .append(Integer.toHexString(intFlag))
        .toString();
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
    if (val == null) {
      newBaggage.remove(key);
    } else {
      newBaggage.put(key, val);
    }
    return new SpanContext(traceId, spanId, parentId, flags, newBaggage, debugId);
  }

  public SpanContext withBaggage(Map<String, String> newBaggage) {
    return new SpanContext(traceId, spanId, parentId, flags, newBaggage, debugId);
  }

  public SpanContext withFlags(byte flags) {
    return new SpanContext(traceId, spanId, parentId, flags, baggage, debugId);
  }

  /**
   * @return true when the instance of the context is only used to return the debug/correlation ID
   * from extract() method. This happens in the situation when "jaeger-debug-id" header is passed in
   * the carrier to the extract() method, but the request otherwise has no span context in it.
   * Previously this would've returned null from the extract method, but now it returns a dummy
   * context with only debugId filled in.
   *
   * @see Constants#DEBUG_ID_HEADER_KEY
   */
  boolean isDebugIdContainerOnly() {
    return traceId == 0 && debugId != null;
  }

  /**
   * Create a new dummy SpanContext as a container for debugId string. This is used when
   * "jaeger-debug-id" header is passed in the request headers and forces the trace to be sampled as
   * debug trace, and the value of header recorded as a span tag to serve as a searchable
   * correlation ID.
   *
   * @param debugId arbitrary string used as correlation ID
   *
   * @return new dummy SpanContext that serves as a container for debugId only.
   *
   * @see Constants#DEBUG_ID_HEADER_KEY
   */
  public static SpanContext withDebugId(String debugId) {
    return new SpanContext(0, 0, 0, (byte) 0, Collections.<String, String>emptyMap(), debugId);
  }

  String getDebugId() {
    return debugId;
  }
}
