/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.Configuration.ReporterConfiguration;
import com.uber.jaeger.Configuration.SamplerConfiguration;
import com.uber.jaeger.samplers.ConstSampler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConfigurationTest {

  @Before
  @After
  public void clearProperties() {
    // Explicitly clear all properties
    System.clearProperty(Configuration.JAEGER_AGENT_HOST);
    System.clearProperty(Configuration.JAEGER_AGENT_PORT);
    System.clearProperty(Configuration.JAEGER_REPORTER_LOG_SPANS);
    System.clearProperty(Configuration.JAEGER_REPORTER_MAX_QUEUE_SIZE);
    System.clearProperty(Configuration.JAEGER_REPORTER_FLUSH_INTERVAL);
    System.clearProperty(Configuration.JAEGER_SAMPLER_TYPE);
    System.clearProperty(Configuration.JAEGER_SAMPLER_PARAM);
    System.clearProperty(Configuration.JAEGER_SAMPLER_MANAGER_HOST_PORT);
    System.clearProperty(Configuration.JAEGER_SERVICE_NAME);
  }

  @Test
  public void testFromEnv() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    assertNotNull(Configuration.fromEnv().getTracer());
  }

  @Test
  public void testSamplerConst() {
    System.setProperty(Configuration.JAEGER_SAMPLER_TYPE, ConstSampler.TYPE);
    System.setProperty(Configuration.JAEGER_SAMPLER_PARAM, "1");
    SamplerConfiguration samplerConfig = Configuration.getSamplerConfigurationFromEnv();
    assertEquals(ConstSampler.TYPE, samplerConfig.getType());
    assertEquals(1, samplerConfig.getParam().intValue());
  }

  @Test
  public void testSamplerConstInvalidParam() {
    System.setProperty(Configuration.JAEGER_SAMPLER_TYPE, ConstSampler.TYPE);
    System.setProperty(Configuration.JAEGER_SAMPLER_PARAM, "X");
    SamplerConfiguration samplerConfig = Configuration.getSamplerConfigurationFromEnv();
    assertEquals(ConstSampler.TYPE, samplerConfig.getType());
    assertNull(samplerConfig.getParam());
  }

  @Test
  public void testReporterConfiguration() {
    System.setProperty(Configuration.JAEGER_REPORTER_LOG_SPANS, "true");
    System.setProperty(Configuration.JAEGER_AGENT_HOST, "MyHost");
    System.setProperty(Configuration.JAEGER_AGENT_PORT, "1234");
    System.setProperty(Configuration.JAEGER_REPORTER_FLUSH_INTERVAL, "500");
    System.setProperty(Configuration.JAEGER_REPORTER_MAX_QUEUE_SIZE, "1000");
    ReporterConfiguration reporterConfig = Configuration.getReporterConfigurationFromEnv();
    assertTrue(reporterConfig.getLogSpans());
    assertEquals("MyHost", reporterConfig.getAgentHost());
    assertEquals(1234, reporterConfig.getAgentPort().intValue());
    assertEquals(500, reporterConfig.getFlushIntervalMs().intValue());
    assertEquals(1000, reporterConfig.getMaxQueueSize().intValue());
  }

  @Test
  public void testReporterConfigurationInvalidFlushInterval() {
    System.setProperty(Configuration.JAEGER_REPORTER_FLUSH_INTERVAL, "X");
    ReporterConfiguration reporterConfig = Configuration.getReporterConfigurationFromEnv();
    assertNull(reporterConfig.getFlushIntervalMs());
  }

  @Test
  public void testReporterConfigurationInvalidLogSpans() {
    System.setProperty(Configuration.JAEGER_REPORTER_LOG_SPANS, "X");
    ReporterConfiguration reporterConfig = Configuration.getReporterConfigurationFromEnv();
    assertFalse(reporterConfig.getLogSpans());
  }

}
