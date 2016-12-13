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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes {@link #getSamplingStatus} using the name of the operation, and maintains a specific
 * {@link GuaranteedThroughputSampler} instance for each operation.
 */
@Slf4j
public class PerOperationSampler implements Sampler {
  private final ConcurrentHashMap<String, GuaranteedThroughputSampler> operationNameToSampler;
  private final int maxOperations;
  private volatile ProbabilisticSampler defaultSampler;

  public PerOperationSampler(int maxOperations, OperationSamplingParameters strategies) {
    this.maxOperations = maxOperations;
    this.operationNameToSampler = new ConcurrentHashMap<>();
    update(strategies);
  }

  // Visible for testing
  PerOperationSampler(int maxOperations,
                      ConcurrentHashMap<String, GuaranteedThroughputSampler> operationNameToSampler,
                      ProbabilisticSampler defaultSampler) {

    this.operationNameToSampler = operationNameToSampler;
    this.maxOperations = maxOperations;
    this.defaultSampler = defaultSampler;
  }

  public void update(OperationSamplingParameters strategies){
    double lowerBound = strategies.getDefaultLowerBoundTracesPerSecond();
    double defaultSamplingRate = strategies.getDefaultSamplingProbability();

    if (defaultSampler == null) {
      defaultSampler = new ProbabilisticSampler(defaultSamplingRate);
    } else {
      defaultSampler.update(defaultSamplingRate);
    }

    for (PerOperationSamplingParameters strategy : strategies.getPerOperationStrategies()) {

      String operation = strategy.getOperation();
      double samplingRate = strategy.getProbabilisticSampling().getSamplingRate();

      GuaranteedThroughputSampler guaranteedThroughputSampler = operationNameToSampler.get(operation);
      if (guaranteedThroughputSampler == null) {
        if (operationNameToSampler.size() < maxOperations) {
          operationNameToSampler
              .putIfAbsent(operation, new GuaranteedThroughputSampler(samplingRate, lowerBound));
        }
      } else {
        guaranteedThroughputSampler.update(samplingRate, lowerBound);
      }
    }

  }

  @Override
  public SamplingStatus getSamplingStatus(String operation, long id) {
    GuaranteedThroughputSampler sampler = operationNameToSampler.get(operation);
    if (sampler != null) {
      return sampler.getSamplingStatus(operation, id);
    }

    return defaultSampler.getSamplingStatus(operation, id);
  }

  @Override
  public void close() {
    defaultSampler.close();
    for (Map.Entry<String, GuaranteedThroughputSampler> sampler : operationNameToSampler.entrySet()) {
      sampler.getValue().close();
    }

  }
}
