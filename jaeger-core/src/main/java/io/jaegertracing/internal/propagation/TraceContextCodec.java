/*
 * Copyright (c) 2019, The Jaeger Authors
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
import io.jaegertracing.internal.utils.Utils;
import io.jaegertracing.spi.Codec;
import io.opentracing.propagation.TextMap;

import java.util.Collections;
import java.util.Map;


/**
 * This format follows the Trace Context specification  https://www.w3.org/TR/trace-context/
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * traceContextCodec = new TraceContextCodec();
 * tracer = new JaegerTracer.Builder(serviceName, reporter, sampler)
 *                    .registerInjector(Format.Builtin.HTTP_HEADERS, traceContextCodec)
 *                    .registerExtractor(Format.Builtin.HTTP_HEADERS, traceContextCodec)
 *                    ...
 * }</pre>
 *
 * <p>
 */
public class TraceContextCodec implements Codec<TextMap> {
  protected static final String TRACE_CONTEXT_NAME = "traceparent";

  private static final int VERSION_SIZE = 2;
  private static final int TRACEPARENT_DELIMITER_SIZE = 1;
  private static final int TRACE_ID_HEX_SIZE = 32;
  private static final int SPAN_ID_HEX_SIZE = 16;
  private static final int TRACE_ID_OFFSET = VERSION_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int SPAN_ID_OFFSET =
      TRACE_ID_OFFSET + TRACE_ID_HEX_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int TRACE_OPTION_OFFSET =
      SPAN_ID_OFFSET + SPAN_ID_HEX_SIZE + TRACEPARENT_DELIMITER_SIZE;
  private static final int SAMPLED_FLAG = 1;

  private static final byte JAEGER_SAMPLED_FLAG = 1;


  private final JaegerObjectFactory objectFactory;
  private final Long version;

  private TraceContextCodec(Builder builder) {
    this.objectFactory = builder.objectFactory;
    this.version = builder.version;
  }

  @Override
  public void inject(JaegerSpanContext spanContext, TextMap carrier) {
    carrier.put(TRACE_CONTEXT_NAME, String.format("%02d-%s-%s-%s",
        version,
        HexCodec.toLowerHexWithLength32(spanContext.getTraceIdHigh(), spanContext.getTraceIdLow()),
        HexCodec.toLowerHex(spanContext.getSpanId()),
        spanContext.isSampled() ? "01" : "00"
    ));
  }

  private boolean isValidId(Long id) {
    return id != null && id != 0;
  }

  @Override
  public JaegerSpanContext extract(TextMap carrier) {
    Long traceIdLow = 0L;
    Long traceIdHigh = 0L;
    Long spanId = Utils.uniqueId();
    Long parentId = 0L; // Conventionally, parent id == 0 means the root span
    byte flags = 0;
    for (Map.Entry<String, String> entry : carrier) {
      if (entry.getKey().equalsIgnoreCase(TRACE_CONTEXT_NAME)) {
        String value = entry.getValue();
        traceIdHigh = HexCodec.hexToUnsignedLong(value, TRACE_ID_OFFSET, TRACE_ID_OFFSET + 16);
        traceIdLow = HexCodec.hexToUnsignedLong(value, TRACE_ID_OFFSET + 16, TRACE_ID_OFFSET + 32);
        parentId = HexCodec.hexToUnsignedLong(value, SPAN_ID_OFFSET, SPAN_ID_OFFSET + 16);
        long traceContextFlags = HexCodec.hexToUnsignedLong(value, TRACE_OPTION_OFFSET, TRACE_OPTION_OFFSET + 2);

        if ((traceContextFlags & SAMPLED_FLAG) == SAMPLED_FLAG) {
          flags |= JAEGER_SAMPLED_FLAG;
        }
      }
    }

    // Follow Trace Context specification
    // If the trace-id or parent-id value is invalid (for example if it contains non-allowed characters or all zeros),
    // we MUST ignore the traceparent per the specification.
    if (isValidId(traceIdLow)
        && isValidId(parentId)) {
      JaegerSpanContext spanContext = objectFactory.createSpanContext(
          traceIdHigh,
          traceIdLow,
          spanId,
          parentId,
          flags,
          Collections.<String, String>emptyMap(),
          null // debugId
      );
      return spanContext;
    }
    return null;
  }

  public static class Builder {
    private JaegerObjectFactory objectFactory = new JaegerObjectFactory();
    private Long version = 0L;

    /**
     * Specify JaegerSpanContext factory. Used for creating new span contexts. The default factory
     * is an instance of {@link JaegerObjectFactory}.
     */
    public Builder withObjectFactory(JaegerObjectFactory objectFactory) {
      this.objectFactory = objectFactory;
      return this;
    }

    public Builder withVersion(Long version) {
      this.version = version;
      return this;
    }

    public TraceContextCodec build() {
      return new TraceContextCodec(this);
    }
  }
}
