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

import com.uber.jaeger.samplers.http.OperationSamplingParameters;
import com.uber.jaeger.samplers.http.PerOperationSamplingParameters;

import java.util.HashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
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
   * Updates the probabilistic samplers for each operation
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
      GuaranteedThroughputSampler sampler = new GuaranteedThroughputSampler(samplingRate, lowerBound);
      GuaranteedThroughputSampler oldSampler = operationNameToSampler.get(operation);

      if (oldSampler == null) {
        if (operationNameToSampler.size() < maxOperations) {
          operationNameToSampler.put(operation, sampler);
          isUpdated = true;
        } else {
          log.info("Exceeded the maximum number of operations({}) for per operations sampling",
                   maxOperations);
        }
      } else if (!sampler.equals(oldSampler)) {
        operationNameToSampler.put(operation, sampler);
        isUpdated = true;
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
