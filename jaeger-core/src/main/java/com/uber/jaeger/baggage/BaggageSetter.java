/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger.baggage;

import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
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
    if (value != null && value.length() > restriction.getMaxValueLength()) {
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
