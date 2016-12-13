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

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * {@link GuaranteedThroughputSampler} is a {@link Sampler} that guarantees a throughput by using
 * a {@link ProbabilisticSampler} and {@link RateLimitingSampler} in tandem.
 *
 * The RateLimitingSampler is used to establish a lowerBound so that every operation is sampled
 * at least once in the time interval defined by the lowerBound.
 */
@EqualsAndHashCode
@ToString
public class GuaranteedThroughputSampler implements Sampler {
  private final ProbabilisticSampler probabilisticSampler;
  private final RateLimitingSampler lowerBoundSampler;

  public GuaranteedThroughputSampler(double samplingRate, double lowerBound) {
    this(new ProbabilisticSampler(samplingRate), new RateLimitingSampler(lowerBound));
  }

  // Visible for testing
  GuaranteedThroughputSampler(ProbabilisticSampler probabilisticSampler,
                              RateLimitingSampler rateLimitingSampler) {
    this.probabilisticSampler = probabilisticSampler;
    this.lowerBoundSampler = rateLimitingSampler;
  }


  /**
   * Calls {@link Sampler#getSamplingStatus(String, long)} on both samplers, returning true for
   * {@link SamplingStatus#isSampled} if either samplers set #isSampled to true.
   * The tags corresponding to the sampler that returned true are set on {@link SamplingStatus#tags}
   * If both samplers return true, tags for {@link ProbabilisticSampler} is given priority.
   * @param operation The operation name, which is ignored by this sampler
   * @param id The traceId on the span
   */
  @Override
  public SamplingStatus getSamplingStatus(String operation, long id) {
    SamplingStatus probabilisticSamplingStatus = probabilisticSampler.getSamplingStatus(operation, id);
    SamplingStatus lowerBoundSamplingStatus = lowerBoundSampler.getSamplingStatus(operation, id);

    if (probabilisticSamplingStatus.isSampled()) {
      return probabilisticSamplingStatus;
    }

    return lowerBoundSamplingStatus;
  }

  public void update(double samplingRate, double lowerBound){
    probabilisticSampler.update(samplingRate);
    lowerBoundSampler.update(lowerBound);
  }

  @Override
  public void close() {
    probabilisticSampler.close();
    lowerBoundSampler.close();
  }
}
