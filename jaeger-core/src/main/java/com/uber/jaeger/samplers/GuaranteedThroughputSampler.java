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
import com.uber.jaeger.utils.RateLimiter;
import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * {@link GuaranteedThroughputSampler} is a {@link Sampler} that guarantees a throughput by using
 * a {@link ProbabilisticSampler} and {@link RateLimitingSampler} in tandem.
 *
 * The RateLimitingSampler is used to establish a lowerBound so that every operation is sampled
 * at least once in the time interval defined by the lowerBound.
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor (access = AccessLevel.PACKAGE) // Visible for testing
public final class GuaranteedThroughputSampler implements Sampler {
  public static final String TYPE = "lowerbound";

  private ProbabilisticSampler probabilisticSampler;
  private RateLimitingSampler lowerBoundSampler;
  private RateLimiter upperBoundRateLimiter;
  private Map<String, Object> tags;

  GuaranteedThroughputSampler(double samplingRate, double minSamplesPerSecond, double maxSamplesPerSecond) {
    tags = new HashMap<String, Object>();
    tags.put(Constants.SAMPLER_TYPE_TAG_KEY, TYPE);

    setProbabilisticSampler(samplingRate);
    lowerBoundSampler = new RateLimitingSampler(minSamplesPerSecond);
    upperBoundRateLimiter = new RateLimiter(maxSamplesPerSecond, maxSamplesPerSecond);
  }

  private synchronized void setProbabilisticSampler(double samplingRate) {
    probabilisticSampler = new ProbabilisticSampler(samplingRate);
    tags.put(Constants.SAMPLER_PARAM_TAG_KEY, samplingRate);
  }

  /**
   * Updates the probabilistic and lowerBound samplers
   * @param samplingRate The sampling rate for probabilistic sampling
   * @param minSamplesPerSecond The minimum number of samples for lower bound sampling
   * @param maxSamplesPerSecond The maximum number of samples for upper bound rate limiter
   * @return true iff any samplers were updated
   */
  public synchronized boolean update(double samplingRate, double minSamplesPerSecond, double maxSamplesPerSecond) {
    boolean isUpdated = false;
    if (samplingRate != probabilisticSampler.getSamplingRate()) {
      setProbabilisticSampler(samplingRate);
      isUpdated = true;
    }
    if (minSamplesPerSecond != lowerBoundSampler.getMaxTracesPerSecond()) {
      lowerBoundSampler = new RateLimitingSampler(minSamplesPerSecond);
      isUpdated = true;
    }
    if (maxSamplesPerSecond != upperBoundRateLimiter.getCreditsPerSecond()) {
      upperBoundRateLimiter = new RateLimiter(maxSamplesPerSecond, maxSamplesPerSecond);
      isUpdated = true;
    }
    return isUpdated;
  }

  /**
   * Calls {@link Sampler#sample(String, long)} (String, long)} on both samplers, returning true for
   * {@link SamplingStatus#isSampled} if either samplers set #isSampled to true.
   * The tags corresponding to the sampler that returned true are set on {@link SamplingStatus#tags}
   * If both samplers return true, tags for {@link ProbabilisticSampler} is given priority.
   * @param operation The operation name, which is ignored by this sampler
   * @param id The traceId on the span
   */
  @Override
  public synchronized SamplingStatus sample(String operation, long id) {
    SamplingStatus probabilisticSamplingStatus = probabilisticSampler.sample(operation, id);
    SamplingStatus lowerBoundSamplingStatus = lowerBoundSampler.sample(operation, id);

    if (probabilisticSamplingStatus.isSampled()) {
      if (!upperBoundRateLimiter.checkCredit(1)) {
        reduceSamplingRate();
      }
      return probabilisticSamplingStatus;
    }

    return SamplingStatus.of(lowerBoundSamplingStatus.isSampled(), tags);
  }

  /**
   * Due to inherent latencies in the adaptive sampling feedback loop, the new probabilities
   * are not calculated and propagated in real time. In the worst case, sampling probabilities
   * can take up to 2 minutes to propagate to the corresponding client. This means that adaptive
   * sampling cannot react to fluctuations in traffic.
   *
   * Under certain conditions, the sampling probability might increase to 100% (imagine having a
   * very low QPS operation). However, every now and then, this operation is hit with a ton of
   * traffic and all the requests are sampled before adaptive sampling can kick in and prevent
   * oversampling.
   *
   * To prevent this, we reduce the sampling probability wherever the upper bound rate limiter
   * is triggered such that clients don't over sample during traffic spikes.
   */
  private synchronized void reduceSamplingRate() {
    setProbabilisticSampler(probabilisticSampler.getSamplingRate() / 2.0);
  }

  @Override
  public synchronized void close() {
    probabilisticSampler.close();
    lowerBoundSampler.close();
  }
}
