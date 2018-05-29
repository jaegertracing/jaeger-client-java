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

package io.jaegertracing.sampler;

import io.jaegertracing.SamplingStatus;
import io.jaegertracing.internal.exceptions.SamplingStrategyErrorException;
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.samplers.HttpSamplingManager;
import io.jaegertracing.internal.samplers.SamplingManager;
import io.jaegertracing.internal.samplers.http.OperationSamplingParameters;
import io.jaegertracing.internal.samplers.http.ProbabilisticSamplingStrategy;
import io.jaegertracing.internal.samplers.http.RateLimitingSamplingStrategy;
import io.jaegertracing.internal.samplers.http.SamplingStrategyResponse;
import io.jaegertracing.spi.Sampler;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("EqualsHashCode")
@ToString(exclude = {"pollTimer", "lock"})
@Slf4j
public class RemoteControlledSampler implements Sampler {
  public static final String TYPE = "remote";
  private static final int DEFAULT_POLLING_INTERVAL_MS = 60000;

  private final int maxOperations = 2000;
  private final String serviceName;
  private final SamplingManager manager;
  private final Timer pollTimer;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Metrics metrics;
  @Getter(AccessLevel.PACKAGE)
  private Sampler sampler;

  private RemoteControlledSampler(Builder builder) {
    this.serviceName = builder.serviceName;
    this.manager = builder.samplingManager;
    this.metrics = builder.metrics;

    if (builder.initialSampler != null) {
      this.sampler = builder.initialSampler;
    } else {
      this.sampler = new ProbabilisticSampler(ProbabilisticSampler.DEFAULT_SAMPLING_PROBABILITY);
    }

    pollTimer = new Timer(true); // true makes this a daemon thread
    pollTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            updateSampler();
          }
        },
        0,
        builder.poolingIntervalMs);
    return;
  }

  public ReentrantReadWriteLock getLock() {
    return lock;
  }

  /**
   * Updates {@link #sampler} to a new sampler when it is different.
   */
  void updateSampler() {
    SamplingStrategyResponse response;
    try {
      response = manager.getSamplingStrategy(serviceName);
      metrics.samplerRetrieved.inc(1);
    } catch (SamplingStrategyErrorException e) {
      metrics.samplerQueryFailure.inc(1);
      return;
    }

    if (response.getOperationSampling() != null) {
      updatePerOperationSampler(response.getOperationSampling());
    } else {
      updateRateLimitingOrProbabilisticSampler(response);
    }
  }

  /**
   * Replace {@link #sampler} with a new instance when parameters are updated.
   * @param response which contains either a {@link ProbabilisticSampler} or {@link RateLimitingSampler}
   */
  private void updateRateLimitingOrProbabilisticSampler(SamplingStrategyResponse response) {
    Sampler sampler;
    if (response.getProbabilisticSampling() != null) {
      ProbabilisticSamplingStrategy strategy = response.getProbabilisticSampling();
      sampler = new ProbabilisticSampler(strategy.getSamplingRate());
    } else if (response.getRateLimitingSampling() != null) {
      RateLimitingSamplingStrategy strategy = response.getRateLimitingSampling();
      sampler = new RateLimitingSampler(strategy.getMaxTracesPerSecond());
    } else {
      metrics.samplerParsingFailure.inc(1);
      log.error("No strategy present in response. Not updating sampler.");
      return;
    }

    synchronized (this) {
      if (!this.sampler.equals(sampler)) {
        this.sampler = sampler;
        metrics.samplerUpdated.inc(1);
      }
    }
  }

  private synchronized void updatePerOperationSampler(OperationSamplingParameters samplingParameters) {
    if (sampler instanceof PerOperationSampler) {
      if (((PerOperationSampler) sampler).update(samplingParameters)) {
        metrics.samplerUpdated.inc(1);
      }
    } else {
      sampler = new PerOperationSampler(maxOperations, samplingParameters);
    }
  }

  @Override
  public SamplingStatus sample(String operation, long id) {
    synchronized (this) {
      return sampler.sample(operation, id);
    }
  }

  @Override
  public boolean equals(Object sampler) {
    if (this == sampler) {
      return true;
    }
    if (sampler instanceof RemoteControlledSampler) {
      RemoteControlledSampler remoteSampler = ((RemoteControlledSampler) sampler);
      synchronized (this) {
        ReentrantReadWriteLock.ReadLock readLock = remoteSampler.getLock().readLock();
        readLock.lock();
        try {
          return this.sampler.equals(remoteSampler.sampler);
        } finally {
          readLock.unlock();
        }
      }
    }
    return false;
  }

  @Override
  public void close() {
    synchronized (this) {
      pollTimer.cancel();
    }
  }

  public static class Builder {
    private final String serviceName;
    private SamplingManager samplingManager;
    private Sampler initialSampler;
    private Metrics metrics;
    private int poolingIntervalMs = DEFAULT_POLLING_INTERVAL_MS;

    public Builder(String serviceName) {
      this.serviceName = serviceName;
    }

    public Builder withSamplingManager(SamplingManager samplingManager) {
      this.samplingManager = samplingManager;
      return this;
    }

    public Builder withInitialSampler(Sampler initialSampler) {
      this.initialSampler = initialSampler;
      return this;
    }

    public Builder withMetrics(Metrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public Builder withPollingInterval(int pollingIntervalMs) {
      this.poolingIntervalMs = pollingIntervalMs;
      return this;
    }

    public RemoteControlledSampler build() {
      if (samplingManager == null) {
        samplingManager = new HttpSamplingManager();
      }
      if (initialSampler == null) {
        initialSampler = new ProbabilisticSampler(0.001);
      }
      if (metrics == null) {
        metrics = new Metrics(new InMemoryMetricsFactory());
      }
      return new RemoteControlledSampler(this);
    }
  }
}
