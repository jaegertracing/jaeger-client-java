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

package com.uber.jaeger.propagation;

import com.uber.jaeger.SpanContext;
import io.opentracing.propagation.TextMap;
import java.util.Map;

/**
 * This format is compatible with other trace libraries such as Brave, Wingtips, zipkin-js, etc.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * b3Codec = new B3TextMapCodec();
 * tracer = new Tracer.Builder(serviceName, reporter, sampler)
 *                    .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
 *                    .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
 *                    ...
 * }</pre>
 *
 * <p>
 * See <a href="http://zipkin.io/pages/instrumenting.html">Instrumenting a Library</a>
 */
public class B3TextMapCodec implements Codec<TextMap> {
  protected static final String TRACE_ID_NAME = "X-B3-TraceId";
  protected static final String SPAN_ID_NAME = "X-B3-SpanId";
  protected static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
  protected static final String SAMPLED_NAME = "X-B3-Sampled";
  protected static final String FLAGS_NAME = "X-B3-Flags";
  // NOTE: uber's flags aren't the same as B3/Finagle ones
  protected static final byte SAMPLED_FLAG = 1;
  protected static final byte DEBUG_FLAG = 2;

  @Override
  public void inject(SpanContext spanContext, TextMap carrier) {
    carrier.put(TRACE_ID_NAME, HexCodec.toLowerHex(spanContext.getTraceId()));
    if (spanContext.getParentId() != 0L) { // Conventionally, parent id == 0 means the root span
      carrier.put(PARENT_SPAN_ID_NAME, HexCodec.toLowerHex(spanContext.getParentId()));
    }
    carrier.put(SPAN_ID_NAME, HexCodec.toLowerHex(spanContext.getSpanId()));
    carrier.put(SAMPLED_NAME, spanContext.isSampled() ? "1" : "0");
    if (spanContext.isDebug()) {
      carrier.put(FLAGS_NAME, "1");
    }
  }

  @Override
  public SpanContext extract(TextMap carrier) {
    Long traceId = null;
    Long spanId = null;
    long parentId = 0L; // Conventionally, parent id == 0 means the root span
    byte flags = 0;
    for (Map.Entry<String, String> entry : carrier) {
      if (entry.getKey().equalsIgnoreCase(SAMPLED_NAME)) {
        String value = entry.getValue();
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
          flags |= SAMPLED_FLAG;
        }
      } else if (entry.getKey().equalsIgnoreCase(TRACE_ID_NAME)) {
        traceId = HexCodec.lowerHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(PARENT_SPAN_ID_NAME)) {
        parentId = HexCodec.lowerHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(SPAN_ID_NAME)) {
        spanId = HexCodec.lowerHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(FLAGS_NAME)) {
        if (entry.getValue().equals("1")) {
          flags |= DEBUG_FLAG;
        }
      }
    }

    if (traceId != null && spanId != null) {
      return new SpanContext(traceId, spanId, parentId, flags);
    }
    return null;
  }
}
