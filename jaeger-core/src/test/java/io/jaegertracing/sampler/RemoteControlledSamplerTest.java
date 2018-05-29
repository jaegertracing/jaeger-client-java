/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.jaegertracing.sampler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jaegertracing.internal.exceptions.SamplingStrategyErrorException;
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.samplers.SamplingManager;
import io.jaegertracing.internal.samplers.http.OperationSamplingParameters;
import io.jaegertracing.internal.samplers.http.PerOperationSamplingParameters;
import io.jaegertracing.internal.samplers.http.ProbabilisticSamplingStrategy;
import io.jaegertracing.internal.samplers.http.RateLimitingSamplingStrategy;
import io.jaegertracing.internal.samplers.http.SamplingStrategyResponse;
import io.jaegertracing.spi.Sampler;
import io.jaegertracing.spi.metrics.MetricsFactory;
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

  private MetricsFactory metricsFactory;
  private static final String SERVICE_NAME = "thachi mamu";

  private RemoteControlledSampler undertest;

  @Before
  public void setUp() throws Exception {
    metricsFactory = new InMemoryMetricsFactory();
    undertest = new RemoteControlledSampler.Builder(SERVICE_NAME)
        .withSamplingManager(samplingManager)
        .withInitialSampler(initialSampler)
        .withMetricsFactory(metricsFactory)
        .build();
  }

  @After
  public void tearDown() {
    undertest.close();
  }

  @Test
  public void testUpdateToProbabilisticSampler() throws Exception {
    final double samplingRate = 0.55;
    SamplingStrategyResponse probabilisticResponse = new SamplingStrategyResponse(
        new ProbabilisticSamplingStrategy(samplingRate), null, null);
    when(samplingManager.getSamplingStrategy(SERVICE_NAME)).thenReturn(probabilisticResponse);

    undertest.updateSampler();

    assertEquals(new ProbabilisticSampler(samplingRate), undertest.getSampler());
  }

  @Test
  public void testUpdateToRateLimitingSampler() throws Exception {
    final int tracesPerSecond = 22;
    SamplingStrategyResponse rateLimitingResponse = new SamplingStrategyResponse(null,
        new RateLimitingSamplingStrategy(tracesPerSecond), null);
    when(samplingManager.getSamplingStrategy(SERVICE_NAME)).thenReturn(rateLimitingResponse);

    undertest.updateSampler();

    assertEquals(new RateLimitingSampler(tracesPerSecond), undertest.getSampler());
  }

  @Test
  public void testUpdateToPerOperationSamplerReplacesProbabilisticSampler() throws Exception {
    List<PerOperationSamplingParameters> operationToSampler = new ArrayList<>();
    operationToSampler.add(new PerOperationSamplingParameters("operation",
        new ProbabilisticSamplingStrategy(0.1)));
    OperationSamplingParameters parameters = new OperationSamplingParameters(0.11, 0.22, operationToSampler);
    SamplingStrategyResponse response = new SamplingStrategyResponse(null,
        null, parameters);
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
    undertest = new RemoteControlledSampler.Builder(SERVICE_NAME)
        .withSamplingManager(samplingManager)
        .withInitialSampler(perOperationSampler)
        .withMetricsFactory(metricsFactory)
        .build();


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
    RemoteControlledSampler i2 = new RemoteControlledSampler.Builder(SERVICE_NAME)
        .withSamplingManager(samplingManager)
        .withInitialSampler(mock(Sampler.class))
        .withMetricsFactory(metricsFactory)
        .build();

    assertEquals(undertest, undertest);
    assertNotEquals(undertest, initialSampler);
    assertNotEquals(undertest, i2);
    assertNotEquals(i2, undertest);
  }

  @Test
  public void testDefaultProbabilisticSampler() {
    undertest = new RemoteControlledSampler.Builder(SERVICE_NAME)
        .withSamplingManager(samplingManager)
        .withInitialSampler(null)
        .withMetricsFactory(metricsFactory)
        .build();


    assertEquals(new ProbabilisticSampler(0.001), undertest.getSampler());
  }

}
