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

import com.uber.jaeger.Constants;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * {@link GuaranteedThroughputSampler} is a {@link Sampler} that guarantees a throughput by using a
 * {@link ProbabilisticSampler} and {@link RateLimitingSampler} in tandem.
 *
 * <p>The RateLimitingSampler is used to establish a lowerBound so that every operation is sampled
 * at least once in the time interval defined by the lowerBound.
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PACKAGE) // Visible for testing
public final class GuaranteedThroughputSampler implements Sampler {
  public static final String TYPE = "lowerbound";

  private ProbabilisticSampler probabilisticSampler;
  private RateLimitingSampler lowerBoundSampler;
  private Map<String, Object> tags;

  GuaranteedThroughputSampler(double samplingRate, double lowerBound) {
    tags = new HashMap<String, Object>();
    tags.put(Constants.SAMPLER_TYPE_TAG_KEY, TYPE);
    tags.put(Constants.SAMPLER_PARAM_TAG_KEY, samplingRate);

    probabilisticSampler = new ProbabilisticSampler(samplingRate);
    lowerBoundSampler = new RateLimitingSampler(lowerBound);
  }

  /**
   * Updates the probabilistic and lowerBound samplers
   *
   * @param samplingRate The sampling rate for probabilistic sampling
   * @param lowerBound The lower bound limit for lower bound sampling
   * @return true iff any samplers were updated
   */
  public synchronized boolean update(double samplingRate, double lowerBound) {
    boolean isUpdated = false;
    if (samplingRate != probabilisticSampler.getSamplingRate()) {
      probabilisticSampler = new ProbabilisticSampler(samplingRate);
      tags.put(Constants.SAMPLER_PARAM_TAG_KEY, samplingRate);
      isUpdated = true;
    }
    if (lowerBound != lowerBoundSampler.getMaxTracesPerSecond()) {
      lowerBoundSampler = new RateLimitingSampler(lowerBound);
      isUpdated = true;
    }
    return isUpdated;
  }

  /**
   * Calls {@link Sampler#sample(String, long)} (String, long)} on both samplers, returning true for
   * {@link SamplingStatus#isSampled} if either samplers set #isSampled to true. The tags
   * corresponding to the sampler that returned true are set on {@link SamplingStatus#tags} If both
   * samplers return true, tags for {@link ProbabilisticSampler} is given priority.
   *
   * @param operation The operation name, which is ignored by this sampler
   * @param id The traceId on the span
   */
  @Override
  public synchronized SamplingStatus sample(String operation, long id) {
    SamplingStatus probabilisticSamplingStatus = probabilisticSampler.sample(operation, id);
    SamplingStatus lowerBoundSamplingStatus = lowerBoundSampler.sample(operation, id);

    if (probabilisticSamplingStatus.isSampled()) {
      return probabilisticSamplingStatus;
    }

    return SamplingStatus.of(lowerBoundSamplingStatus.isSampled(), tags);
  }

  @Override
  public synchronized void close() {
    probabilisticSampler.close();
    lowerBoundSampler.close();
  }
}
