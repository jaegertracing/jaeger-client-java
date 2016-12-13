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
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PerOperationSamplerTest {

  long traceId = 1L;
  String operation = "some operation";

  @Test
  public void testFallbackToDefaultProbabilisticSampler() {
    ProbabilisticSampler defaultProbabilisticSampler = mock(ProbabilisticSampler.class);
    ConcurrentHashMap<String, GuaranteedThroughputSampler>  operationToSamplers = new ConcurrentHashMap<>();
    PerOperationSampler undertest = new PerOperationSampler(100, operationToSamplers, defaultProbabilisticSampler);

    when(defaultProbabilisticSampler.getSamplingStatus(operation, traceId)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));

    SamplingStatus samplingStatus = undertest.getSamplingStatus(operation, traceId);

    assertTrue(samplingStatus.isSampled());
    verify(defaultProbabilisticSampler).getSamplingStatus(operation, traceId);
  }

  @Test
  public void testPerOperationSampler() {
    ProbabilisticSampler defaultProbabilisticSampler = mock(ProbabilisticSampler.class);
    ConcurrentHashMap<String, GuaranteedThroughputSampler>  operationToSamplers = new ConcurrentHashMap<>();
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(operation, guaranteedThroughputSampler);

    PerOperationSampler undertest = new PerOperationSampler(100, operationToSamplers, defaultProbabilisticSampler);

    when(defaultProbabilisticSampler.getSamplingStatus(operation, traceId)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));
    when(guaranteedThroughputSampler.getSamplingStatus(operation, traceId)).thenReturn(SamplingStatus.of(true, new HashMap<String, Object>()));

    SamplingStatus samplingStatus = undertest.getSamplingStatus(operation, traceId);
    assertTrue(samplingStatus.isSampled());
    verify(guaranteedThroughputSampler).getSamplingStatus(operation, traceId);
    verifyNoMoreInteractions(defaultProbabilisticSampler);
  }

  @Test
  public void testUpdate(){
    ProbabilisticSampler defaultProbabilisticSampler = mock(ProbabilisticSampler.class);
    ConcurrentHashMap<String, GuaranteedThroughputSampler>  operationToSamplers = new ConcurrentHashMap<>();
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(operation, guaranteedThroughputSampler);

    PerOperationSampler undertest = new PerOperationSampler(100, operationToSamplers, defaultProbabilisticSampler);
    PerOperationSamplingParameters perOperationSamplingParameters = new PerOperationSamplingParameters(operation, new ProbabilisticSamplingStrategy(0.31415));
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(perOperationSamplingParameters);

    undertest.update(new OperationSamplingParameters(0.512, 2.0, parametersList));

    verify(guaranteedThroughputSampler).update(0.31415, 2.0);
    verify(defaultProbabilisticSampler).update(0.512);
  }

  @Test
  public void testUpdateIgnoreGreaterThanMax(){
    ProbabilisticSampler defaultProbabilisticSampler = mock(ProbabilisticSampler.class);
    ConcurrentHashMap<String, GuaranteedThroughputSampler>  operationToSamplers = new ConcurrentHashMap<>();
    GuaranteedThroughputSampler guaranteedThroughputSampler = mock(GuaranteedThroughputSampler.class);
    operationToSamplers.put(operation, guaranteedThroughputSampler);

    PerOperationSampler undertest = new PerOperationSampler(1, operationToSamplers, defaultProbabilisticSampler);
    PerOperationSamplingParameters perOperationSamplingParameters1 = new PerOperationSamplingParameters(operation, new ProbabilisticSamplingStrategy(0.31415));
    PerOperationSamplingParameters perOperationSamplingParameters2 = new PerOperationSamplingParameters("second operation", new ProbabilisticSamplingStrategy(0.31415));
    List<PerOperationSamplingParameters> parametersList = new ArrayList<>();
    parametersList.add(perOperationSamplingParameters1);
    parametersList.add(perOperationSamplingParameters2);

    undertest.update(new OperationSamplingParameters(0.512, 2.0, parametersList));

    assertEquals(1, operationToSamplers.size());
    assertNotNull(operationToSamplers.get(operation));
  }

}
