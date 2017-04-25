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
package com.uber.jaeger.propagation.b3;

import com.uber.jaeger.SpanContext;
import com.uber.jaeger.propagation.Extractor;
import com.uber.jaeger.propagation.Injector;
import io.opentracing.propagation.TextMap;
import java.util.Map;

/**
 * This format is compatible with other trace libraries such as Brave, Wingtips, zipkin-js, etc.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * b3Codec = new B3TextMapCodec();
 * tracer = new Tracer.Builder(serviceName, reporter, sampler)
 *                    .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
 *                    .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
 *                    ...
 * }</pre>
 *
 * <p>See <a href="http://zipkin.io/pages/instrumenting.html">Instrumenting a Library</a>
 */
public final class B3TextMapCodec implements Injector<TextMap>, Extractor<TextMap> {
  static final String TRACE_ID_NAME = "X-B3-TraceId";
  static final String SPAN_ID_NAME = "X-B3-SpanId";
  static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
  static final String SAMPLED_NAME = "X-B3-Sampled";
  static final String FLAGS_NAME = "X-B3-Flags";
  // NOTE: uber's flags aren't the same as B3/Finagle ones
  static final byte SAMPLED_FLAG = 1;
  static final byte DEBUG_FLAG = 2;

  @Override
  public void inject(SpanContext spanContext, TextMap carrier) {
    carrier.put(TRACE_ID_NAME, HexCodec.toLowerHex(spanContext.getTraceID()));
    if (spanContext.getParentID() != 0L) { // Conventionally, parent id == 0 means the root span
      carrier.put(PARENT_SPAN_ID_NAME, HexCodec.toLowerHex(spanContext.getParentID()));
    }
    carrier.put(SPAN_ID_NAME, HexCodec.toLowerHex(spanContext.getSpanID()));
    carrier.put(SAMPLED_NAME, spanContext.isSampled() ? "1" : "0");
    if (spanContext.isDebug()) {
      carrier.put(FLAGS_NAME, "1");
    }
  }

  @Override
  public SpanContext extract(TextMap carrier) {
    Long traceID = null;
    Long spanID = null;
    long parentID = 0L; // Conventionally, parent id == 0 means the root span
    byte flags = 0;
    for (Map.Entry<String, String> entry : carrier) {
      if (entry.getKey().equalsIgnoreCase(SAMPLED_NAME)) {
        if (entry.getValue().equals("1") || entry.getValue().toLowerCase().equals("true")) {
          flags |= SAMPLED_FLAG;
        }
      } else if (entry.getKey().equalsIgnoreCase(TRACE_ID_NAME)) {
        traceID = HexCodec.lowerHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(PARENT_SPAN_ID_NAME)) {
        parentID = HexCodec.lowerHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(SPAN_ID_NAME)) {
        spanID = HexCodec.lowerHexToUnsignedLong(entry.getValue());
      } else if (entry.getKey().equalsIgnoreCase(FLAGS_NAME)) {
        if (entry.getValue().equals("1")) {
          flags |= DEBUG_FLAG;
        }
      }
    }

    if (traceID != null && spanID != null) {
      return new SpanContext(traceID, spanID, parentID, flags);
    }
    return null;
  }
}
