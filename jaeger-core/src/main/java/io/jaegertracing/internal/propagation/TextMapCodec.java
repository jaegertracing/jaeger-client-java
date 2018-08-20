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

package io.jaegertracing.internal.propagation;

import io.jaegertracing.internal.Constants;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.exceptions.EmptyTracerStateStringException;
import io.jaegertracing.internal.exceptions.MalformedTracerStateStringException;
import io.jaegertracing.spi.Codec;
import io.opentracing.propagation.TextMap;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TextMapCodec implements Codec<TextMap> {
  /**
   * Key used to store serialized span context representation
   */
  private static final String SPAN_CONTEXT_KEY = "uber-trace-id";

  /**
   * Key prefix used for baggage items
   */
  private static final String BAGGAGE_KEY_PREFIX = "uberctx-";

  private static final PrefixedKeys keys = new PrefixedKeys();

  private final String contextKey;

  private final String baggagePrefix;

  private final boolean urlEncoding;

  public TextMapCodec(boolean urlEncoding) {
    this(builder().withUrlEncoding(urlEncoding));
  }

  private TextMapCodec(Builder builder) {
    this.urlEncoding = builder.urlEncoding;
    this.contextKey = builder.spanContextKey;
    this.baggagePrefix = builder.baggagePrefix;
  }

  static JaegerSpanContext contextFromString(String value)
      throws MalformedTracerStateStringException, EmptyTracerStateStringException {
    if (value == null || value.equals("")) {
      throw new EmptyTracerStateStringException();
    }

    String[] parts = value.split(":");
    if (parts.length != 4) {
      throw new MalformedTracerStateStringException(value);
    }

    // TODO(isaachier): When we drop Java 1.6 support, use Long.parseUnsignedLong instead of using BigInteger.
    return new JaegerSpanContext(
        new BigInteger(parts[0], 16).longValue(),
        new BigInteger(parts[1], 16).longValue(),
        new BigInteger(parts[2], 16).longValue(),
        new BigInteger(parts[3], 16).byteValue());
  }

  /**
   * Encode context into a string.
   * @param context Span context to encode.
   * @return Encoded string representing span context.
   */
  public static String contextAsString(JaegerSpanContext context) {
    int intFlag = context.getFlags() & 0xFF;
    return new StringBuilder()
        .append(Long.toHexString(context.getTraceId())).append(":")
        .append(Long.toHexString(context.getSpanId())).append(":")
        .append(Long.toHexString(context.getParentId())).append(":")
        .append(Integer.toHexString(intFlag))
        .toString();
  }

  @Override
  public void inject(JaegerSpanContext spanContext, TextMap carrier) {
    carrier.put(contextKey, encodedValue(contextAsString(spanContext)));
    for (Map.Entry<String, String> entry : spanContext.baggageItems()) {
      carrier.put(keys.prefixedKey(entry.getKey(), baggagePrefix), encodedValue(entry.getValue()));
    }
  }

  @Override
  public JaegerSpanContext extract(TextMap carrier) {
    JaegerSpanContext context = null;
    Map<String, String> baggage = null;
    String debugId = null;
    for (Map.Entry<String, String> entry : carrier) {
      // TODO there should be no lower-case here
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      if (key.equals(contextKey)) {
        context = contextFromString(decodedValue(entry.getValue()));
      } else if (key.equals(Constants.DEBUG_ID_HEADER_KEY)) {
        debugId = decodedValue(entry.getValue());
      } else if (key.startsWith(baggagePrefix)) {
        if (baggage == null) {
          baggage = new HashMap<String, String>();
        }
        baggage.put(keys.unprefixedKey(key, baggagePrefix), decodedValue(entry.getValue()));
      }
    }
    if (context == null) {
      if (debugId != null) {
        return JaegerSpanContext.withDebugId(debugId);
      }
      return null;
    }
    if (baggage == null) {
      return context;
    }
    return context.withBaggage(baggage);
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

    public TextMapCodec build() {
      return new TextMapCodec(this);
    }
  }
}
