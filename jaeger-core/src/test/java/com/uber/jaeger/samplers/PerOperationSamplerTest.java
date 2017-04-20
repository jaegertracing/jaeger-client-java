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
import com.uber.jaeger.samplers.http.ProbabilisticSamplingParameters;
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
  private final double DELTA = 0.001;
  private final long TRACE_ID = 1L;
  private final String OPERATION = "some OPERATION";

  @Mock private ProbabilisticSampler defaultProbabilisticSampler;
  private HashMap<String, GuaranteedThroughputSampler> operationToSamplers = new HashMap<>();
  private PerOperationSampler undertest;

  @Before
  public void setUp(){
    undertest = new PerOperationSampler(MAX_OPERATIONS, operationToSamplers, defaultProbabilisticSampler, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);
    when(defaultProbabilisticSampler.sample(OPERATION, TRACE_ID)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));
    when(defaultProbabilisticSampler.getSamplingRate()).thenReturn(DEFAULT_SAMPLING_PROBABILITY);
  }

  @After
  public void tearDown() {
    undertest.close();
  }

  @Test
  public void testFallbackToDefaultProbabilisticSampler() {
    undertest = new PerOperationSampler(0, operationToSamplers, defaultProbabilisticSampler, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);
    SamplingStatus samplingStatus = undertest.sample(OPERATION, TRACE_ID);
    assertTrue(samplingStatus.isSampled());
    verify(defaultProbabilisticSampler).sample(OPERATION, TRACE_ID);
  }

  @Test
  public void testCreateGuaranteedSamplerOnUnseenOperation() {
    String newOperation = "new OPERATION";
    undertest.sample(newOperation, TRACE_ID);
    assertEquals(new GuaranteedThroughputSampler(DEFAULT_SAMPLING_PROBABILITY,
                                                 DEFAULT_LOWER_BOUND_TRACES_PER_SECOND),
                 operationToSamplers.get(newOperation));
  }

  @Test
  public void testPerOperationSamplerWithKnownOperation() {
    GuaranteedThroughputSampler sampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(OPERATION, sampler);

    when(sampler.sample(OPERATION, TRACE_ID)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));

    SamplingStatus samplingStatus = undertest.sample(OPERATION, TRACE_ID);
    assertTrue(samplingStatus.isSampled());
    verify(sampler).sample(OPERATION, TRACE_ID);
    verifyNoMoreInteractions(defaultProbabilisticSampler);
  }

  @Test
  public void testUpdate(){
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(OPERATION, guaranteedThroughputSampler);

    PerOperationSamplingParameters perOperationSamplingParameters =
        new PerOperationSamplingParameters(OPERATION, new ProbabilisticSamplingParameters(SAMPLING_RATE));
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(perOperationSamplingParameters);

    OperationSamplingParameters parameters =
        new OperationSamplingParameters(DEFAULT_SAMPLING_PROBABILITY,
            DEFAULT_LOWER_BOUND_TRACES_PER_SECOND, parametersList);

    assertTrue(undertest.update(parameters));
    verify(guaranteedThroughputSampler).update(SAMPLING_RATE, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);
    verifyNoMoreInteractions(guaranteedThroughputSampler);
  }

  @Test
  public void testNoopUpdate(){
    ProbabilisticSampler defaultProbabilisticSampler = new ProbabilisticSampler(DEFAULT_SAMPLING_PROBABILITY);
    double operationSamplingRate = 0.23;
    operationToSamplers.put(OPERATION, new GuaranteedThroughputSampler(operationSamplingRate, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND));
    undertest = new PerOperationSampler(MAX_OPERATIONS, operationToSamplers, defaultProbabilisticSampler, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);

    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(new PerOperationSamplingParameters(OPERATION, new ProbabilisticSamplingParameters(operationSamplingRate)));
    OperationSamplingParameters parameters = new OperationSamplingParameters(DEFAULT_SAMPLING_PROBABILITY, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND, parametersList);

    assertFalse(undertest.update(parameters));
    assertEquals(operationToSamplers, undertest.getOperationNameToSampler());
    assertEquals(DEFAULT_LOWER_BOUND_TRACES_PER_SECOND, undertest.getLowerBound(), DELTA);
    assertEquals(DEFAULT_SAMPLING_PROBABILITY, undertest.getDefaultSampler().getSamplingRate(), DELTA);
  }

  @Test
  public void testUpdateIgnoreGreaterThanMax(){
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(OPERATION, guaranteedThroughputSampler);

    PerOperationSampler undertest = new PerOperationSampler(1, operationToSamplers, defaultProbabilisticSampler, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);
    PerOperationSamplingParameters perOperationSamplingParameters1 =
        new PerOperationSamplingParameters(OPERATION, new ProbabilisticSamplingParameters(SAMPLING_RATE));
    PerOperationSamplingParameters perOperationSamplingParameters2 =
        new PerOperationSamplingParameters("second OPERATION", new ProbabilisticSamplingParameters(SAMPLING_RATE));
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(perOperationSamplingParameters1);
    parametersList.add(perOperationSamplingParameters2);

    undertest.update(new OperationSamplingParameters(DEFAULT_SAMPLING_PROBABILITY,
                                                     DEFAULT_LOWER_BOUND_TRACES_PER_SECOND, parametersList));

    assertEquals(1, operationToSamplers.size());
    assertNotNull(operationToSamplers.get(OPERATION));
  }

  @Test
  public void testUpdateAddOperation(){
    PerOperationSamplingParameters perOperationSamplingParameters1 =
        new PerOperationSamplingParameters(OPERATION, new ProbabilisticSamplingParameters(SAMPLING_RATE));
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(perOperationSamplingParameters1);

    undertest.update(new OperationSamplingParameters(DEFAULT_SAMPLING_PROBABILITY,
                                                     DEFAULT_LOWER_BOUND_TRACES_PER_SECOND,
                                                     parametersList));

    assertEquals(1, operationToSamplers.size());
    assertEquals(new GuaranteedThroughputSampler(SAMPLING_RATE,
                                                 DEFAULT_LOWER_BOUND_TRACES_PER_SECOND),
                 operationToSamplers.get(OPERATION));
  }

}
