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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.uber.jaeger.samplers.http.OperationSamplingParameters;
import com.uber.jaeger.samplers.http.PerOperationSamplingParameters;
import com.uber.jaeger.samplers.http.ProbabilisticSamplingStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class PerOperationSamplerTest {

  private final double SAMPLING_RATE = 0.31415;
  private final double DEFAULT_SAMPLING_PROBABILITY = 0.512;
  private final double DEFAULT_LOWER_BOUND_TRACES_PER_SECOND = 2.0;
  private final int MAX_OPERATIONS = 100;
  private final long traceId = 1L;
  private final String operation = "some operation";

  @Mock private ProbabilisticSampler defaultProbabilisticSampler;
  private HashMap<String, GuaranteedThroughputSampler>  operationToSamplers = new HashMap<>();
  private PerOperationSampler undertest;

  @Before
  public void setUp(){
    undertest = new PerOperationSampler(MAX_OPERATIONS, operationToSamplers, defaultProbabilisticSampler, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);
    when(defaultProbabilisticSampler.sample(operation, traceId)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));
    when(defaultProbabilisticSampler.getSamplingRate()).thenReturn(DEFAULT_SAMPLING_PROBABILITY);
  }

  @After
  public void tearDown() {
    undertest.close();
  }

  @Test
  public void testFallbackToDefaultProbabilisticSampler() {
    undertest = new PerOperationSampler(0, operationToSamplers, defaultProbabilisticSampler, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);
    SamplingStatus samplingStatus = undertest.sample(operation, traceId);
    assertTrue(samplingStatus.isSampled());
    verify(defaultProbabilisticSampler).sample(operation, traceId);
  }

  @Test
  public void testCreateGuaranteedSamplerOnUnseenOperation() {
    String newOperation = "new operation";
    undertest.sample(newOperation, traceId);
    assertEquals(new GuaranteedThroughputSampler(DEFAULT_SAMPLING_PROBABILITY,
                                                 DEFAULT_LOWER_BOUND_TRACES_PER_SECOND),
                 operationToSamplers.get(newOperation));
  }

  @Test
  public void testPerOperationSamplerWithKnownOperation() {
    GuaranteedThroughputSampler sampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(operation, sampler);

    when(sampler.sample(operation, traceId)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));

    SamplingStatus samplingStatus = undertest.sample(operation, traceId);
    assertTrue(samplingStatus.isSampled());
    verify(sampler).sample(operation, traceId);
    verifyNoMoreInteractions(defaultProbabilisticSampler);
  }

  @Test
  public void testUpdate(){
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(operation, guaranteedThroughputSampler);

    PerOperationSamplingParameters perOperationSamplingParameters =
        new PerOperationSamplingParameters(operation, new ProbabilisticSamplingStrategy(SAMPLING_RATE));
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(perOperationSamplingParameters);

    OperationSamplingParameters parameters =
        new OperationSamplingParameters(DEFAULT_SAMPLING_PROBABILITY,
                                        DEFAULT_LOWER_BOUND_TRACES_PER_SECOND, parametersList);

    assertTrue(undertest.update(parameters));
    //Checks that creating a new instance with the given parameters is the same as updating an existing instance
    assertEquals(new PerOperationSampler(MAX_OPERATIONS, parameters), undertest);
  }

  @Test
  public void testNoopUpdate(){
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    OperationSamplingParameters parameters = new OperationSamplingParameters(0, 0, parametersList);
    assertFalse(undertest.update(parameters));
  }

  @Test
  public void testUpdateIgnoreGreaterThanMax(){
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(operation, guaranteedThroughputSampler);

    PerOperationSampler undertest = new PerOperationSampler(1, operationToSamplers, defaultProbabilisticSampler, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);
    PerOperationSamplingParameters perOperationSamplingParameters1 =
        new PerOperationSamplingParameters(operation, new ProbabilisticSamplingStrategy(SAMPLING_RATE));
    PerOperationSamplingParameters perOperationSamplingParameters2 =
        new PerOperationSamplingParameters("second operation", new ProbabilisticSamplingStrategy(SAMPLING_RATE));
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(perOperationSamplingParameters1);
    parametersList.add(perOperationSamplingParameters2);

    undertest.update(new OperationSamplingParameters(DEFAULT_SAMPLING_PROBABILITY,
                                                     DEFAULT_LOWER_BOUND_TRACES_PER_SECOND, parametersList));

    assertEquals(1, operationToSamplers.size());
    assertNotNull(operationToSamplers.get(operation));
  }

  @Test
  public void testUpdateAddOperation(){
    PerOperationSamplingParameters perOperationSamplingParameters1 =
        new PerOperationSamplingParameters(operation, new ProbabilisticSamplingStrategy(SAMPLING_RATE));
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(perOperationSamplingParameters1);

    undertest.update(new OperationSamplingParameters(DEFAULT_SAMPLING_PROBABILITY,
                                                     DEFAULT_LOWER_BOUND_TRACES_PER_SECOND,
                                                     parametersList));

    assertEquals(1, operationToSamplers.size());
    assertEquals(new GuaranteedThroughputSampler(SAMPLING_RATE,
                                                 DEFAULT_LOWER_BOUND_TRACES_PER_SECOND),
                 operationToSamplers.get(operation));
  }

}
