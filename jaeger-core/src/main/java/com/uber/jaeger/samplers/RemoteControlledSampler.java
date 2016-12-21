/*
 * Copyright (c) 2016, Uber Technologies, Inc
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
package com.uber.jaeger.samplers;

import com.uber.jaeger.exceptions.SamplingStrategyErrorException;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.samplers.http.OperationSamplingParameters;
import com.uber.jaeger.samplers.http.ProbabilisticSamplingStrategy;
import com.uber.jaeger.samplers.http.RateLimitingSamplingStrategy;
import com.uber.jaeger.samplers.http.SamplingStrategyResponse;

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

  private final int maxOperations = 2000;
  private final String serviceName;
  private final SamplingManager manager;
  private final Timer pollTimer;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Metrics metrics;
  @Getter(AccessLevel.PACKAGE)
  private Sampler sampler;

  public RemoteControlledSampler(
      String serviceName, SamplingManager manager, Sampler initial, Metrics metrics) {
    final int pollingIntervalMs = 60000;
    this.serviceName = serviceName;
    this.manager = manager;
    this.metrics = metrics;

    if (initial != null) {
      this.sampler = initial;
    } else {
      this.sampler = new ProbabilisticSampler(0.001);
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
        pollingIntervalMs);
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
    if (this == sampler) return true;
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
}
