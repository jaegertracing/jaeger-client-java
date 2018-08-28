/*
 * Copyright (c) 2018, The Jaeger Authors
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

import io.jaegertracing.internal.propagation.TextMapCodec;
import io.opentracing.SpanContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JaegerSpanContext implements SpanContext {
  protected static final byte flagSampled = 1;
  protected static final byte flagDebug = 2;

  private final long traceId;
  private final long spanId;
  private final long parentId;
  private final byte flags;
  private final Map<String, String> baggage;
  private final String debugId;
  private final JaegerObjectFactory objectFactory;

  public JaegerSpanContext(long traceId, long spanId, long parentId, byte flags) {
    this(
        traceId,
        spanId,
        parentId,
        flags,
        Collections.<String, String>emptyMap(),
        null, // debugId
        new JaegerObjectFactory());
  }

  protected JaegerSpanContext(
      long traceId,
      long spanId,
      long parentId,
      byte flags,
      Map<String, String> baggage,
      String debugId,
      JaegerObjectFactory objectFactory) {
    if (baggage == null) {
      baggage = Collections.<String, String>emptyMap();
    }
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentId = parentId;
    this.flags = flags;
    this.baggage = baggage;
    this.debugId = debugId;
    this.objectFactory = objectFactory;
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

  @Override
  public String toString() {
    return TextMapCodec.contextAsString(this);
  }

  public JaegerSpanContext withBaggageItem(String key, String val) {
    Map<String, String> newBaggage = new HashMap<String, String>(this.baggage);
    if (val == null) {
      newBaggage.remove(key);
    } else {
      newBaggage.put(key, val);
    }
    return objectFactory.createSpanContext(traceId, spanId, parentId, flags, newBaggage, debugId);
  }

  public JaegerSpanContext withBaggage(Map<String, String> newBaggage) {
    return objectFactory.createSpanContext(traceId, spanId, parentId, flags, newBaggage, debugId);
  }

  public JaegerSpanContext withFlags(byte flags) {
    return objectFactory.createSpanContext(traceId, spanId, parentId, flags, baggage, debugId);
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
   * debugId is used when "jaeger-debug-id" header is passed in the request headers and forces the
   * trace to be sampled as debug trace, and the value of header recorded as a span tag to serve as
   * a searchable correlation ID.
   *
   * @see Constants#DEBUG_ID_HEADER_KEY
   */
  String getDebugId() {
    return debugId;
  }
}
