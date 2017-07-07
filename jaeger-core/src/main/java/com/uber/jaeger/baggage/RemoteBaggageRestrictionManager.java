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

import static com.uber.jaeger.baggage.Constants.DEFAULT_MAX_VALUE_LENGTH;

import com.uber.jaeger.baggage.http.BaggageRestriction;
import com.uber.jaeger.exceptions.BaggageRestrictionErrorException;
import com.uber.jaeger.metrics.Metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RemoteBaggageRestrictionManager implements BaggageRestrictionManager {
  private static final int DEFAULT_REFRESH_INTERVAL_MS = 60000;
  private static final int DEFAULT_REFRESH_DELAY_MS = 0;

  private final String serviceName;
  private final BaggageRestrictionProxy proxy;
  private final Timer pollTimer;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Metrics metrics;
  private final boolean denyBaggageOnInitializationFailure;
  private boolean initialized;
  private Map<String, Integer> restrictions = new HashMap<String, Integer>();

  public RemoteBaggageRestrictionManager(
      String serviceName, BaggageRestrictionProxy proxy, Metrics metrics, boolean denyBaggageOnInitializationFailure) {
    this(serviceName, proxy, metrics, denyBaggageOnInitializationFailure,
        DEFAULT_REFRESH_INTERVAL_MS, DEFAULT_REFRESH_DELAY_MS);
  }

  public RemoteBaggageRestrictionManager(
      String serviceName,
      BaggageRestrictionProxy proxy,
      Metrics metrics,
      boolean denyBaggageOnInitializationFailure,
      int refreshIntervalMs,
      int refreshDelayMs
  ) {
    this.serviceName = serviceName;
    this.proxy = proxy;
    this.metrics = metrics;
    this.denyBaggageOnInitializationFailure = denyBaggageOnInitializationFailure;
    this.initialized = false;

    pollTimer = new Timer(true); // true makes this a daemon thread
    pollTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            updateBaggageRestrictions();
          }
        },
        refreshDelayMs,
        refreshIntervalMs);
  }

  void updateBaggageRestrictions() {
    List<BaggageRestriction> response;
    try {
      response = proxy.getBaggageRestrictions(serviceName);
    } catch (BaggageRestrictionErrorException e) {
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
    ReentrantReadWriteLock.WriteLock writeLock = this.lock.writeLock();
    writeLock.lock();
    this.restrictions = baggageRestrictions;
    this.initialized = true;
    writeLock.unlock();
  }

  public void close() {
    synchronized (this) {
      pollTimer.cancel();
    }
  }

  @Override
  public BaggageValidity isValidBaggageKey(String key) {
    BaggageValidity restriction;

    if (!this.initialized) {
      if (this.denyBaggageOnInitializationFailure) {
        restriction = BaggageValidity.of(false, 0);
      } else {
        restriction = BaggageValidity.of(true, DEFAULT_MAX_VALUE_LENGTH);
      }
    } else {
      ReentrantReadWriteLock.ReadLock readLock = this.lock.readLock();
      readLock.lock();
      if (this.restrictions.containsKey(key)) {
        restriction = BaggageValidity.of(true, this.restrictions.get(key));
      } else {
        restriction = BaggageValidity.of(false, 0);
      }
      readLock.unlock();
    }
    return restriction;
  }
}
