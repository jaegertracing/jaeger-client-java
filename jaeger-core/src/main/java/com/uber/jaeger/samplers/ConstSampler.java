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
public class ConstSampler implements Sampler {
  public static final String TYPE = "const";

  private final boolean decision;

  private final Map<String, Object> tags;

  public ConstSampler(boolean decision) {
    this.decision = decision;
    Map<String, Object> tags = new HashMap<>();
    tags.put(Constants.SAMPLER_TYPE_TAG_KEY, TYPE);
    tags.put(Constants.SAMPLER_PARAM_TAG_KEY, decision);
    this.tags = Collections.unmodifiableMap(tags);
  }

  /**
   * @param id A long that represents the traceid used to make a sampling decision the command line
   * arguments.
   * @return A boolean that says whether to sample.
   */
  @Override
  public SamplingStatus getSamplingStatus(String operation, long id) {
    return SamplingStatus.of(decision, tags);
  }

  // Visible for testing
  Map<String, Object> getTags() {
    return this.tags;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other instanceof ConstSampler) {
      return this.decision == ((ConstSampler) other).decision;
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
