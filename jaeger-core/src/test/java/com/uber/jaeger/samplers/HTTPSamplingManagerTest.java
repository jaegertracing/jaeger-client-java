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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.uber.jaeger.samplers.http.ProbabilisticSamplingStrategy;
import com.uber.jaeger.samplers.http.RateLimitingSamplingStrategy;
import com.uber.jaeger.samplers.http.SamplingStrategyResponse;
import com.uber.jaeger.samplers.http.SamplingStrategyType;
import org.junit.Test;

public class HTTPSamplingManagerTest {

  HTTPSamplingManager undertest = new HTTPSamplingManager(null);

  @Test
  public void testParseProbabilisticSampling() {
    SamplingStrategyResponse response =
        undertest.parseJson(
            "{\"strategyType\":0,"
                + "\"probabilisticSampling\":{\"samplingRate\":0.01},"
                + "\"rateLimitingSampling\":null}");
    assertEquals(SamplingStrategyType.PROBABILISTIC, response.getStrategyType());
    assertEquals(new ProbabilisticSamplingStrategy(0.01), response.getProbabilisticSampling());
    assertNull(response.getRateLimitingSampling());
  }

  @Test
  public void testParseRateLimitingSampling() {
    SamplingStrategyResponse response =
        undertest.parseJson(
            "{\"strategyType\":1,"
                + "\"probabilisticSampling\":null,"
                + "\"rateLimitingSampling\":{\"maxTracesPerSecond\":1}}");
    assertEquals(SamplingStrategyType.RATE_LIMITING, response.getStrategyType());
    assertEquals(new RateLimitingSamplingStrategy(1), response.getRateLimitingSampling());
    assertNull(response.getProbabilisticSampling());
  }

}
