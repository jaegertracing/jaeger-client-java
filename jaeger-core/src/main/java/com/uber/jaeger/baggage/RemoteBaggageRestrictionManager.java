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

import com.uber.jaeger.baggage.http.BaggageRestriction;
import com.uber.jaeger.exceptions.BaggageRestrictionException;
import com.uber.jaeger.metrics.Metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class RemoteBaggageRestrictionManager extends BaggageRestrictionManager {
  private static final int DEFAULT_REFRESH_INTERVAL_MS = 60000;

  private final String serviceName;
  private final BaggageRestrictionProxy proxy;
  private final Timer pollTimer;
  private final Metrics metrics;
  private final boolean denyBaggageOnInitializationFailure;
  private volatile boolean initialized;
  private volatile Map<String, Integer> restrictions = new HashMap<String, Integer>();

  public RemoteBaggageRestrictionManager(
      String serviceName, BaggageRestrictionProxy proxy, Metrics metrics, boolean denyBaggageOnInitializationFailure) {
    this(serviceName, proxy, metrics, denyBaggageOnInitializationFailure, DEFAULT_REFRESH_INTERVAL_MS);
  }

  public RemoteBaggageRestrictionManager(
      String serviceName,
      BaggageRestrictionProxy proxy,
      Metrics metrics,
      boolean denyBaggageOnInitializationFailure,
      int refreshIntervalMs
  ) {
    this.serviceName = serviceName;
    this.proxy = proxy;
    this.metrics = metrics;
    this.denyBaggageOnInitializationFailure = denyBaggageOnInitializationFailure;
    this.initialized = false;

    // Initialize baggage restrictions
    this.updateBaggageRestrictions();

    int randomRefreshDelayMs = (int)(Math.random() * DEFAULT_REFRESH_INTERVAL_MS);
    pollTimer = new Timer(true); // true makes this a daemon thread
    pollTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            updateBaggageRestrictions();
          }
        },
        randomRefreshDelayMs,
        refreshIntervalMs);
  }

  void updateBaggageRestrictions() {
    List<BaggageRestriction> response;
    try {
      response = proxy.getBaggageRestrictions(serviceName);
    } catch (BaggageRestrictionException e) {
      metrics.baggageRestrictionsUpdateFailure.inc(1);
      return;
    }

    updateBaggageRestrictions(response);
    metrics.baggageRestrictionsUpdateSuccess.inc(1);
  }

  private void updateBaggageRestrictions(List<com.uber.jaeger.baggage.http.BaggageRestriction> restrictions) {
    Map<String, Integer> baggageRestrictions = new HashMap<String, Integer>();
    for (com.uber.jaeger.baggage.http.BaggageRestriction restriction : restrictions) {
      baggageRestrictions.put(restriction.getBaggageKey(), restriction.getMaxValueLength());
    }
    this.restrictions = baggageRestrictions;
    this.initialized = true;
  }

  public void close() {
    pollTimer.cancel();
  }

  @Override
  public SanitizedBaggage sanitizeBaggage(String key, String value, String prevValue) {
    if (!this.initialized) {
      if (this.denyBaggageOnInitializationFailure) {
        return INVALID_BAGGAGE;
      } else {
        return sanitizeBaggage(key, value, prevValue, DEFAULT_MAX_VALUE_LENGTH);
      }
    }
    Integer maxValueLength = this.restrictions.get(key);
    if (maxValueLength != null) {
      return sanitizeBaggage(key, value, prevValue, maxValueLength);
    }
    return INVALID_BAGGAGE;
  }
}
