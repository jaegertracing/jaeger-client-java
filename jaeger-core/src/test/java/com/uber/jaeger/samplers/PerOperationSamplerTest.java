package com.uber.jaeger.samplers;

import com.uber.jaeger.samplers.http.OperationSamplingParameters;
import com.uber.jaeger.samplers.http.PerOperationSamplingParameters;
import com.uber.jaeger.samplers.http.ProbabilisticSamplingStrategy;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PerOperationSamplerTest {

  List<PerOperationSamplingParameters> strategies = new ArrayList<>();
  OperationSamplingParameters samplingStrategy = new OperationSamplingParameters(1.0, 1, strategies);
  PerOperationSampler undertest = new PerOperationSampler(100, samplingStrategy);
  Random random = new Random();

  @Test
  public void testB() {
    strategies.add(new PerOperationSamplingParameters("test", new ProbabilisticSamplingStrategy(1)));
    undertest.update(samplingStrategy);
    SamplingStatus test = undertest.getSamplingStatus("test", random.nextLong());
    Assert.assertFalse(undertest.getOperationNameToSampler().isEmpty());
  }

}
