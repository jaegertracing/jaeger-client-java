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

import com.uber.jaeger.Constants;
import com.uber.jaeger.SpanContext;

import io.opentracing.propagation.TextMap;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TextMapCodec implements Injector<TextMap>, Extractor<TextMap> {
  /**
   * Key used to store serialized span context representation
   */
  private static final String SPAN_CONTEXT_KEY = "uber-trace-id";

  /**
   * Key prefix used for baggage items
   */
  private static final String BAGGAGE_KEY_PREFIX = "uberctx-";

  protected static final String TRACE_ID_NAME = "X-B3-TraceId";
  protected static final String SPAN_ID_NAME = "X-B3-SpanId";
  protected static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
  protected static final String SAMPLED_NAME = "X-B3-Sampled";
  protected static final String FLAGS_NAME = "X-B3-Flags";
  // NOTE: uber's flags aren't the same as B3/Finagle ones
  protected static final byte SAMPLED_FLAG = 1;
  protected static final byte DEBUG_FLAG = 2;

  private static final PrefixedKeys keys = new PrefixedKeys();

  private final String contextKey;

  private final String baggagePrefix;

  private final boolean urlEncoding;

  private final boolean b3;

  public TextMapCodec(boolean urlEncoding) {
    this(builder().withUrlEncoding(urlEncoding));
  }

  private TextMapCodec(Builder builder) {
    this.urlEncoding = builder.urlEncoding;
    this.contextKey = builder.spanContextKey;
    this.baggagePrefix = builder.baggagePrefix;
    this.b3 = builder.b3;
  }

  @Override
  public void inject(SpanContext spanContext, TextMap carrier) {
    carrier.put(contextKey, encodedValue(spanContext.contextAsString()));
    for (Map.Entry<String, String> entry : spanContext.baggageItems()) {
      carrier.put(keys.prefixedKey(entry.getKey(), baggagePrefix), encodedValue(entry.getValue()));
    }
    if (b3) {
      injectB3(spanContext, carrier);
    }
  }

  protected void injectB3(SpanContext spanContext, TextMap carrier) {
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
    SpanContext context = null;
    Map<String, String> baggage = null;
    String debugId = null;
    for (Map.Entry<String, String> entry : carrier) {
      // TODO there should be no lower-case here
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      if (key.equals(contextKey)) {
        context = SpanContext.contextFromString(decodedValue(entry.getValue()));
      } else if (key.equals(Constants.DEBUG_ID_HEADER_KEY)) {
        debugId = decodedValue(entry.getValue());
      } else if (key.startsWith(baggagePrefix)) {
        if (baggage == null) {
          baggage = new HashMap<String, String>();
        }
        baggage.put(keys.unprefixedKey(key, baggagePrefix), decodedValue(entry.getValue()));
      }
    }
    if (context == null && b3) {
      context = extractB3(carrier);
    }
    if (context == null) {
      if (debugId != null) {
        return SpanContext.withDebugId(debugId);
      }
      return null;
    }
    if (baggage == null) {
      return context;
    }
    return context.withBaggage(baggage);
  }

  protected SpanContext extractB3(TextMap carrier) {
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

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer
        .append("TextMapCodec{")
        .append("contextKey=")
        .append(contextKey)
        .append(',')
        .append("baggagePrefix=")
        .append(baggagePrefix)
        .append(',')
        .append("urlEncoding=")
        .append(urlEncoding)
        .append(',')
        .append("b3=")
        .append(b3)
        .append('}');
    return buffer.toString();
  }

  private String encodedValue(String value) {
    if (!urlEncoding) {
      return value;
    }
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // not much we can do, try raw value
      return value;
    }
  }

  private String decodedValue(String value) {
    if (!urlEncoding) {
      return value;
    }
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // not much we can do, try raw value
      return value;
    }
  }

  /**
   * Returns a builder for TextMapCodec.
   *
   * @return Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private boolean urlEncoding;
    private String spanContextKey = SPAN_CONTEXT_KEY;
    private String baggagePrefix = BAGGAGE_KEY_PREFIX;
    private boolean b3 = false;

    public Builder withUrlEncoding(boolean urlEncoding) {
      this.urlEncoding = urlEncoding;
      return this;
    }

    public Builder withSpanContextKey(String spanContextKey) {
      this.spanContextKey = spanContextKey;
      return this;
    }

    public Builder withBaggagePrefix(String baggagePrefix) {
      this.baggagePrefix = baggagePrefix;
      return this;
    }

    public Builder withB3(boolean b3) {
      this.b3 = b3;
      return this;
    }

    public TextMapCodec build() {
      return new TextMapCodec(this);
    }
  }
}
