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
import java.util.concurrent.ConcurrentHashMap;

@RunWith(MockitoJUnitRunner.class)
public class PerOperationSamplerTest {

  private static final double SAMPLING_RATE = 0.31415;
  private static final double DEFAULT_SAMPLING_PROBABILITY = 0.512;
  private static final double DEFAULT_LOWER_BOUND_TRACES_PER_SECOND = 2.0;
  private long traceId = 1L;
  private String operation = "some operation";

  @Mock private ProbabilisticSampler defaultProbabilisticSampler;
  private ConcurrentHashMap<String, GuaranteedThroughputSampler>  operationToSamplers = new ConcurrentHashMap<>();
  private PerOperationSampler undertest;

  @Before
  public void setUp(){
    undertest = new PerOperationSampler(100, operationToSamplers, defaultProbabilisticSampler);
    when(defaultProbabilisticSampler.sample(operation, traceId)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));
  }

  @After
  public void tearDown() {
    undertest.close();
  }

  @Test
  public void testFallbackToDefaultProbabilisticSampler() {
    SamplingStatus samplingStatus = undertest.sample(operation, traceId);
    assertTrue(samplingStatus.isSampled());
    verify(defaultProbabilisticSampler).sample(operation, traceId);
  }

  @Test
  public void testPerOperationSampler() {
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(operation, guaranteedThroughputSampler);

    when(guaranteedThroughputSampler.sample(operation, traceId)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));

    SamplingStatus samplingStatus = undertest.sample(operation, traceId);
    assertTrue(samplingStatus.isSampled());
    verify(guaranteedThroughputSampler).sample(operation, traceId);
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

    undertest.update(new OperationSamplingParameters(DEFAULT_SAMPLING_PROBABILITY,
                                                     DEFAULT_LOWER_BOUND_TRACES_PER_SECOND, parametersList));

    verify(guaranteedThroughputSampler).update(SAMPLING_RATE, DEFAULT_LOWER_BOUND_TRACES_PER_SECOND);
    verify(defaultProbabilisticSampler).update(DEFAULT_SAMPLING_PROBABILITY);
  }

  @Test
  public void testUpdateIgnoreGreaterThanMax(){
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(operation, guaranteedThroughputSampler);

    PerOperationSampler undertest = new PerOperationSampler(1, operationToSamplers, defaultProbabilisticSampler);
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
