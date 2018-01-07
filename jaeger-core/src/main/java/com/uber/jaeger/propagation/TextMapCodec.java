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

import static com.uber.jaeger.Constants.DEBUG_JAEGER_SPAN_BAGGAGE_HEADER;

import com.uber.jaeger.Constants;
import com.uber.jaeger.SpanContext;
import io.opentracing.propagation.TextMap;
import java.io.UnsupportedEncodingException;
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

  @Override
  public void inject(SpanContext spanContext, TextMap carrier) {
    carrier.put(contextKey, encodedValue(spanContext.contextAsString()));
    for (Map.Entry<String, String> entry : spanContext.baggageItems()) {
      carrier.put(keys.prefixedKey(entry.getKey(), baggagePrefix), encodedValue(entry.getValue()));
    }
  }

  @Override
  public SpanContext extract(TextMap carrier) {
    SpanContext context = null;
    Map<String, String> baggage = new HashMap();
    String debugId = null;
    for (Map.Entry<String, String> entry : carrier) {
      // TODO there should be no lower-case here
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      if (key.equals(contextKey)) {
        context = SpanContext.contextFromString(decodedValue(entry.getValue()));
      } else if (key.equals(Constants.DEBUG_ID_HEADER_KEY)) {
        debugId = decodedValue(entry.getValue());
      } else if (key.startsWith(baggagePrefix)) {
        baggage.put(keys.unprefixedKey(key, baggagePrefix), decodedValue(entry.getValue()));
      } else if (key.equals(DEBUG_JAEGER_SPAN_BAGGAGE_HEADER)) {
        /**
         * debug jaeger baggage is loaded for a header item that looks like,
         * <pre>
         * {@code
         *    "jaeger-baggage": "k1=v1,k2=v2"
         * }
         * </pre>
         */
        String decodedDebugBaggageItems = decodedValue(entry.getValue());
        String[] debugBaggageItems = decodedDebugBaggageItems.split(",");
        for (String debugBaggageItem: debugBaggageItems) {
          String[] debugBaggageItemParts = debugBaggageItem.split("=");
          baggage.put(debugBaggageItemParts[0], debugBaggageItemParts[1]);
        }
      }
    }
    // this pattern allows for presence of debug ID to trump everything else
    if (context == null) {
      if (debugId == null) {
        return null;
      }
      context = SpanContext.withDebugId(debugId);
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
