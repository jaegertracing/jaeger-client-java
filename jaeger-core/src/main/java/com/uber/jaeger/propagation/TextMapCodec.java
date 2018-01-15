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

import static com.uber.jaeger.Constants.MANUAL_BAGGAGE_HEADER_KEY;

import com.google.common.annotations.VisibleForTesting;
import com.uber.jaeger.Constants;
import com.uber.jaeger.SpanContext;
import io.opentracing.propagation.TextMap;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private static final Pattern commaPattern = Pattern.compile(",");

  private static final Pattern equalsPattern = Pattern.compile("=");

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

  /**
   * Populates SpanContext based on the header values available in the carrier.
   *
   * Rules followed are:
   * 1. When trace ID is present
   *    - baggage from uberctx- is respected
   *    - baggage from jaeger-baggage is not respected
   * 2. When trace ID is not present
   *    - baggage from uberctx- is not respected
   *    - baggage from jaeger-baggage is respected
   * 3. When neither trace ID, nor debug ID is present, null is returned.
   * 4. Debug ID is always respected regardless of other data
   *
   * @param carrier The iterable to parse the headers out of
   * @return SpanContext populated with information from headers, based on aforementioned rules.
   */
  @Override
  public SpanContext extract(TextMap carrier) {
    Map<String, String> baggage = null;
    Map<String, String> debugBaggage = null;
    @Nullable String debugId = null;
    @Nullable String traceId = null;
    for (Map.Entry<String, String> entry : carrier) {
      // TODO there should be no lower-case here
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      if (key.equals(contextKey)) {
        traceId = decodedValue(entry.getValue());
      } else if (key.equals(Constants.DEBUG_ID_HEADER_KEY)) {
        debugId = decodedValue(entry.getValue());
      } else if (key.startsWith(baggagePrefix)) {
        if (baggage == null) {
          baggage = new HashMap<String, String>();
        }
        baggage.put(keys.unprefixedKey(key, baggagePrefix), decodedValue(entry.getValue()));
      } else if (key.equals(MANUAL_BAGGAGE_HEADER_KEY)) {
        String decodedDebugBaggageItems = decodedValue(entry.getValue());
        debugBaggage = getDebugHeadersAsBaggage(decodedDebugBaggageItems);
      }
    }

    @NotNull SpanContext context;
    if (debugId == null && traceId == null) {
      return null;
    } else if (debugId != null) {
      // Debug ID is always respected regardless of other data
      baggage = debugBaggage;
      context = SpanContext.withDebugId(debugId);
    } else {
      // debug ID == null + trace ID != null
      context = SpanContext.contextFromString(traceId);
    }

    // there is a chance that baggage variable is uninitialized
    // if there is no `uberctx-` or debug baggage.
    if (baggage == null) {
      return context;
    }

    return context.withBaggage(baggage);
  }

  /**
   * Transform string like "k1=v1,k2=v2" into a map like {k1: v1, k2: v2}.
   *
   * All items that do not confirm to the aforementioned rule will not be included. In case where
   * there is no baggage in the passed string, a null value will be returned.
   *
   * @param decodedDebugBaggageItems Contents of debug baggage values in the form "k1=v1,k2=v2"
   * @return Map of debug headers
   */
  @VisibleForTesting protected @Nullable Map<String, String> getDebugHeadersAsBaggage(
      @NotNull String decodedDebugBaggageItems
  ) {
    // strip spaces from both ends to not cause cases like ",,,= " to get picked up
    decodedDebugBaggageItems = decodedDebugBaggageItems.trim();

    Map<String, String> debugBaggage = null;
    String[] debugBaggageItems = commaPattern.split(decodedDebugBaggageItems);
    for (String debugBaggageItem: debugBaggageItems) {
      String[] debugBaggageItemParts = equalsPattern.split(debugBaggageItem.trim());
      // ensure degree of validation in debug baggage contents
      // expect contents to be of form x=y
      if (debugBaggageItemParts.length != 2) {
        continue;
      }

      if (debugBaggage == null) {
        debugBaggage = new HashMap<String, String>();
      }
      debugBaggage.put(debugBaggageItemParts[0].trim(), debugBaggageItemParts[1].trim());
    }
    return debugBaggage;
  }

  @Override
  public @NotNull String toString() {
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

  private @NotNull String decodedValue(@NotNull String value) {
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
