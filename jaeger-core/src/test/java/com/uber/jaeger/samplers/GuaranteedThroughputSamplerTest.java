package com.uber.jaeger.samplers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.uber.jaeger.Constants;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class GuaranteedThroughputSamplerTest {

  @Test
  public void testRateLimitingLowerBound() throws Exception {
    GuaranteedThroughputSampler undertest = new GuaranteedThroughputSampler(0.0001, 1.0);

    SamplingStatus samplingStatus = undertest.getSamplingStatus("test", Long.MAX_VALUE);
    Assert.assertTrue(samplingStatus.isSampled());
    Map<String, Object> tags = samplingStatus.getTags();

    assertEquals(tags.get(Constants.SAMPLER_TYPE_TAG_KEY), RateLimitingSampler.TYPE);
    assertEquals(tags.get(Constants.SAMPLER_PARAM_TAG_KEY), 1.0);
  }

  @Test
  public void testProbabilityTagsOverrideRateLimitingTags() throws Exception {
    GuaranteedThroughputSampler undertest = new GuaranteedThroughputSampler(0.999, 1.0);

    SamplingStatus samplingStatus = undertest.getSamplingStatus("test", 0L);
    Assert.assertTrue(samplingStatus.isSampled());
    Map<String, Object> tags = samplingStatus.getTags();

    assertEquals(tags.get(Constants.SAMPLER_TYPE_TAG_KEY), ProbabilisticSampler.TYPE);
    assertEquals(tags.get(Constants.SAMPLER_PARAM_TAG_KEY), 0.999);
  }

  @Test
  public void testLowerBoundUpdate() throws Exception {
    ProbabilisticSampler probabilisticSampler = mock(ProbabilisticSampler.class);
    RateLimitingSampler rateLimitingSampler = mock(RateLimitingSampler.class);
    GuaranteedThroughputSampler undertest = new GuaranteedThroughputSampler(probabilisticSampler, rateLimitingSampler);
    undertest.updateLowerBound(2.0);
    verify(rateLimitingSampler).update(2.0);
    verifyNoMoreInteractions(probabilisticSampler);
 }

 @Test
  public void testSamplingRateUpdate() throws Exception {
    ProbabilisticSampler probabilisticSampler = mock(ProbabilisticSampler.class);
    RateLimitingSampler rateLimitingSampler = mock(RateLimitingSampler.class);
    GuaranteedThroughputSampler undertest = new GuaranteedThroughputSampler(probabilisticSampler, rateLimitingSampler);
    undertest.updateSamplingRate(1.234);
    verify(probabilisticSampler).update(1.234);
    verifyNoMoreInteractions(rateLimitingSampler);
  }


}
