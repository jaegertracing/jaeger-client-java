/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger.baggage;

import com.uber.jaeger.metrics.Metrics;

import java.util.HashMap;
import java.util.Map;

public class DefaultBaggageRestrictionManager extends BaggageRestrictionManager {
  private Map<String, BaggageSetter> baggageSetters;
  private final int maxValueLength;
  private final Metrics metrics;

  public DefaultBaggageRestrictionManager(Metrics metrics) {
    this(metrics, DEFAULT_MAX_VALUE_LENGTH);
  }

  public DefaultBaggageRestrictionManager(Metrics metrics, int maxValueLength) {
    this.maxValueLength = maxValueLength;
    this.metrics = metrics;
    this.baggageSetters = new HashMap<String, BaggageSetter>();
  }

  @Override
  public BaggageSetter getBaggageSetter(String key) {
    BaggageSetter setter = baggageSetters.get(key);
    if (setter == null) {
      setter = BaggageSetter.of(key, true, maxValueLength, metrics);
      baggageSetters.put(key, setter);
    }
    return setter;
  }
}
