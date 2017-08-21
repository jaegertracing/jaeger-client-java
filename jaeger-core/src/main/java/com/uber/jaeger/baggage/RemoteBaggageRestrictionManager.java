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

import com.uber.jaeger.baggage.http.BaggageRestrictionResponse;
import com.uber.jaeger.exceptions.BaggageRestrictionManagerException;
import com.uber.jaeger.metrics.Metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * NewRestrictionManager returns a BaggageRestrictionManager that polls the agent for the latest
 * baggage restrictions.
 */
public class RemoteBaggageRestrictionManager implements BaggageRestrictionManager {
  private static final int DEFAULT_REFRESH_INTERVAL_MS = 60000;
  private static final int DEFAULT_INITIAL_DELAY_MS = 0;

  private final String serviceName;
  private final BaggageRestrictionManagerProxy proxy;
  private final Timer pollTimer;
  private final Metrics metrics;
  private final boolean denyBaggageOnInitializationFailure;
  private volatile boolean initialized;
  private volatile Map<String, Restriction> restrictions = new HashMap<String, Restriction>();
  private final Restriction invalidRestriction;
  private final Restriction validRestriction;

  public RemoteBaggageRestrictionManager(
      String serviceName,
      BaggageRestrictionManagerProxy proxy,
      Metrics metrics,
      boolean denyBaggageOnInitializationFailure
  ) {
    this(serviceName, proxy, metrics, denyBaggageOnInitializationFailure, DEFAULT_REFRESH_INTERVAL_MS);
  }

  public RemoteBaggageRestrictionManager(
      String serviceName,
      BaggageRestrictionManagerProxy proxy,
      Metrics metrics,
      boolean denyBaggageOnInitializationFailure,
      int refreshIntervalMs
  ) {
    this(serviceName, proxy, metrics, denyBaggageOnInitializationFailure, refreshIntervalMs, DEFAULT_INITIAL_DELAY_MS);
  }

  /**
   * Creates a RemoteBaggageRestrictionManager that fetches {@link BaggageRestrictionResponse} from a remote
   * agent and keeps track of {@link Restriction} for a service.
   *
   * {@param initialDelayMs} is only exposed for testing purposes so users can determine when the first call to
   * remote agent is made. Under normal operations, this RemoteBaggageRestrictionManager will start up and
   * asynchronously fetch restrictions. If the user wants to know if restrictions are ready, they can check via
   * isReady().
   *
   * @param serviceName restrictions for this service are kept track of.
   * @param proxy proxy to remote agent.
   * @param metrics metrics for metrics emission.
   * @param denyBaggageOnInitializationFailure determines the startup failure mode of RemoteBaggageRestrictionManager.
   *                                           If DenyBaggageOnInitializationFailure is true,
   *                                           RemoteBaggageRestrictionManager will not allow any baggage to be written
   *                                           until baggage restrictions have been retrieved from agent. If
   *                                           DenyBaggageOnInitializationFailure is false,
   *                                           RemoteBaggageRestrictionManager will allow any baggage to be written
   *                                           until baggage restrictions have been retrieved from agent.
   * @param refreshIntervalMs how often restriction are fetched from remote agent.
   * @param initialDelayMs delay before first fetch of restrictions.
   */
  RemoteBaggageRestrictionManager(
      String serviceName,
      BaggageRestrictionManagerProxy proxy,
      Metrics metrics,
      boolean denyBaggageOnInitializationFailure,
      int refreshIntervalMs,
      int initialDelayMs
  ) {
    this.serviceName = serviceName;
    this.proxy = proxy;
    this.metrics = metrics;
    this.denyBaggageOnInitializationFailure = denyBaggageOnInitializationFailure;
    this.initialized = false;
    this.invalidRestriction = Restriction.of(false, 0);
    this.validRestriction = Restriction.of(true, DEFAULT_MAX_VALUE_LENGTH);

    pollTimer = new Timer(true); // true makes this a daemon thread
    pollTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            updateBaggageRestrictions();
          }
        },
        initialDelayMs,
        refreshIntervalMs);
  }

  public boolean isReady() {
    return initialized;
  }

  void updateBaggageRestrictions() {
    List<BaggageRestrictionResponse> response;
    try {
      response = proxy.getBaggageRestrictions(serviceName);
    } catch (BaggageRestrictionManagerException e) {
      metrics.baggageRestrictionsUpdateFailure.inc(1);
      return;
    }

    updateBaggageRestrictions(response);
    metrics.baggageRestrictionsUpdateSuccess.inc(1);
  }

  private void updateBaggageRestrictions(List<BaggageRestrictionResponse> restrictions) {
    Map<String, Restriction> baggageRestrictions = new HashMap<String, Restriction>();
    for (BaggageRestrictionResponse restriction : restrictions) {
      baggageRestrictions.put(restriction.getBaggageKey(), Restriction.of(true, restriction.getMaxValueLength()));
    }
    this.restrictions = baggageRestrictions;
    initialized = true;
  }

  public void close() {
    pollTimer.cancel();
  }

  @Override
  public Restriction getRestriction(String service, String key) {
    if (!initialized) {
      if (denyBaggageOnInitializationFailure) {
        return invalidRestriction;
      } else {
        return validRestriction;
      }
    }
    Restriction restriction = this.restrictions.get(key);
    if (restriction != null) {
      return restriction;
    }
    return invalidRestriction;
  }
}
