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

import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.metrics.Metrics;

import java.util.HashMap;
import java.util.Map;

import lombok.Value;

@Value(staticConstructor = "of")
public class BaggageSetter {
  final boolean valid;
  final int maxValueLength;
  final Metrics metrics;

  public SpanContext setBaggage(Span span, String key, String value) {
    if (!this.isValid()) {
      metrics.baggageUpdateFailure.inc(1);
      this.logFields(span, key, value, null, false, true);
      return span.context();
    }
    boolean truncated = false;
    if (value.length() > maxValueLength) {
      truncated = true;
      value = value.substring(0, maxValueLength);
      metrics.baggageTruncate.inc(1);
    }

    String prevItem = span.getBaggageItem(key);
    this.logFields(span, key, value, prevItem, truncated, false);
    SpanContext context = span.context().withBaggageItem(key, value);
    metrics.baggageUpdateSuccess.inc(1);
    return context;
  }

  void logFields(Span span, String key, String value, String prevItem, boolean truncated, boolean invalid) {
    if (span.context().isSampled()) {
      Map<String, String> fields = new HashMap<String, String>();
      fields.put("event", "baggage");
      fields.put("key", key);
      fields.put("value", value);
      if (prevItem != null) {
        fields.put("override", "true");
      }
      if (truncated) {
        fields.put("truncated", "true");
      }
      if (invalid) {
        fields.put("invalid", "true");
      }
      span.log(fields);
    }
  }
}
