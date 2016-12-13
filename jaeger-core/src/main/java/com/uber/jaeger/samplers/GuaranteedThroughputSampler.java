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

/**
 * {@link GuaranteedThroughputSampler} is a {@link Sampler} that guarantees a throughput by using
 * a {@link ProbabilisticSampler} and {@link RateLimitingSampler} in tandem.
 *
 * The RateLimitingSampler is used to establish a lowerBound so that every operation is sampled
 * at least one in the time interval defined by the lowerBound.
 */
public class GuaranteedThroughputSampler implements Sampler {
  private final ProbabilisticSampler probabilisticSampler;
  private final RateLimitingSampler lowerBoundSampler;

  GuaranteedThroughputSampler(double samplingRate,
                              double lowerBound) {
    this(new ProbabilisticSampler(samplingRate), new RateLimitingSampler(lowerBound));
  }

  GuaranteedThroughputSampler(ProbabilisticSampler probabilisticSampler,
                              RateLimitingSampler rateLimitingSampler) {
    this.probabilisticSampler = probabilisticSampler;
    this.lowerBoundSampler = rateLimitingSampler;
  }


  @Override
  public SamplingStatus getSamplingStatus(String ignored, long id) {
    SamplingStatus probabilisticSamplingStatus = probabilisticSampler.getSamplingStatus(ignored, id);
    SamplingStatus lowerBoundSamplingStatus = lowerBoundSampler.getSamplingStatus(ignored, id);

    if (probabilisticSamplingStatus.isSampled()) {
      return probabilisticSamplingStatus;
    }

    return lowerBoundSamplingStatus;
  }

  public void updateSamplingRate(double samplingRate){
    probabilisticSampler.update(samplingRate);
  }

  public void updateLowerBound(double lowerBound){
    lowerBoundSampler.update(lowerBound);
  }

  // Visible for testing
  double getLowerBound(){
    return lowerBoundSampler.getMaxTracesPerSecond();
  }

  // Visible for testing
  double getSamplingRate(){
    return probabilisticSampler.getSamplingRate();
  }

  @Override
  public void close() {
    probabilisticSampler.close();
    lowerBoundSampler.close();
  }
}
