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

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class ProbabilisticSamplerTest {

  @Test
  public void testSamplingBoundariesPositive() {
    double samplingRate = 0.5;

    long halfwayBoundary = 0x3fffffffffffffffL;
    Sampler sampler = new ProbabilisticSampler(samplingRate);
    assertTrue(sampler.sample("", halfwayBoundary).isSampled());

    assertFalse(sampler.sample("", halfwayBoundary + 2).isSampled());
  }

  @Test
  public void testSamplingBoundariesNegative() {
    double samplingRate = 0.5;

    long halfwayBoundary = -0x4000000000000000L;
    Sampler sampler = new ProbabilisticSampler(samplingRate);
    assertTrue(sampler.sample("", halfwayBoundary).isSampled());

    assertFalse(sampler.sample("", halfwayBoundary - 1).isSampled());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSamplerThrowsInvalidSamplingRangeExceptionUnder() {
    Sampler sampler = new ProbabilisticSampler(-0.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSamplerThrowsInvalidSamplingRangeExceptionOver() {
    Sampler sampler = new ProbabilisticSampler(1.1);
  }

  @Test
  public void testTags() {
    ProbabilisticSampler sampler = new ProbabilisticSampler(0.1);
    Map<String, Object> tags = sampler.sample("vadacurry", 20L).getTags();
    assertEquals("probabilistic", tags.get("sampler.type"));
    assertEquals(0.1, tags.get("sampler.param"));
  }
}
