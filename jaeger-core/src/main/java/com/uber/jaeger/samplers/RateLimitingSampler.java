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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RateLimitingSampler implements Sampler {
  public static final String TYPE = "ratelimiting";

  private final RateLimiter rateLimiter;
  private final int maxTracesPerSecond;
  private final Map<String, Object> tags;

  public RateLimitingSampler(int maxTracesPerSecond) {
    this.maxTracesPerSecond = maxTracesPerSecond;
    this.rateLimiter = new RateLimiter(maxTracesPerSecond);

    Map<String, Object> tags = new HashMap<>();
    tags.put(Constants.SAMPLER_TYPE_TAG_KEY, TYPE);
    tags.put(Constants.SAMPLER_PARAM_TAG_KEY, maxTracesPerSecond);
    this.tags = Collections.unmodifiableMap(tags);
  }

  @Override
  public boolean isSampled(long id) {
    return this.rateLimiter.checkCredit(1.0);
  }

  @Override
  public Map<String, Object> getTags() {
    return tags;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other instanceof RateLimitingSampler) {
      return this.maxTracesPerSecond == ((RateLimitingSampler) other).maxTracesPerSecond;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = rateLimiter != null ? rateLimiter.hashCode() : 0;
    result = 31 * result + maxTracesPerSecond;
    result = 31 * result + (tags != null ? tags.hashCode() : 0);
    return result;
  }

  /**
   * Only implemented to maintain compatibility with sampling interface.
   */
  @Override
  public void close() {}
}
