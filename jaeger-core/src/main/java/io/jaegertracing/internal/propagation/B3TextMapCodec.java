/*
 * Copyright (c) 2018, The Jaeger Authors
 * Copyright (c) 2017, Uber Technologies, Inc
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

import io.jaegertracing.internal.JaegerObjectFactory;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.spi.BaggageRestrictionManager;
import io.jaegertracing.spi.Codec;
import io.opentracing.propagation.TextMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This format is compatible with other Zipkin based trace libraries such as Brave, Wingtips, zipkin-js, etc.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * b3Codec = new B3TextMapCodec();
 * tracer = new JaegerTracer.Builder(serviceName, reporter, sampler)
 *                    .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
 *                    .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
 *                    ...
 * }</pre>
 *
 * <p>
 * See <a href="http://zipkin.io/pages/instrumenting.html">Instrumenting a Library</a>
 *
 * Note that this codec automatically propagates baggage
 * (with {@value io.jaegertracing.internal.propagation.B3TextMapCodec#BAGGAGE_PREFIX} prefix).
 * Baggage whitelisting can be configured in {@link BaggageRestrictionManager} and then
 * passed to {@link JaegerTracer.Builder#baggageRestrictionManager}
 */
public class B3TextMapCodec implements Codec<TextMap> {
  protected static final String TRACE_ID_NAME = "X-B3-TraceId";
  protected static final String SPAN_ID_NAME = "X-B3-SpanId";
  protected static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
  protected static final String SAMPLED_NAME = "X-B3-Sampled";
  protected static final String FLAGS_NAME = "X-B3-Flags";
  protected static final String BAGGAGE_PREFIX = "baggage-";
  // NOTE: uber's flags aren't the same as B3/Finagle ones
  protected static final byte SAMPLED_FLAG = 1;
  protected static final byte DEBUG_FLAG = 2;

  private static final PrefixedKeys keys = new PrefixedKeys();
  private final String baggagePrefix;
  private final JaegerObjectFactory objectFactory;

  /**
   * @deprecated use {@link Builder} instead
   */
  @Deprecated
  public B3TextMapCodec() {
    this(new Builder());
  }

  private B3TextMapCodec(Builder builder) {
    this.baggagePrefix = builder.baggagePrefix;
    this.objectFactory = builder.objectFactory;
  }

  @Override
  public void inject(JaegerSpanContext spanContext, TextMap carrier) {
    carrier.put(TRACE_ID_NAME, // Use HexCode instead of getTraceId to ensure zipkin compatibility
            HexCodec.toLowerHex(spanContext.getTraceIdHigh(), spanContext.getTraceIdLow()));
    if (spanContext.getParentId() != 0L) { // Conventionally, parent id == 0 means the root span
      carrier.put(PARENT_SPAN_ID_NAME, HexCodec.toLowerHex(spanContext.getParentId()));
    }
    carrier.put(SPAN_ID_NAME, HexCodec.toLowerHex(spanContext.getSpanId()));
    carrier.put(SAMPLED_NAME, spanContext.isSampled() ? "1" : "0");
    if (spanContext.isDebug()) {
      carrier.put(FLAGS_NAME, "1");
    }
    for (Map.Entry<String, String> entry : spanContext.baggageItems()) {
      carrier.put(keys.prefixedKey(entry.getKey(), baggagePrefix), entry.getValue());
    }
  }

  @Override
  public JaegerSpanContext extract(TextMap carrier) {
    Long traceIdLow = null;
    Long traceIdHigh = 0L; // It's enough to check for a null low trace id
    Long spanId = null;
    Long parentId = 0L; // Conventionally, parent id == 0 means the root span
    byte flags = 0;
    Map<String, String> baggage = null;
    for (Map.Entry<String, String> entry : carrier) {
      if (entry.getKey().equalsIgnoreCase(SAMPLED_NAME)) {
        String value = entry.getValue();
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
          flags |= SAMPLED_FLAG;
        }
      } else if (entry.getKey().equalsIgnoreCase(TRACE_ID_NAME)) {
        traceIdLow = HexCodec.lowerHexToUnsignedLong(entry.getValue());
        traceIdHigh = HexCodec.higherHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(PARENT_SPAN_ID_NAME)) {
        parentId = HexCodec.lowerHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(SPAN_ID_NAME)) {
        spanId = HexCodec.lowerHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(FLAGS_NAME)) {
        if (entry.getValue().equals("1")) {
          flags |= DEBUG_FLAG;
        }
      } else if (entry.getKey().startsWith(baggagePrefix)) {
        if (baggage == null) {
          baggage = new HashMap<String, String>();
        }
        baggage.put(keys.unprefixedKey(entry.getKey(), baggagePrefix), entry.getValue());
      }
    }

    if (null != traceIdLow && null != parentId && null != spanId) {
      JaegerSpanContext spanContext = objectFactory.createSpanContext(
          traceIdHigh,
          traceIdLow,
          spanId,
          parentId,
          flags,
          Collections.<String, String>emptyMap(),
          null // debugId
          );
      if (baggage != null) {
        spanContext = spanContext.withBaggage(baggage);
      }
      return spanContext;
    }
    return null;
  }

  public static class Builder {
    private String baggagePrefix = BAGGAGE_PREFIX;
    private JaegerObjectFactory objectFactory = new JaegerObjectFactory();

    /**
     * Specify baggage prefix. The default is {@value B3TextMapCodec#BAGGAGE_PREFIX}
     */
    public Builder withBaggagePrefix(String baggagePrefix) {
      this.baggagePrefix = baggagePrefix;
      return this;
    }

    /**
     * Specify JaegerSpanContext factory. Used for creating new span contexts. The default factory
     * is an instance of {@link JaegerObjectFactory}.
     */
    public Builder withObjectFactory(JaegerObjectFactory objectFactory) {
      this.objectFactory = objectFactory;
      return this;
    }

    public B3TextMapCodec build() {
      return new B3TextMapCodec(this);
    }
  }
}
