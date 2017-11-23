/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.Configuration.ReporterConfiguration;
import com.uber.jaeger.Configuration.SamplerConfiguration;
import com.uber.jaeger.samplers.ConstSampler;

import com.uber.jaeger.senders.HttpSender;
import com.uber.jaeger.senders.Sender;
import io.opentracing.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import java.net.SocketException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConfigurationTest {

  private static final String TEST_PROPERTY = "TestProperty";

  @Before
  @After
  public void clearProperties() throws NoSuchFieldException, IllegalAccessException {
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
    System.clearProperty(Configuration.JAEGER_TAGS);
    System.clearProperty(Configuration.JAEGER_DISABLE_GLOBAL_TRACER);
    System.clearProperty(Configuration.JAEGER_ENDPOINT);
    System.clearProperty(Configuration.JAEGER_AUTH_TOKEN);
    System.clearProperty(Configuration.JAEGER_USER);
    System.clearProperty(Configuration.JAEGER_PASSWORD);

    System.clearProperty(TEST_PROPERTY);

    // Reset opentracing's global tracer
    Field field = GlobalTracer.class.getDeclaredField("tracer");
    field.setAccessible(true);
    field.set(null, NoopTracerFactory.create());
  }

  @Test
  public void testFromEnv() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    assertNotNull(Configuration.fromEnv().getTracer());
    assertTrue(GlobalTracer.isRegistered());
  }

  @Test (expected = IllegalStateException.class)
  public void testFromEnvWithoutDisabledTracer() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    assertNotNull(Configuration.fromEnv().getTracer());
    assertTrue(GlobalTracer.isRegistered());

    Configuration.fromEnv().getTracer();
  }

  @Test
  public void testDisableGlobalTracer() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    System.setProperty(Configuration.JAEGER_DISABLE_GLOBAL_TRACER, "true");
    assertNotNull(Configuration.fromEnv().getTracer());
    assertFalse(GlobalTracer.isRegistered());
  }

  @Test
  public void testDefaultGlobalTracer() {
    Configuration config = new Configuration("Test");
    assertNotNull(config.getTracer());
    assertTrue(GlobalTracer.isRegistered());
  }

  @Test
  public void testSamplerConst() {
    System.setProperty(Configuration.JAEGER_SAMPLER_TYPE, ConstSampler.TYPE);
    System.setProperty(Configuration.JAEGER_SAMPLER_PARAM, "1");
    SamplerConfiguration samplerConfig = SamplerConfiguration.fromEnv();
    assertEquals(ConstSampler.TYPE, samplerConfig.getType());
    assertEquals(1, samplerConfig.getParam().intValue());
  }

  @Test
  public void testSamplerConstInvalidParam() {
    System.setProperty(Configuration.JAEGER_SAMPLER_TYPE, ConstSampler.TYPE);
    System.setProperty(Configuration.JAEGER_SAMPLER_PARAM, "X");
    SamplerConfiguration samplerConfig = SamplerConfiguration.fromEnv();
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
    ReporterConfiguration reporterConfig = ReporterConfiguration.fromEnv();
    assertTrue(reporterConfig.getLogSpans());
    assertEquals("MyHost", reporterConfig.getAgentHost());
    assertEquals(1234, reporterConfig.getAgentPort().intValue());
    assertEquals(500, reporterConfig.getFlushIntervalMs().intValue());
    assertEquals(1000, reporterConfig.getMaxQueueSize().intValue());
  }

  @Test
  public void testReporterConfigurationInvalidFlushInterval() {
    System.setProperty(Configuration.JAEGER_REPORTER_FLUSH_INTERVAL, "X");
    ReporterConfiguration reporterConfig = ReporterConfiguration.fromEnv();
    assertNull(reporterConfig.getFlushIntervalMs());
  }

  @Test
  public void testReporterConfigurationInvalidLogSpans() {
    System.setProperty(Configuration.JAEGER_REPORTER_LOG_SPANS, "X");
    ReporterConfiguration reporterConfig = ReporterConfiguration.fromEnv();
    assertFalse(reporterConfig.getLogSpans());
  }

  @Test
  public void testTracerTagslist() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    System.setProperty(Configuration.JAEGER_TAGS, "testTag1=testValue1, testTag2 = testValue2");
    com.uber.jaeger.Tracer tracer = (com.uber.jaeger.Tracer) Configuration.fromEnv().getTracer();
    assertEquals("testValue1", tracer.tags().get("testTag1"));
    assertEquals("testValue2", tracer.tags().get("testTag2"));
  }

  @Test
  public void testTracerTagslistFormatError() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    System.setProperty(Configuration.JAEGER_TAGS, "testTag1, testTag2 = testValue2");
    com.uber.jaeger.Tracer tracer = (com.uber.jaeger.Tracer) Configuration.fromEnv().getTracer();
    assertEquals("testValue2", tracer.tags().get("testTag2"));
  }

  @Test
  public void testTracerTagsSubstitutionDefault() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    System.setProperty(Configuration.JAEGER_TAGS, "testTag1=${" + TEST_PROPERTY + ":hello}");
    com.uber.jaeger.Tracer tracer = (com.uber.jaeger.Tracer) Configuration.fromEnv().getTracer();
    assertEquals("hello", tracer.tags().get("testTag1"));
  }

  @Test
  public void testTracerTagsSubstitutionSpecified() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    System.setProperty(TEST_PROPERTY, "goodbye");
    System.setProperty(Configuration.JAEGER_TAGS, "testTag1=${" + TEST_PROPERTY + ":hello}");
    com.uber.jaeger.Tracer tracer = (com.uber.jaeger.Tracer) Configuration.fromEnv().getTracer();
    assertEquals("goodbye", tracer.tags().get("testTag1"));
  }

  @Test
  public void testSenderWithEndpointWithoutAuthData() {
    System.setProperty(Configuration.JAEGER_ENDPOINT, "https://jaeger-collector:14268/api/traces");
    Sender sender = Configuration.SenderConfiguration.fromEnv().getSender();
    assertTrue(sender instanceof HttpSender);
  }

  @Test
  public void testSenderWithAgentDataFromEnv() {
    System.setProperty(Configuration.JAEGER_AGENT_HOST, "jaeger-agent");
    System.setProperty(Configuration.JAEGER_AGENT_PORT, "6832");
    try {
      Configuration.SenderConfiguration.fromEnv().getSender();
    } catch (RuntimeException re) {
      // we need to catch it here instead of using @Test(expected = ...) because the SocketException is
      // wrapped into a runtime exception
      assertTrue(re.getCause() instanceof SocketException);
    }
  }

  @Test
  public void testSenderBackwardsCompatibilityGettingAgentHostAndPort() {
    System.setProperty(Configuration.JAEGER_AGENT_HOST, "jaeger-agent");
    System.setProperty(Configuration.JAEGER_AGENT_PORT, "6832");
    assertEquals("jaeger-agent", Configuration.ReporterConfiguration.fromEnv().getAgentHost());
    assertEquals(new Integer(6832), Configuration.ReporterConfiguration.fromEnv().getAgentPort());
  }

  @Test
  public void testNoNullPointerOnNullSender() {
    Configuration.ReporterConfiguration reporterConfiguration =
            new Configuration.ReporterConfiguration(null, null, null, null);
    assertNull(reporterConfiguration.getAgentHost());
    assertNull(reporterConfiguration.getAgentPort());

    reporterConfiguration = new Configuration.ReporterConfiguration(null);
    assertNull(reporterConfiguration.getAgentHost());
    assertNull(reporterConfiguration.getAgentPort());

    reporterConfiguration = new Configuration.ReporterConfiguration();
    assertNull(reporterConfiguration.getAgentHost());
    assertNull(reporterConfiguration.getAgentPort());
  }

  @Test
  public void testCustomSender() {
    String endpoint = "https://custom-sender-endpoint:14268/api/traces";
    System.setProperty(Configuration.JAEGER_ENDPOINT, "https://jaeger-collector:14268/api/traces");
    CustomSender customSender = new CustomSender(endpoint);
    Configuration.SenderConfiguration senderConfiguration = new Configuration.SenderConfiguration.Builder()
            .sender(customSender)
            .build();
    assertEquals(endpoint, ((CustomSender)senderConfiguration.getSender()).getEndpoint());
  }

  @Test
  public void testSenderWithBasicAuthUsesHttpSender() {
    Configuration.SenderConfiguration senderConfiguration = new Configuration.SenderConfiguration.Builder()
            .endpoint("https://jaeger-collector:14268/api/traces")
            .authUsername("username")
            .authPassword("password")
            .build();
    assertTrue(senderConfiguration.getSender() instanceof HttpSender);
  }

  @Test
  public void testSenderWithAuthTokenUsesHttpSender() {
    Configuration.SenderConfiguration senderConfiguration = new Configuration.SenderConfiguration.Builder()
            .endpoint("https://jaeger-collector:14268/api/traces")
            .authToken("authToken")
            .build();
    assertTrue(senderConfiguration.getSender() instanceof HttpSender);
  }

  @Test
  public void testSenderWithAllPropertiesReturnsHttpSender() {
    System.setProperty(Configuration.JAEGER_ENDPOINT, "https://jaeger-collector:14268/api/traces");
    System.setProperty(Configuration.JAEGER_AGENT_HOST, "jaeger-agent");
    System.setProperty(Configuration.JAEGER_AGENT_PORT, "6832");

    assertTrue(Configuration.SenderConfiguration.fromEnv().getSender() instanceof HttpSender);
  }

  private class CustomSender extends HttpSender {
    private String endpoint;

    public CustomSender(String endpoint) {
      super(endpoint);
      this.endpoint = endpoint;
    }

    public String getEndpoint() {
      return endpoint;
    }
  }
}
