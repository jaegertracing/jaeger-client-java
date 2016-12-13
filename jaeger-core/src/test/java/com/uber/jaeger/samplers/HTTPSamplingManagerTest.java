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

import com.uber.jaeger.samplers.http.OperationSamplingParameters;
import com.uber.jaeger.samplers.http.PerOperationSamplingParameters;
import com.uber.jaeger.samplers.http.ProbabilisticSamplingStrategy;
import com.uber.jaeger.samplers.http.RateLimitingSamplingStrategy;
import com.uber.jaeger.samplers.http.SamplingStrategyResponse;
import com.uber.jaeger.samplers.http.SamplingStrategyType;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

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

  @Test
  public void testParsePerOperationSampling() throws Exception {
    SamplingStrategyResponse response =
        undertest.parseJson(readFixture("per_operation_sampling.json"));
    OperationSamplingParameters actual = response.getOperationSampling();
    assertEquals(0.001, actual.getDefaultSamplingProbability(), 0.0001);
    assertEquals(0.001666, actual.getDefaultLowerBoundTracesPerSecond(),0.0001);

    List<PerOperationSamplingParameters> actualPerOperationStrategies = actual.getPerOperationStrategies();
    assertEquals(2, actualPerOperationStrategies.size());
    assertEquals(
        new PerOperationSamplingParameters("GET:/search", new ProbabilisticSamplingStrategy(1.0)),
        actualPerOperationStrategies.get(0));
    assertEquals(
        new PerOperationSamplingParameters("PUT:/pacifique", new ProbabilisticSamplingStrategy(0.8258308134813166)),
        actualPerOperationStrategies.get(1));
  }

  private String readFixture(String fixtureName) throws Exception {
    URL resource = getClass().getClassLoader().getResource(fixtureName);
    File file = new File(resource.toURI());
    return new String(Files.readAllBytes(file.toPath()));
  }

}
