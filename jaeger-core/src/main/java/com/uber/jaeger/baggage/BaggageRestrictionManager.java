/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger.baggage;

import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;

public abstract class BaggageRestrictionManager {
  static final int DEFAULT_MAX_VALUE_LENGTH = 2048;
  static final SanitizedBaggage INVALID_BAGGAGE = SanitizedBaggage.of(false, null, null, null, false);

  public abstract SanitizedBaggage sanitizeBaggage(String key, String value, String prevValue);

  SanitizedBaggage sanitizeBaggage(String key, String value, String prevValue, int maxValueLength) {
    TruncatedBaggage baggage = truncateBaggage(value, maxValueLength);
    Map<String, String> fields = new HashMap<String, String>();
    fields.put("event", "baggage");
    fields.put("key", key);
    fields.put("value", baggage.getTruncatedValue());
    if (baggage.isTruncated()) {
      fields.put("truncated", "true");
    }
    if (prevValue != null) {
      fields.put("override", "true");
    }
    return SanitizedBaggage.of(true, key, baggage.getTruncatedValue(), fields,
        baggage.isTruncated());
  }

  TruncatedBaggage truncateBaggage(String value, int maxValueLength) {
    if (value.length() > maxValueLength) {
      return new TruncatedBaggage(value.substring(0, maxValueLength), true);
    }
    return new TruncatedBaggage(value, false);
  }

  @Getter(AccessLevel.PACKAGE)
  class TruncatedBaggage {
    final String truncatedValue;
    final boolean truncated;

    TruncatedBaggage(String truncatedValue, boolean truncated) {
      this.truncatedValue = truncatedValue;
      this.truncated = truncated;
    }
  }
}
