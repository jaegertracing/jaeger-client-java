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

package io.jaegertracing.samplers;

import io.jaegertracing.samplers.http.OperationSamplingParameters;
import io.jaegertracing.samplers.http.PerOperationSamplingParameters;
import java.util.HashMap;
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
  private final HashMap<String, GuaranteedThroughputSampler> operationNameToSampler;
  private ProbabilisticSampler defaultSampler;
  private double lowerBound;

  public PerOperationSampler(int maxOperations, OperationSamplingParameters strategies) {
    this(maxOperations,
         new HashMap<String, GuaranteedThroughputSampler>(),
         new ProbabilisticSampler(strategies.getDefaultSamplingProbability()),
         strategies.getDefaultLowerBoundTracesPerSecond());
    update(strategies);
  }

  /**
   * Updates the GuaranteedThroughputSampler for each operation
   * @param strategies The parameters for operation sampling
   * @return true iff any samplers were updated
   */
  public synchronized boolean update(OperationSamplingParameters strategies) {
    boolean isUpdated = false;

    lowerBound = strategies.getDefaultLowerBoundTracesPerSecond();
    ProbabilisticSampler defaultSampler = new ProbabilisticSampler(strategies.getDefaultSamplingProbability());

    if (!defaultSampler.equals(this.defaultSampler)) {
      this.defaultSampler = defaultSampler;
      isUpdated = true;
    }

    for (PerOperationSamplingParameters strategy : strategies.getPerOperationStrategies()) {
      String operation = strategy.getOperation();
      double samplingRate = strategy.getProbabilisticSampling().getSamplingRate();
      GuaranteedThroughputSampler sampler = operationNameToSampler.get(operation);
      if (sampler != null) {
        isUpdated = sampler.update(samplingRate, lowerBound) || isUpdated;
      } else {
        if (operationNameToSampler.size() < maxOperations) {
          sampler = new GuaranteedThroughputSampler(samplingRate, lowerBound);
          operationNameToSampler.put(operation, sampler);
          isUpdated = true;
        } else {
          log.info("Exceeded the maximum number of operations({}) for per operations sampling",
              maxOperations);
        }
      }
    }
    return isUpdated;
  }

  @Override
  public synchronized SamplingStatus sample(String operation, long id) {
    GuaranteedThroughputSampler sampler = operationNameToSampler.get(operation);
    if (sampler != null) {
      return sampler.sample(operation, id);
    }

    if (operationNameToSampler.size() < maxOperations) {
      sampler = new GuaranteedThroughputSampler(defaultSampler.getSamplingRate(), lowerBound);
      operationNameToSampler.put(operation, sampler);
      return sampler.sample(operation, id);
    }

    return defaultSampler.sample(operation, id);
  }

  @Override
  public synchronized void close() {
    defaultSampler.close();
    for (GuaranteedThroughputSampler sampler : operationNameToSampler.values()) {
      sampler.close();
    }
  }
}
