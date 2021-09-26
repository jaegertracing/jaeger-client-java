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

package io.jaegertracing.internal.propagation;

import io.jaegertracing.internal.Constants;
import io.jaegertracing.internal.JaegerObjectFactory;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.exceptions.EmptyTracerStateStringException;
import io.jaegertracing.internal.exceptions.MalformedTracerStateStringException;
import io.jaegertracing.internal.exceptions.TraceIdOutOfBoundException;
import io.jaegertracing.internal.utils.Utils;
import io.jaegertracing.spi.Codec;
import io.opentracing.propagation.TextMap;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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

  /**
   * Object factory used to construct JaegerSpanContext subclass instances.
   */
  private final JaegerObjectFactory objectFactory;

  public TextMapCodec(boolean urlEncoding) {
    this(builder().withUrlEncoding(urlEncoding));
  }

  private TextMapCodec(Builder builder) {
    this.urlEncoding = builder.urlEncoding;
    this.contextKey = builder.spanContextKey;
    this.baggagePrefix = builder.baggagePrefix;
    this.objectFactory = builder.objectFactory;
  }

  static JaegerSpanContext contextFromString(String value)
      throws MalformedTracerStateStringException, EmptyTracerStateStringException {
    if (value == null || value.isEmpty()) {
      throw new EmptyTracerStateStringException();
    }

    final int traceIdEnd = value.indexOf(':');
    if (traceIdEnd == -1) {
      throw new MalformedTracerStateStringException(value);
    }

    if (traceIdEnd > 32 || traceIdEnd == 0) {
      throw new TraceIdOutOfBoundException(
              "Trace id [" + value.substring(0, traceIdEnd) + "] length is not within 1 and 32");
    }

    final int lowTraceIdStart = Math.max(0, traceIdEnd - 16);
    final long high = lowTraceIdStart == 0 ? 0 : hexToUnsignedLong("trace ID high part", value, 0, lowTraceIdStart);
    final long low = hexToUnsignedLong("trace ID low part", value, lowTraceIdStart, traceIdEnd);

    final int spanIdEnd = value.indexOf(':', traceIdEnd + 1);
    if (spanIdEnd == -1) {
      throw new MalformedTracerStateStringException(value);
    }

    final long spanId = hexToUnsignedLong("span ID", value, traceIdEnd + 1, spanIdEnd);

    final int parentIdEnd = value.indexOf(':', spanIdEnd + 1);
    if (parentIdEnd == -1) {
      throw new MalformedTracerStateStringException(value);
    }

    final long parentId = hexToUnsignedLong("parent ID", value, spanIdEnd + 1, parentIdEnd);

    final byte flags = (byte)hexToUnsignedLong("flags", value, parentIdEnd + 1, value.length());

    return new JaegerSpanContext(
        high,
        low,
        spanId,
        parentId,
        flags);
  }

  // TODO(amirhadadi):
  // When supporting Java >= 9 use Long.parseUnsignedLong(CharSequence s, int beginIndex, int endIndex, int radix)
  // which allows avoiding creating a String.
  private static long hexToUnsignedLong(String label, String value, int beginIndex, int endIndex) {
    if (beginIndex >= endIndex) {
      throw new MalformedTracerStateStringException("Empty " + label + " in context string " + value);
    }
    long result = 0;
    for (; beginIndex < endIndex; beginIndex++) {
      char c = value.charAt(beginIndex);
      result <<= 4;
      if (c >= '0' && c <= '9') {
        result |= c - '0';
      } else if (c >= 'a' && c <= 'f') {
        result |= c - 'a' + 10;
      } else {
        throw new MalformedTracerStateStringException("Failed to parse " + label + " in context string " + value
                +  ", '" + c + "' is not a legal hex character expecting only 0-9 and a-f");
      }
    }
    return result;
  }

  /**
   * Encode context into a string.
   * @param context Span context to encode.
   * @return Encoded string representing span context.
   */
  public static String contextAsString(JaegerSpanContext context) {
    return context.getTraceId() + ":"
            + context.toSpanId() + ":"
            // parent=0 is special, no need to encode as full 16 characters, and more readable this way
            + (context.getParentId() == 0 ? "0" : Utils.to16HexString(context.getParentId())) + ":"
            + Integer.toHexString(context.getFlags() & 0xFF);
  }

  @Override
  public void inject(JaegerSpanContext spanContext, TextMap carrier) {
    carrier.put(contextKey, contextAsString(spanContext));
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
      String key = entry.getKey();
      if (key.equalsIgnoreCase(contextKey)) {
        context = contextFromString(decodedValue(entry.getValue()));
      } else if (key.equalsIgnoreCase(Constants.DEBUG_ID_HEADER_KEY)) {
        debugId = decodedValue(entry.getValue());
      } else if (key.regionMatches(true, 0, baggagePrefix, 0, baggagePrefix.length())) {
        if (baggage == null) {
          baggage = new HashMap<>();
        }
        baggage.put(keys.unprefixedKey(key.toLowerCase(Locale.ROOT), baggagePrefix), decodedValue(entry.getValue()));
      } else if (key.equalsIgnoreCase(Constants.BAGGAGE_HEADER_KEY)) {
        baggage = parseBaggageHeader(decodedValue(entry.getValue()), baggage);
      }
    }
    if (debugId == null && baggage == null) {
      return context;
    }
    return objectFactory.createSpanContext(
      context == null ? 0L : context.getTraceIdHigh(),
      context == null ? 0L : context.getTraceIdLow(),
      context == null ? 0L : context.getSpanId(),
      context == null ? 0L : context.getParentId(),
      context == null ? (byte)0 : context.getFlags(),
      baggage,
      debugId);
  }

  private Map<String, String> parseBaggageHeader(String header, Map<String, String> baggage) {
    for (String part : header.split("\\s*,\\s*")) {
      String[] kv = part.split("\\s*=\\s*");
      if (kv.length == 2) {
        if (baggage == null) {
          baggage = new HashMap<String, String>();
        }
        baggage.put(kv[0], kv[1]);
      } else {
        log.debug("malformed token in {} header: {}", Constants.BAGGAGE_HEADER_KEY, part);
      }
    }
    return baggage;
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
    private JaegerObjectFactory objectFactory = new JaegerObjectFactory();

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

    /**
     * Set object factory to use for construction of JaegerSpanContext subclass instances.
     *
     * @param objectFactory JaegerObjectFactory subclass instance.
     */
    public Builder withObjectFactory(JaegerObjectFactory objectFactory) {
      this.objectFactory = objectFactory;
      return this;
    }

    public TextMapCodec build() {
      return new TextMapCodec(this);
    }
  }
}
