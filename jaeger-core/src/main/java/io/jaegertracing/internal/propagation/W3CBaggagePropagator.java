/*
 * Copyright 2021, OpenTelemetry Authors
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

import io.jaegertracing.internal.JaegerSpanContext;
import io.opentracing.propagation.TextMap;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Implementation of the Baggage propagation protocol used by TraceContextCodec See <a
 * href="https://github.com/w3c/baggage">w3c/baggage</a>
 *
 * This implementation is mostly copied over from OpenTelemetry Java SDK
 * https://github.com/open-telemetry/opentelemetry-java/blob/v1.5.0/api/all/src/main/java/io/opentelemetry/api/baggage/propagation
 */
class W3CBaggagePropagator {
  
  static final String BAGGAGE = "baggage";
  
  static final W3CBaggagePropagator INSTANCE = new W3CBaggagePropagator();
  
  private W3CBaggagePropagator() {}
  
  Map<String, String> extractBaggage(String baggage) {
    if (baggage == null) {
      return null;
    } 
    if (baggage.trim().isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> parsed = new HashMap<String, String>();
    new BaggageParser(baggage).parseInto(parsed);
    return parsed;
  }
  
  void injectBaggage(JaegerSpanContext spanContext, TextMap carrier) {
    if (spanContext != null && spanContext.baggageCount() > 0) {
      StringBuilder headerContent = new StringBuilder();
      for (Entry<String, String> baggageEntry : spanContext.baggageItems()) {
        headerContent.append(baggageEntry.getKey()).append("=")
            .append(urlEncode(baggageEntry.getValue()));
        headerContent.append(",");
      }
      if (headerContent.length() > 0) {
        headerContent.setLength(headerContent.length() - 1);
      }
      String baggageString = headerContent.toString();
      carrier.put(BAGGAGE, baggageString);
    }
  }
  
  private static String urlDecode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // not much we can do, try raw value
      return value;
    }
  }

  private static String urlEncode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // not much we can do, try raw value
      return value;
    }
  }
 
  /**
   * Implements single-pass Baggage parsing in accordance with https://w3c.github.io/baggage/ Key /
   * value are restricted in accordance with https://www.ietf.org/rfc/rfc2616.txt
   *
   * <p>
   * Note: following aspects are not specified in RFC: - some invalid elements (key or value) - parser
   * will include valid ones, disregard invalid - empty "value" is regarded as invalid - meta -
   * anything besides element terminator (comma), which are not propagated as there is no support for
   * baggage metadata in the existing API.
   */
  private static class BaggageParser {

    private enum State {
      KEY, VALUE, META
    }

    private final String baggageHeader;

    private final Element key = Element.createKeyElement();
    private final Element value = Element.createValueElement();

    private BaggageParser.State state;

    private boolean skipToNext;

    private BaggageParser(String baggageHeader) {
      this.baggageHeader = baggageHeader;
      reset(0);
    }

    private void parseInto(Map<String, String> baggageBuilder) {
      for (int i = 0, n = baggageHeader.length(); i < n; i++) {
        char current = baggageHeader.charAt(i);

        if (skipToNext) {
          if (current == ',') {
            reset(i + 1);
          }
          continue;
        }

        switch (current) {
          case '=': {
            if (state == State.KEY) {
              if (key.tryTerminating(i, baggageHeader)) {
                setState(State.VALUE, i + 1);
              } else {
                skipToNext = true;
              }
            }
            break;
          }
          case ';': {
            if (state == State.VALUE) {
              skipToNext = !value.tryTerminating(i, baggageHeader);
              setState(State.META, i + 1);
            }
            break;
          }
          case ',': {
            switch (state) {
              case VALUE:
                value.tryTerminating(i, baggageHeader);
                break;
              case META:
                break;
              case KEY: // none
              default: // required by checkstyle
            }
            putBaggage(baggageBuilder, key.getValue(), value.getValue());
            reset(i + 1);
            break;
          }
          default: {
            switch (state) {
              case KEY:
                skipToNext = !key.tryNextChar(current, i);
                break;
              case VALUE:
                skipToNext = !value.tryNextChar(current, i);
                break;
              case META: // none
              default: // required by checkstyle
            }
          }
        }
      }
      // need to finish parsing if there was no list element termination comma
      switch (state) {
        case KEY:
          break;
        case META: {
          putBaggage(baggageBuilder, key.getValue(), value.getValue());
          break;
        }
        case VALUE: {
          if (!skipToNext) {
            value.tryTerminating(baggageHeader.length(), baggageHeader);
            putBaggage(baggageBuilder, key.getValue(), value.getValue());
          }
          break;
        }
        default: // required by checkstyle
      }
    }

    private static void putBaggage(Map<String, String> baggage, String key, String value) {
      if (key != null && value != null) {
        baggage.put(key, W3CBaggagePropagator.urlDecode(value));
      }
    }

    /**
     * Resets parsing state, preparing to start a new list element (see spec).
     *
     * @param index index where parser should start new element scan
     */
    private void reset(int index) {
      this.skipToNext = false;
      this.state = State.KEY;
      this.key.reset(index);
      this.value.reset(index);
    }

    /** Switches parser state (element of a list member). */
    private void setState(BaggageParser.State state, int start) {
      this.state = state;
    }

    private static class Element {

      private static final BitSet EXCLUDED_KEY_CHARS = new BitSet(128);
      private static final BitSet EXCLUDED_VALUE_CHARS = new BitSet(128);

      static {
        for (char c : new char[] { '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']',
            '?', '=', '{', '}' }) {
          EXCLUDED_KEY_CHARS.set(c);
        }
        for (char c : new char[] { '"', ',', ';', '\\' }) {
          EXCLUDED_VALUE_CHARS.set(c);
        }
      }

      private final BitSet excluded;

      private boolean leadingSpace;
      private boolean readingValue;
      private boolean trailingSpace;
      private int start;
      private int end;
      private String value;

      private static Element createKeyElement() {
        return new Element(EXCLUDED_KEY_CHARS);
      }

      private static Element createValueElement() {
        return new Element(EXCLUDED_VALUE_CHARS);
      }

      /**
       * Constructs element instance.
       *
       * @param excluded characters that are not allowed for this type of an element
       */
      private Element(BitSet excluded) {
        this.excluded = excluded;
        reset(0);
      }

      private String getValue() {
        return value;
      }

      private void reset(int start) {
        this.start = start;
        leadingSpace = true;
        readingValue = false;
        trailingSpace = false;
        value = null;
      }

      private boolean tryTerminating(int index, String header) {
        if (this.readingValue) {
          markEnd(index);
        }
        if (this.trailingSpace) {
          setValue(header);
          return true;
        } else {
          // leading spaces - no content, invalid
          return false;
        }
      }

      private void markEnd(int end) {
        this.end = end;
        this.readingValue = false;
        trailingSpace = true;
      }

      private void setValue(String header) {
        this.value = header.substring(this.start, this.end);
      }

      private boolean tryNextChar(char character, int index) {
        if (isWhitespace(character)) {
          return tryNextWhitespace(index);
        } else if (isExcluded(character)) {
          return false;
        } else {
          return tryNextTokenChar(index);
        }
      }

      private static boolean isWhitespace(char character) {
        return character == ' ' || character == '\t';
      }

      private boolean tryNextWhitespace(int index) {
        if (readingValue) {
          markEnd(index);
        }
        return true;
      }

      private boolean isExcluded(char character) {
        return (character <= 32 || character >= 127 || excluded.get(character));
      }

      private boolean tryNextTokenChar(int index) {
        if (leadingSpace) {
          markStart(index);
        }
        return !trailingSpace;
      }

      private void markStart(int start) {
        this.start = start;
        readingValue = true;
        leadingSpace = false;
      }
    }
  }
}
