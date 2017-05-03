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
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.Constants;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GuaranteedThroughputSamplerTest {

  private GuaranteedThroughputSampler undertest;

  public void tearDown() {
    undertest.close();
  }

  @Test
  public void testRateLimitingLowerBound() throws Exception {
    undertest = new GuaranteedThroughputSampler(0.0001, 1.0);

    SamplingStatus samplingStatus = undertest.sample("test", Long.MAX_VALUE);
    Assert.assertTrue(samplingStatus.isSampled());
    Map<String, Object> tags = samplingStatus.getTags();

    assertEquals(tags.get(Constants.SAMPLER_TYPE_TAG_KEY), GuaranteedThroughputSampler.TYPE);
    assertEquals(tags.get(Constants.SAMPLER_PARAM_TAG_KEY), 0.0001);
  }

  @Test
  public void testProbabilityTagsOverrideRateLimitingTags() throws Exception {
    undertest = new GuaranteedThroughputSampler(0.999, 1.0);

    SamplingStatus samplingStatus = undertest.sample("test", 0L);
    Assert.assertTrue(samplingStatus.isSampled());
    Map<String, Object> tags = samplingStatus.getTags();

    assertEquals(tags.get(Constants.SAMPLER_TYPE_TAG_KEY), ProbabilisticSampler.TYPE);
    assertEquals(tags.get(Constants.SAMPLER_PARAM_TAG_KEY), 0.999);
  }

  @Test
  public void testUpdate_probabilisticSampler() {
    undertest = new GuaranteedThroughputSampler(0.001, 1);

    assertFalse(undertest.update(0.001, 1));
    assertTrue(undertest.update(0.002, 1));

    SamplingStatus samplingStatus = undertest.sample("test", Long.MAX_VALUE);
    Assert.assertTrue(samplingStatus.isSampled());
    Map<String, Object> tags = samplingStatus.getTags();

    assertEquals(tags.get(Constants.SAMPLER_TYPE_TAG_KEY), GuaranteedThroughputSampler.TYPE);
    assertEquals(tags.get(Constants.SAMPLER_PARAM_TAG_KEY), 0.002);
  }

  @Test
  public void testUpdate_rateLimitingSampler() {
    undertest = new GuaranteedThroughputSampler(0.001, 1);

    assertFalse(undertest.update(0.001, 1));
    assertTrue(undertest.update(0.001, 0));

    SamplingStatus samplingStatus = undertest.sample("test", 0L);
    Assert.assertTrue(samplingStatus.isSampled());
    Map<String, Object> tags = samplingStatus.getTags();

    assertEquals(tags.get(Constants.SAMPLER_TYPE_TAG_KEY), ProbabilisticSampler.TYPE);
    assertEquals(tags.get(Constants.SAMPLER_PARAM_TAG_KEY), 0.001);
  }
}
