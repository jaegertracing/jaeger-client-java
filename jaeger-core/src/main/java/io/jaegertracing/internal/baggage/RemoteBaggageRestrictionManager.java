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

package io.jaegertracing.internal.baggage;

import io.jaegertracing.internal.baggage.http.BaggageRestrictionResponse;
import io.jaegertracing.internal.exceptions.BaggageRestrictionManagerException;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.spi.BaggageRestrictionManager;
import io.jaegertracing.spi.BaggageRestrictionManagerProxy;
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

  protected RemoteBaggageRestrictionManager(
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
      if (response == null || response.isEmpty()) {
        throw new BaggageRestrictionManagerException("empty restrictions response");
      }
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

  public static class Builder {
    private String serviceName;
    private BaggageRestrictionManagerProxy proxy;
    private Metrics metrics;
    private boolean denyBaggageOnInitializationFailure;
    private int refreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS;
    private int initialDelayMs = DEFAULT_INITIAL_DELAY_MS;

    /**
     * @param serviceName restrictions for this service are kept track of.
     */
    public Builder(String serviceName) {
      this.serviceName = serviceName;
    }

    /**
     * @param proxy proxy to remote agent.
     */
    public Builder withProxy(BaggageRestrictionManagerProxy proxy) {
      this.proxy = proxy;
      return this;
    }

    public Builder withMetrics(Metrics metrics) {
      this.metrics = metrics;
      return this;
    }

    /**
     * If {@code denyBaggageOnInitializationFailure} is true,
     * {@link RemoteBaggageRestrictionManager} will not allow any baggage to be written until
     * baggage restrictions have been retrieved from agent. If {@code denyBaggageOnInitializationFailure} is
     * false, {@link RemoteBaggageRestrictionManager} will allow any baggage to be written until
     * baggage restrictions have been retrieved from agent.
     *
     * @param denyBaggageOnInitializationFailure determines the startup failure mode of
     * {@link RemoteBaggageRestrictionManager}
     */
    public Builder withDenyBaggageInitializationFailure(boolean denyBaggageOnInitializationFailure) {
      this.denyBaggageOnInitializationFailure = denyBaggageOnInitializationFailure;
      return this;
    }

    /**
     * @param refreshIntervalMs how often restriction are fetched from remote agent.
     */
    public Builder withRefreshIntervalMs(int refreshIntervalMs) {
      this.refreshIntervalMs = refreshIntervalMs;
      return this;
    }

    /**
     * {@code initialDelayMs} is only exposed for testing purposes so users can determine when
     * the first call to remote agent is made. Under normal operations,
     * this {@link RemoteBaggageRestrictionManager} will start up and asynchronously fetch restrictions.
     * If the user wants to know if restrictions are ready, they can check via
     * {@link RemoteBaggageRestrictionManager#isReady()}.
     *
     * @param initialDelayMs delay before first fetch of restrictions.
     */
    public Builder withInitialDelayMs(int initialDelayMs) {
      this.initialDelayMs = initialDelayMs;
      return this;
    }

    /**
     * Creates a RemoteBaggageRestrictionManager that fetches {@link BaggageRestrictionResponse} from a remote
     * agent and keeps track of {@link Restriction} for a service.
     */
    public RemoteBaggageRestrictionManager build() {
      return new RemoteBaggageRestrictionManager(serviceName, proxy, metrics, denyBaggageOnInitializationFailure,
          refreshIntervalMs, initialDelayMs);
    }
  }
}
