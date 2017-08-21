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
import com.uber.jaeger.metrics.Metric;
import com.uber.jaeger.metrics.Metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * BaggageSetter is a class that sets baggage and the logs associated
 * with the baggage on a given {@link Span}.
 */
public class BaggageSetter {

  private final BaggageRestrictionManager restrictionManager;
  private final Metrics metrics;

  public BaggageSetter(BaggageRestrictionManager restrictionManager, Metrics metrics) {
    this.restrictionManager = restrictionManager;
    this.metrics = metrics;
  }

  /**
   * Sets the baggage key:value on the {@link Span} and the corresponding
   * logs. Whether the baggage is set on the span depends on if the key
   * is allowed to be set by this service.
   * <p>
   * A {@link SpanContext} is returned with the new baggage key:value set
   * if key is valid, else returns the existing {@link SpanContext}
   * on the {@link Span}.
   *
   * @param  span  the span to set the baggage on
   * @param  value the baggage value to set
   * @return       the SpanContext with the baggage set
   */
  public SpanContext setBaggage(Span span, String key, String value) {
    Restriction restriction = restrictionManager.getRestriction(span.getServiceName(), key);
    boolean truncated = false;
    String prevItem = null;
    if (!restriction.isKeyAllowed()) {
      metrics.baggageUpdateFailure.inc(1);
      logFields(span, key, value, prevItem, truncated, restriction.isKeyAllowed());
      return span.context();
    }
    if (value.length() > restriction.getMaxValueLength()) {
      truncated = true;
      value = value.substring(0, restriction.getMaxValueLength());
      metrics.baggageTruncate.inc(1);
    }
    prevItem = span.getBaggageItem(key);
    logFields(span, key, value, prevItem, truncated, restriction.isKeyAllowed());
    metrics.baggageUpdateSuccess.inc(1);
    return span.context().withBaggageItem(key, value);
  }

  private void logFields(Span span, String key, String value, String prevItem, boolean truncated, boolean valid) {
    if (!span.context().isSampled()) {
      return;
    }
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
    if (!valid) {
      fields.put("invalid", "true");
    }
    span.log(fields);
  }
}
