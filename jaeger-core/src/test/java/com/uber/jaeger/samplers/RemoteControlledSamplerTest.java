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
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uber.jaeger.exceptions.SamplingStrategyErrorException;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.samplers.http.OperationSamplingParameters;
import com.uber.jaeger.samplers.http.PerOperationSamplingParameters;
import com.uber.jaeger.samplers.http.ProbabilisticSamplingStrategy;
import com.uber.jaeger.samplers.http.RateLimitingSamplingStrategy;
import com.uber.jaeger.samplers.http.SamplingStrategyResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RemoteControlledSamplerTest {
  @Mock private SamplingManager samplingManager;
  @Mock private Sampler initialSampler;
  private Metrics metrics;
  private final String SERVICE_NAME = "thachi mamu";

  private RemoteControlledSampler undertest;

  @Before
  public void setUp() throws Exception {
    metrics = Metrics.fromStatsReporter(new InMemoryStatsReporter());
    undertest = new RemoteControlledSampler(SERVICE_NAME, samplingManager, initialSampler, metrics);
  }

  @After
  public void tearDown() {
    undertest.close();
  }

  @Test
  public void testUpdateToProbabilisticSampler() throws Exception {
    final double samplingRate = 0.55;
    SamplingStrategyResponse probabilisticResponse = new SamplingStrategyResponse(new ProbabilisticSamplingStrategy(samplingRate), null, null);
    when(samplingManager.getSamplingStrategy(SERVICE_NAME)).thenReturn(probabilisticResponse);

    undertest.updateSampler();

    assertEquals(new ProbabilisticSampler(samplingRate), undertest.getSampler());
  }

  @Test
  public void testUpdateToRateLimitingSampler() throws Exception {
    final int tracesPerSecond = 22;
    SamplingStrategyResponse rateLimitingResponse = new SamplingStrategyResponse(null, new RateLimitingSamplingStrategy(tracesPerSecond), null);
    when(samplingManager.getSamplingStrategy(SERVICE_NAME)).thenReturn(rateLimitingResponse);

    undertest.updateSampler();

    assertEquals(new RateLimitingSampler(tracesPerSecond), undertest.getSampler());
  }

  @Test
  public void testUpdateToPerOperationSamplerReplacesProbabilisticSampler() throws Exception {
    List<PerOperationSamplingParameters> operationToSampler = new ArrayList<>();
    operationToSampler.add(new PerOperationSamplingParameters("operation", new ProbabilisticSamplingStrategy(0.1)));
    OperationSamplingParameters parameters = new OperationSamplingParameters(0.11, 0.22, operationToSampler);
    SamplingStrategyResponse response = new SamplingStrategyResponse(null, null, parameters);
    when(samplingManager.getSamplingStrategy(SERVICE_NAME)).thenReturn(response);

    undertest.updateSampler();

    PerOperationSampler perOperationSampler = new PerOperationSampler(2000, parameters);
    Sampler actualSampler = undertest.getSampler();
    assertEquals(perOperationSampler, actualSampler);
  }

  @Test
  public void testUpdatePerOperationSamplerUpdatesExistingPerOperationSampler() throws Exception {
    PerOperationSampler perOperationSampler = mock(PerOperationSampler.class);
    OperationSamplingParameters parameters = mock(OperationSamplingParameters.class);
    when(samplingManager.getSamplingStrategy(SERVICE_NAME)).thenReturn(
        new SamplingStrategyResponse(null, null, parameters));
    undertest = new RemoteControlledSampler(SERVICE_NAME, samplingManager, perOperationSampler, metrics);

    undertest.updateSampler();
    Thread.sleep(20);
    //updateSampler is hit once automatically because of the pollTimer
    verify(perOperationSampler, times(2)).update(parameters);
  }

  @Test
  public void testNullResponse() throws Exception {
    when(samplingManager.getSamplingStrategy(SERVICE_NAME)).thenReturn(new SamplingStrategyResponse(null, null, null));
    undertest.updateSampler();
    assertEquals(initialSampler, undertest.getSampler());
  }

  @Test
  public void testUnparseableResponse() throws Exception {
    when(samplingManager.getSamplingStrategy(SERVICE_NAME)).thenThrow(new SamplingStrategyErrorException("test"));
    undertest.updateSampler();
    assertEquals(initialSampler, undertest.getSampler());
  }

  @Test
  public void testSample() throws Exception {
    undertest.sample("op", 1L);
    verify(initialSampler).sample("op", 1L);
  }

  @Test
  public void testEquals() {
    RemoteControlledSampler i2 = new RemoteControlledSampler(SERVICE_NAME, samplingManager, mock(Sampler.class), metrics);

    assertEquals(undertest, undertest);
    assertNotEquals(undertest, initialSampler);
    assertNotEquals(undertest, i2);
    assertNotEquals(i2, undertest);
  }

  @Test
  public void testDefaultProbabilisticSampler() {
    undertest = new RemoteControlledSampler(SERVICE_NAME, samplingManager, null, metrics);
    assertEquals(new ProbabilisticSampler(0.001), undertest.getSampler());
  }

}
