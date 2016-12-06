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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.ToString;

@SuppressWarnings("EqualsHashCode")
@ToString
public class ProbabilisticSampler implements Sampler {
  public static final String TYPE = "probabilistic";

  private final long positiveSamplingBoundary;
  private final long negativeSamplingBoundary;
  private final double samplingRate;
  private final Map<String, Object> tags;

  public ProbabilisticSampler(double samplingRate) {
    if (samplingRate < 0.0 || samplingRate > 1.0) {
      throw new IllegalArgumentException(
          "The sampling rate must be greater than 0.0 and less than 1.0");
    }

    this.samplingRate = samplingRate;
    this.positiveSamplingBoundary = (long) (((1L << 63) - 1) * samplingRate);
    this.negativeSamplingBoundary = (long) ((1L << 63) * samplingRate);

    Map<String, Object> tags = new HashMap<>();
    tags.put(Constants.SAMPLER_TYPE_TAG_KEY, TYPE);
    tags.put(Constants.SAMPLER_PARAM_TAG_KEY, samplingRate);
    this.tags = Collections.unmodifiableMap(tags);
  }

  /**
   * Uses a trace id to make a sampling decision.
   *
   * @param id A long that represents the traceid used to make a sampling decision
   * @return A boolean that says wheter or not to sample.
   */
  @Override
  public SamplingStatus getSamplingStatus(String operation, long id) {
    if (id > 0) {
      return SamplingStatus.of(id <= this.positiveSamplingBoundary, tags);
    } else {
      return SamplingStatus.of(id >= this.negativeSamplingBoundary, tags);
    }
  }

  @Override
  public Map<String, Object> getTags() {
    return tags;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other instanceof ProbabilisticSampler) {
      return this.samplingRate == ((ProbabilisticSampler) other).samplingRate;
    }
    return false;
  }

  /**
   * Only implemented to satisfy the sampler interface
   */
  @Override
  public void close() {
    // nothing to do
  }
}
