/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jaegertracing.internal.propagation;

import io.jaegertracing.internal.Constants;
import io.jaegertracing.internal.JaegerObjectFactory;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.spi.Codec;
import io.opentracing.propagation.TextMap;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the TraceContext propagation protocol. See <a
 * href=https://github.com/w3c/distributed-tracing>w3c/distributed-tracing</a>.
 *
 * This implementation is mostly copied over from OpenTelemetry Java SDK
 * https://github.com/open-telemetry/opentelemetry-java/blob/ed98c35c0569a48f66339769913670334d6c8a95/api/src/main/java/io/opentelemetry/trace/propagation/HttpTraceContext.java#L40
 */
@Slf4j
public class TraceContextCodec implements Codec<TextMap> {

  static final String TRACE_PARENT = "traceparent";
  static final String TRACE_STATE = "tracestate";

  private static final String VERSION = "00";
  private static final int VERSION_SIZE = 2;
  private static final char TRACEPARENT_DELIMITER = '-';
  private static final int TRACEPARENT_DELIMITER_SIZE = 1;
  private static final int TRACE_ID_HEX_SIZE = 2 * 16;
  private static final int SPAN_ID_HEX_SIZE = 2 * 8;
  private static final int TRACE_FLAGS_HEX_SIZE = 2;
  private static final int TRACE_ID_OFFSET = VERSION_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int SPAN_ID_OFFSET =
      TRACE_ID_OFFSET + TRACE_ID_HEX_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int TRACE_OPTION_OFFSET =
      SPAN_ID_OFFSET + SPAN_ID_HEX_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int TRACEPARENT_HEADER_SIZE = TRACE_OPTION_OFFSET + TRACE_FLAGS_HEX_SIZE;
  private static final byte SAMPLED_FLAG = 1;

  private final JaegerObjectFactory objectFactory;

  private TraceContextCodec(Builder builder) {
    this.objectFactory = builder.objectFactory;
  }

  private JaegerSpanContext extractContextFromTraceParent(String traceparent, String tracestate, String debugId) {
    // TODO(bdrutu): Do we need to verify that version is hex and that
    // for the version the length is the expected one?
    boolean isValid =
        traceparent != null
        && traceparent.charAt(TRACE_OPTION_OFFSET - 1) == TRACEPARENT_DELIMITER
            && (traceparent.length() == TRACEPARENT_HEADER_SIZE
                || (traceparent.length() > TRACEPARENT_HEADER_SIZE
                    && traceparent.charAt(TRACEPARENT_HEADER_SIZE) == TRACEPARENT_DELIMITER))
            && traceparent.charAt(SPAN_ID_OFFSET - 1) == TRACEPARENT_DELIMITER
            && traceparent.charAt(TRACE_OPTION_OFFSET - 1) == TRACEPARENT_DELIMITER;
    if (!isValid) {
      log.warn("Unparseable traceparent header. Returning null span context.");
      return null;
    }

    Long traceIdHigh = HexCodec.hexToUnsignedLong(traceparent, TRACE_ID_OFFSET, TRACE_ID_OFFSET + 16);
    Long traceIdLow = HexCodec.hexToUnsignedLong(traceparent, TRACE_ID_OFFSET + 16, TRACE_ID_OFFSET + 32);
    Long spanId = HexCodec.hexToUnsignedLong(traceparent, SPAN_ID_OFFSET, SPAN_ID_OFFSET + 16);

    boolean sampled = false;
    long traceContextFlags = HexCodec.hexToUnsignedLong(traceparent, TRACE_OPTION_OFFSET, TRACE_OPTION_OFFSET + 2);
    if ((traceContextFlags & SAMPLED_FLAG) == SAMPLED_FLAG) {
      sampled = true;
    }

    if (traceIdLow == null || traceIdLow == 0 || spanId == null || spanId == 0) {
      log.warn("Unparseable traceparent header. Returning null span context.");
      return null;
    }

    JaegerSpanContext spanContext = this.objectFactory.createSpanContext(
        traceIdHigh,
        traceIdLow,
        spanId,
        0,
        sampled ? (byte) 1 : (byte) 0,
        Collections.<String, String>emptyMap(), debugId);
    return spanContext.withTraceState(tracestate);
  }

  @Override
  public JaegerSpanContext extract(TextMap carrier) {
    String traceParent = null;
    String traceState = null;
    String debugId = null;
    for (Map.Entry<String, String> entry: carrier) {
      if (TRACE_PARENT.equalsIgnoreCase(entry.getKey())) {
        traceParent = entry.getValue();
      }
      if (TRACE_STATE.equalsIgnoreCase(entry.getKey())) {
        traceState = entry.getValue();
      }
      if (Constants.DEBUG_ID_HEADER_KEY.equalsIgnoreCase(entry.getKey())) {
        debugId = entry.getValue();
      }
    }
    if (traceParent == null) {
      if (debugId != null) {
        return objectFactory.createSpanContext(0L, 0L, 0L, 0L, (byte) 0, null, debugId);
      }
      return null;
    }
    return extractContextFromTraceParent(traceParent, traceState, debugId);
  }

  @Override
  public void inject(JaegerSpanContext spanContext, TextMap carrier) {
    char[] chars = new char[TRACEPARENT_HEADER_SIZE];
    chars[0] = VERSION.charAt(0);
    chars[1] = VERSION.charAt(1);
    chars[2] = TRACEPARENT_DELIMITER;
    HexCodec.writeHexLong(chars, TRACE_ID_OFFSET, spanContext.getTraceIdHigh());
    HexCodec.writeHexLong(chars, TRACE_ID_OFFSET + 16, spanContext.getTraceIdLow());
    chars[SPAN_ID_OFFSET - 1] = TRACEPARENT_DELIMITER;
    HexCodec.writeHexLong(chars, SPAN_ID_OFFSET, spanContext.getSpanId());
    chars[TRACE_OPTION_OFFSET - 1] = TRACEPARENT_DELIMITER;
    chars[TRACE_OPTION_OFFSET] = '0';
    chars[TRACE_OPTION_OFFSET + 1] = spanContext.isSampled() ? '1' : '0';
    carrier.put(TRACE_PARENT, new String(chars));

    if (spanContext.getTraceState() != null && !spanContext.getTraceState().isEmpty()) {
      carrier.put(TRACE_STATE, spanContext.getTraceState());
    }
  }

  public static class Builder {
    private JaegerObjectFactory objectFactory = new JaegerObjectFactory();

    /**
     * Specify JaegerSpanContext factory. Used for creating new span contexts. The default factory
     * is an instance of {@link JaegerObjectFactory}.
     */
    public Builder withObjectFactory(JaegerObjectFactory objectFactory) {
      this.objectFactory = objectFactory;
      return this;
    }

    public TraceContextCodec build() {
      return new TraceContextCodec(this);
    }
  }
}
