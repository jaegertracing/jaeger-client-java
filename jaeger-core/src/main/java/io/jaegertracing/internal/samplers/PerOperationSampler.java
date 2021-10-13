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

package io.jaegertracing.internal.samplers;

import io.jaegertracing.internal.samplers.http.OperationSamplingParameters;
import io.jaegertracing.internal.samplers.http.PerOperationSamplingParameters;
import io.jaegertracing.spi.Sampler;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;


/**
 * Computes {@link #sample(String, long)} using the name of the operation, and maintains a specific
 * {@link GuaranteedThroughputSampler} instance for each operation.
 */
@Slf4j
@EqualsAndHashCode
@ToString
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Getter(AccessLevel.PACKAGE) //Visible for testing
public class PerOperationSampler implements Sampler {
  private final int maxOperations;
  private final ConcurrentHashMap<String, GuaranteedThroughputSampler> operationNameToSampler;
  private volatile ProbabilisticSampler defaultSampler;
  private volatile double lowerBound;

  public PerOperationSampler(int maxOperations, OperationSamplingParameters strategies) {
    this(maxOperations,
         new ConcurrentHashMap<String, GuaranteedThroughputSampler>(),
         new ProbabilisticSampler(strategies.getDefaultSamplingProbability()),
         strategies.getDefaultLowerBoundTracesPerSecond());
    update(strategies);
  }

  /**
   * Updates the GuaranteedThroughputSampler for each operation
   * @param strategies The parameters for operation sampling
   * @return true if any samplers were updated
   */
  public synchronized boolean update(final OperationSamplingParameters strategies) {
    AtomicBoolean isUpdated = new AtomicBoolean(false);

    if (lowerBound != strategies.getDefaultLowerBoundTracesPerSecond()) {
      lowerBound = strategies.getDefaultLowerBoundTracesPerSecond();
      isUpdated.set(true);
    }
    ProbabilisticSampler defaultSampler = new ProbabilisticSampler(
        strategies.getDefaultSamplingProbability());

    if (!defaultSampler.equals(this.defaultSampler)) {
      this.defaultSampler = defaultSampler;
      isUpdated.set(true);
    }

    Set<String> configuredOperations = strategies.getPerOperationStrategies().stream()
        .map(PerOperationSamplingParameters::getOperation).collect(Collectors.toSet());
    for (Entry<String, GuaranteedThroughputSampler> entry : operationNameToSampler.entrySet()) {
      if (!configuredOperations.contains(entry.getKey())
          && entry.getValue().update(defaultSampler.getSamplingRate(), lowerBound)) {
        isUpdated.set(true);
      }
    }

    // add or update operation samples using given strategies
    for (PerOperationSamplingParameters strategy : strategies.getPerOperationStrategies()) {
      String operation = strategy.getOperation();
      double samplingRate = strategy.getProbabilisticSampling().getSamplingRate();
      GuaranteedThroughputSampler sampler = operationNameToSampler.computeIfAbsent(operation,
          op -> {
            if (operationNameToSampler.size() >= maxOperations) {
              log.info("Exceeded the maximum number of operations({}) for per operations sampling",
                  maxOperations);
              return null;
            }
            isUpdated.set(true);
            return new GuaranteedThroughputSampler(samplingRate, lowerBound);
          });
      if (sampler != null && sampler.update(samplingRate, lowerBound)) {
        isUpdated.set(true);
      }
    }
    return isUpdated.get();
  }

  @Override
  public SamplingStatus sample(String operation, long id) {
    Sampler sampler = operationNameToSampler.computeIfAbsent(operation, op -> {
      if (operationNameToSampler.size() >= maxOperations) {
        return null;
      }
      return new GuaranteedThroughputSampler(defaultSampler.getSamplingRate(), lowerBound);
    });
    if (sampler == null) {
      sampler = defaultSampler;
    }
    return sampler.sample(operation, id);
  }

  @Override
  public synchronized void close() {
    defaultSampler.close();
    for (GuaranteedThroughputSampler sampler : operationNameToSampler.values()) {
      sampler.close();
    }
  }
}
