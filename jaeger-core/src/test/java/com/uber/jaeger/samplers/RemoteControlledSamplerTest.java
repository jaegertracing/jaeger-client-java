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

import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.thrift.sampling_manager.SamplingStrategyResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteControlledSamplerTest {
  MockAgent agent;
  Metrics metrics;

  @Before
  public void setUp() throws Exception {
    agent = new MockAgent();
    agent.start();
    metrics = Metrics.fromStatsReporter(new InMemoryStatsReporter());
  }

  @After
  public void tearDown() {
    agent.stop();
  }

  @Test
  public void testGetProbabilisticRemoteSamplingStrategy() throws Exception {
    double expectedSamplingRate = 0.001;
    double delta = expectedSamplingRate / 100;
    HTTPSamplingManager manager = new HTTPSamplingManager("localhost:" + agent.getPort());
    SamplingStrategyResponse response = manager.getSamplingStrategy("clairvoyant");

    assertEquals(
        response.getProbabilisticSampling().getSamplingRate(), expectedSamplingRate, delta);
  }

  @Test
  public void testGetRateLimitingRemoteSamplingStrategy() throws Exception {
    int expectedMaxTracesPerSecond = 8;
    HTTPSamplingManager manager = new HTTPSamplingManager("localhost:" + agent.getPort());
    SamplingStrategyResponse response = manager.getSamplingStrategy("jaeger");
    assertEquals(
        response.getRateLimitingSampling().getMaxTracesPerSecond(), expectedMaxTracesPerSecond);
  }

  private RemoteControlledSampler makeSamplerWith(Sampler underlyingSampler) throws Exception {
    HTTPSamplingManager manager = mock(HTTPSamplingManager.class);
    SamplingStrategyResponse response = mock(SamplingStrategyResponse.class);
    when(response.isSetProbabilisticSampling()).thenReturn(false);
    when(response.isSetRateLimitingSampling()).thenReturn(false);
    when(manager.getSamplingStrategy("jaeger")).thenReturn(response);
    return new RemoteControlledSampler("jaeger", manager, underlyingSampler, metrics);
  }

  private void testEqualsAgainstSamplers(Sampler trueSampler, Sampler falseSampler)
      throws Exception {
    RemoteControlledSampler sampler = makeSamplerWith(trueSampler);
    RemoteControlledSampler otherSampler = makeSamplerWith(trueSampler);

    assertTrue(sampler.equals(otherSampler));

    otherSampler = makeSamplerWith(falseSampler);

    assertFalse(sampler.equals(otherSampler));
  }

  @Test
  public void testEquals() throws Exception {
    testEqualsAgainstSamplers(new ConstSampler(true), new ConstSampler(false));
    testEqualsAgainstSamplers(new ProbabilisticSampler(0.1), new ProbabilisticSampler(0.01));
    testEqualsAgainstSamplers(new RateLimitingSampler(4), new RateLimitingSampler(5));
  }

  @Test
  public void testTags() throws Exception {
    RemoteControlledSampler sampler = makeSamplerWith(new ProbabilisticSampler(0.5));
    assertEquals("probabilistic", sampler.getTags().get("sampler.type"));
    assertEquals(0.5, sampler.getTags().get("sampler.param"));
  }
}
