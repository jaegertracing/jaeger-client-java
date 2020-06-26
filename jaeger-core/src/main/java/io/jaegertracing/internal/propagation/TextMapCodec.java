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
import io.jaegertracing.spi.Codec;
import io.opentracing.propagation.TextMap;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
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
    if (value == null || value.equals("")) {
      throw new EmptyTracerStateStringException();
    }

    String[] parts = value.split(":");
    if (parts.length != 4) {
      throw new MalformedTracerStateStringException(value);
    }

    String traceId = parts[0];
    if (traceId.length() > 32 || traceId.length() < 1) {
      throw new TraceIdOutOfBoundException("Trace id [" + traceId + "] length is not withing 1 and 32");
    }

    // TODO(isaachier): When we drop Java 1.6 support, use Long.parseUnsignedLong instead of using BigInteger.
    return new JaegerSpanContext(
        high(traceId),
        new BigInteger(traceId, 16).longValue(),
        new BigInteger(parts[1], 16).longValue(),
        new BigInteger(parts[2], 16).longValue(),
        new BigInteger(parts[3], 16).byteValue());
  }

  /**
   * Parses a full (low + high) traceId, trimming the lower 64 bits.
   * @param hexString a full traceId
   * @return the long value of the higher 64 bits for a 128 bit traceId or 0 for 64 bit traceIds
   */
  private static long high(String hexString) {
    if (hexString.length() > 16) {
      int highLength = hexString.length() - 16;
      String highString = hexString.substring(0, highLength);
      return new BigInteger(highString, 16).longValue();
    }
    return 0L;
  }

  /**
   * Encode context into a string.
   * @param context Span context to encode.
   * @return Encoded string representing span context.
   */
  public static String contextAsString(JaegerSpanContext context) {
    int intFlag = context.getFlags() & 0xFF;
    return new StringBuilder()
        .append(context.getTraceId()).append(":")
        .append(Long.toHexString(context.getSpanId())).append(":")
        .append(Long.toHexString(context.getParentId())).append(":")
        .append(Integer.toHexString(intFlag))
        .toString();
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
      } else if (key.equals(Constants.BAGGAGE_HEADER_KEY)) {
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
