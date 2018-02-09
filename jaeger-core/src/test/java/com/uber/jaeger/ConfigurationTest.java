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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.uber.jaeger.Configuration.CodecConfiguration;
import com.uber.jaeger.Configuration.ReporterConfiguration;
import com.uber.jaeger.Configuration.SamplerConfiguration;
import com.uber.jaeger.Configuration.SenderConfiguration;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.reporters.CompositeReporter;
import com.uber.jaeger.reporters.LoggingReporter;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.HttpSamplingManager;
import com.uber.jaeger.samplers.RemoteControlledSampler;
import com.uber.jaeger.senders.HttpSender;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
    System.clearProperty(Configuration.JAEGER_ENDPOINT);
    System.clearProperty(Configuration.JAEGER_AUTH_TOKEN);
    System.clearProperty(Configuration.JAEGER_USER);
    System.clearProperty(Configuration.JAEGER_PASSWORD);
    System.clearProperty(Configuration.JAEGER_PROPAGATION);

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
    assertFalse(GlobalTracer.isRegistered());
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
      fail("expecting exception");
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
    assertEquals(Integer.valueOf(6832), Configuration.ReporterConfiguration.fromEnv().getAgentPort());
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

  @Test
  public void testPropagationB3Only() {
    System.setProperty(Configuration.JAEGER_PROPAGATION, "b3");
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");

    long traceId = 1234;
    long spanId = 5678;

    TestTextMap textMap = new TestTextMap();
    SpanContext spanContext = new SpanContext(traceId, spanId, 0, (byte)0);

    io.opentracing.Tracer tracer = Configuration.fromEnv().getTracer();
    tracer.inject(spanContext, Format.Builtin.TEXT_MAP, textMap);

    assertNotNull(textMap.get("X-B3-TraceId"));
    assertNotNull(textMap.get("X-B3-SpanId"));
    assertNull(textMap.get("uber-trace-id"));

    SpanContext extractedContext = (SpanContext)tracer.extract(Format.Builtin.TEXT_MAP, textMap);
    assertEquals(traceId, extractedContext.getTraceId());
    assertEquals(spanId, extractedContext.getSpanId());
  }

  @Test
  public void testPropagationJaegerAndB3() {
    System.setProperty(Configuration.JAEGER_PROPAGATION, "jaeger,b3");
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");

    long traceId = 1234;
    long spanId = 5678;

    TestTextMap textMap = new TestTextMap();
    SpanContext spanContext = new SpanContext(traceId, spanId, 0, (byte)0);

    io.opentracing.Tracer tracer = Configuration.fromEnv().getTracer();
    tracer.inject(spanContext, Format.Builtin.TEXT_MAP, textMap);

    assertNotNull(textMap.get("uber-trace-id"));
    assertNotNull(textMap.get("X-B3-TraceId"));
    assertNotNull(textMap.get("X-B3-SpanId"));

    SpanContext extractedContext = (SpanContext)tracer.extract(Format.Builtin.TEXT_MAP, textMap);
    assertEquals(traceId, extractedContext.getTraceId());
    assertEquals(spanId, extractedContext.getSpanId());
  }

  @Test
  public void testPropagationDefault() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");

    TestTextMap textMap = new TestTextMap();
    SpanContext spanContext = new SpanContext(1234, 5678, 0, (byte)0);

    Configuration.fromEnv().getTracer().inject(spanContext, Format.Builtin.TEXT_MAP, textMap);

    assertNotNull(textMap.get("uber-trace-id"));
    assertNull(textMap.get("X-B3-TraceId"));
    assertNull(textMap.get("X-B3-SpanId"));
  }

  @Test
  public void testPropagationValidFormat() {
    System.setProperty(Configuration.JAEGER_PROPAGATION, "jaeger,invalid");
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");

    TestTextMap textMap = new TestTextMap();
    SpanContext spanContext = new SpanContext(1234, 5678, 0, (byte)0);

    Configuration.fromEnv().getTracer().inject(spanContext, Format.Builtin.TEXT_MAP, textMap);

    // Check that jaeger context still available even though invalid format specified
    assertNotNull(textMap.get("uber-trace-id"));
  }

  @Test
  public void testEnsureCachedTracerInstance() {
    Configuration configuration = new Configuration("the-service");
    io.opentracing.Tracer tracer = configuration.getTracer();
    assertEquals("Same instance of the tracer was expected", tracer, configuration.getTracer());
  }

  @Test
  public void testEnsureNewTracerInstance() {
    Configuration configuration = new Configuration("the-service");
    io.opentracing.Tracer tracer = configuration.getTracerBuilder().build();
    assertNotEquals("Different instance of the tracer was expected", tracer, configuration.getTracerBuilder().build());
  }

  @Test
  public void testCloseWhenNoTracerWasBuilt() {
    // this is a noop, but we ensure there's no NPE going on
    new Configuration("the-service").closeTracer();
  }

  @Test
  public void testClose() throws SenderException {
    Sender sender = mock(Sender.class);
    ReporterConfiguration reporterConfiguration = new ReporterConfiguration(sender);

    Configuration configuration = Configuration
        .builder("a-name")
        .withReporterConfiguration(reporterConfiguration)
        .build();

    configuration.getTracer();
    configuration.closeTracer();

    verify(sender, times(1)).close();
  }

  @Test
  public void testServiceName() {
    Configuration configuration = Configuration.builder("the-service").build();
    io.opentracing.Tracer tracer = configuration.getTracer();
    assertEquals("the-service", configuration.getServiceName());
    assertEquals("the-service", ((Tracer) tracer).getServiceName());
  }

  @Test
  public void testServiceNameOnConstructor() {
    Configuration configuration = new Configuration("the-service");
    assertEquals("Expected a different service name", "the-service", configuration.getServiceName());

    Tracer tracer = (Tracer) configuration.getTracer();
    assertEquals("Expected a different service name", "the-service", tracer.getServiceName());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoServiceNameFails() {
    Configuration.builder(null).build().getTracer();
  }

  @Test
  public void testOverrideServiceName() {
    Configuration configuration = Configuration
        .builder("the-service")
        .withServiceName("another-service")
        .build();
    assertEquals("Expected a different service name", "another-service", configuration.getServiceName());

    Tracer tracer = (Tracer) configuration.getTracer();
    assertEquals("Should have overridden the service name", "another-service", tracer.getServiceName());
  }

  @Test
  public void testBuilderFromEnv() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "the-service");
    Tracer tracer = (Tracer) Configuration
        .builderFromEnv()
        .build()
        .getTracer();
    assertEquals("Expected a different value for the max queue size", "the-service", tracer.getServiceName());
    System.clearProperty(Configuration.JAEGER_SERVICE_NAME);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderFromEnvWithoutServiceName() {
    System.clearProperty(Configuration.JAEGER_SERVICE_NAME);
    Configuration.builderFromEnv().build().getTracer();
  }

  @Test
  public void testWithStatsFactory() {
    StatsFactory statsFactory = mock(StatsFactory.class);

    Configuration configuration = Configuration
        .builder("the-service")
        .withStatsFactory(statsFactory)
        .build();
    assertEquals("Expected to have used our stats factory", statsFactory, configuration.getStatsFactory());

    configuration.getTracer();
    verify(statsFactory, atLeastOnce()).createCounter(any(String.class), any(Map.class));
  }

  @Test
  public void testAliasedMethodForStatsFactory() {
    StatsFactory statsFactory = mock(StatsFactory.class);

    Configuration configuration = Configuration.builder("the-service").build();
    configuration.setStatsFactor(statsFactory);
    assertEquals("Expected to have used our stats factory", statsFactory, configuration.getStatsFactory());

    configuration.getTracer();
    verify(statsFactory, atLeastOnce()).createCounter(any(String.class), any(Map.class));
  }

  @Test
  public void testWithCodecConfiguration() {
    Configuration.CodecConfiguration codecConfiguration = mock(Configuration.CodecConfiguration.class);

    Configuration configuration = Configuration
        .builder("the-service")
        .withCodecConfiguration(codecConfiguration)
        .build();
    assertEquals("Expected to have used our custom codec configuration",
        codecConfiguration,
        configuration.getCodecConfig()
    );

    configuration.getTracer();
    verify(codecConfiguration, times(1)).apply(any());
  }

  /**
   * @see #testPropagationValidFormat()
   */
  @Test
  public void testPropagationViaBuilder() {
    TestTextMap textMap = new TestTextMap();
    SpanContext spanContext = new SpanContext(1234, 5678, 0, (byte)0);

    Configuration
        .builder("the-service")
        .withCodecConfiguration(
            CodecConfiguration.withPropagations(Configuration.Propagation.JAEGER, Configuration.Propagation.B3)
        )
        .build()
        .getTracer().inject(spanContext, Format.Builtin.TEXT_MAP, textMap);

    // Check that jaeger context still available even though invalid format specified
    assertNotNull(textMap.get("uber-trace-id"));
  }

  @Test
  public void testCustomReporterConfiguration() {
    ReporterConfiguration reporterConfiguration = mock(ReporterConfiguration.class);

    Configuration configuration = Configuration.builder("the-service")
        .withReporterConfiguration(reporterConfiguration)
        .build();

    // ideally, we would have checked if it was actually used when building the tracer, but the methods that
    // are called are private...
    assertEquals("Should have used the same reporter configuration we specified",
        reporterConfiguration,
        configuration.getReporterConfig()
    );
  }

  @Test
  public void testWithSender() throws SenderException {
    Sender sender = mock(Sender.class);
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(new ReporterConfiguration(sender))
        .build();

    configuration.getTracer();
    configuration.closeTracer();

    verify(sender, times(1)).close();
  }

  @Test
  public void testWithSpanLogging() {
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withSpanLogging(true)
                .build()
        )
        .build();
    Tracer tracer = (Tracer) configuration.getTracer();

    assertTrue("Expected the reporter config to have set the logging to true",
        configuration.getReporterConfig().getLogSpans()
    );
    assertTrue("Expected the reporter to be composite", tracer.getReporter() instanceof CompositeReporter);

    CompositeReporter compositeReporter = (CompositeReporter) tracer.getReporter();
    assertTrue("Expected to have LoggingReporter as part of the CompositeReporter",
        compositeReporter.getReporters().stream().anyMatch(r -> r instanceof LoggingReporter)
    );
  }

  @Test
  public void testWithoutSpanLogging() {
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withSpanLogging(false)
                .build()
        )
        .build();
    Tracer tracer = (Tracer) configuration.getTracer();

    assertFalse("Expected the reporter config to have set the logging to false",
        configuration.getReporterConfig().getLogSpans()
    );
    assertFalse("Expected the reporter to be composite", tracer.getReporter() instanceof CompositeReporter);
  }

  @Test
  public void testWithMaxQueueSize() {
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withMaxQueueSize(42)
                .build()
        )
        .build();
    Tracer tracer = (Tracer) configuration.getTracer();

    RemoteReporter reporter = (RemoteReporter) tracer.getReporter();
    assertEquals("Expected a different value for the max queue size", 42, reporter.getMaxQueueSize());

    assertEquals(
        "Expected a different value for the max queue size",
        42,
        (long) configuration.getReporterConfig().getMaxQueueSize()
    );
  }

  @Test
  public void testWithFlushInterval() {
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withFlushInterval(42)
                .build()
        )
        .build();

    assertEquals(
        "Expected a different value for the flush interval",
        42,
        (long) configuration.getReporterConfig().getFlushIntervalMs()
    );
  }

  @Test
  public void testWithSamplerConfiguration() {
    SamplerConfiguration samplerConfiguration = mock(SamplerConfiguration.class);
    Configuration
        .builder("the-service")
        .withSamplerConfiguration(samplerConfiguration)
        .build()
        .getTracer();
    verify(samplerConfiguration, times(1)).getType();
  }

  @Test
  public void testWithSamplerType() {
    Configuration configuration = Configuration.builder("the-service")
        .withSamplerConfiguration(
            SamplerConfiguration.builder()
                .withType(ConstSampler.TYPE)
                .build()
        )
        .build();
    assertEquals("Expected the type to be ConstSampler.TYPE",
        ConstSampler.TYPE,
        configuration.getSamplerConfig().getType()
    );

    assertTrue("Expected the sampler to be a ConstSampler",
        configuration.getTracerBuilder().getSampler() instanceof ConstSampler
    );
  }

  @Test
  public void testWithSamplerParam() {
    Configuration configuration = Configuration.builder("the-service")
        .withSamplerConfiguration(
            SamplerConfiguration.builder()
                .withParam(1)
                .build()
        )
        .build();
    assertEquals("Expected the type to be ConstSampler.TYPE",
        1,
        configuration.getSamplerConfig().getParam()
    );

    assertTrue("Expected to have used the default sampler",
        configuration.getTracerBuilder().getSampler() instanceof RemoteControlledSampler
    );
  }

  @Test
  public void testWithManagerHostPort() {
    Configuration configuration = Configuration.builder("the-service")
        .withSamplerConfiguration(
            SamplerConfiguration.builder()
                .withManagerHostPort("localhost:5779")
                .build()
        )
        .build();

    assertEquals("Expected the have the host:port as localhost:5779",
        "localhost:5779",
        configuration.getSamplerConfig().getManagerHostPort()
    );

    assertTrue("Expected the sampler to be a remote sampler",
        configuration.getTracerBuilder().getSampler() instanceof RemoteControlledSampler
    );

    RemoteControlledSampler sampler = (RemoteControlledSampler) configuration.getTracerBuilder().getSampler();

    assertTrue("Expected the sampling manager to be a http sampling manager",
        sampler.getManager() instanceof HttpSamplingManager
    );
    HttpSamplingManager manager = (HttpSamplingManager) sampler.getManager();
    assertEquals("Expected to have localhost:5779 as hostPort", "localhost:5779", manager.getHostPort());
  }

  @Test
  public void testWithSampler() {
    ConstSampler constSampler = new ConstSampler(true);

    Configuration configuration = Configuration.builder("the-service")
        .withSamplerConfiguration(
            SamplerConfiguration.builder()
                .withSampler(constSampler)
                .build()
        )
        .build();

    assertEquals("Expected to have used the sampler we specified",
        constSampler,
        configuration.getTracerBuilder().getSampler()
    );
  }

  @Test
  public void testWithSamplerPrecedence() {
    ConstSampler constSampler = new ConstSampler(true);

    Configuration configuration = Configuration.builder("the-service")
        .withSamplerConfiguration(
            SamplerConfiguration.builder()
                .withSampler(constSampler)
                .withManagerHostPort("localhost:5779")
                .build()
        )
        .build();

    assertTrue("Expected the sampler to be a remote sampler",
        configuration.getTracerBuilder().getSampler() instanceof RemoteControlledSampler
    );

    RemoteControlledSampler sampler = (RemoteControlledSampler) configuration.getTracerBuilder().getSampler();

    assertTrue("Expected the sampling manager to be a http sampling manager",
        sampler.getManager() instanceof HttpSamplingManager
    );
    HttpSamplingManager manager = (HttpSamplingManager) sampler.getManager();
    assertEquals("Expected to have localhost:5779 as hostPort", "localhost:5779", manager.getHostPort());

    configuration = Configuration.builder("the-service")
        .withSamplerConfiguration(
            SamplerConfiguration.builder()
                .withManagerHostPort("localhost:5779")
                .withSampler(constSampler)
                .build()
        )
        .build();

    assertEquals("Expected to have used the sampler we specified",
        constSampler,
        configuration.getTracerBuilder().getSampler()
    );
  }

  @Test
  public void testWithCollectorEndpoint() {
    String endpoint = "https://jaeger-collector-custom:14268/api/traces";
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withSenderConfiguration(
                    SenderConfiguration.builder()
                        .endpoint(endpoint)
                        .build()
                )
                .build()
        )
        .build();

    // it's hard to check if it's actually the one being used, as it's not stored anywhere other than the final target
    // transport object, like OkHttp or Thrift
    assertEquals("Expected to have set the collector endpoint on the sender",
        endpoint,
        configuration.getReporterConfig().getSenderConfiguration().getEndpoint()
    );

  }

  @Test
  public void testWithAuthUserAndPasswordWithoutEndpoint() {
    // username/password is ignored if no endpoint is specified
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withSenderConfiguration(
                    SenderConfiguration.builder()
                        .authUsername("jdoe")
                        .authPassword("pass")
                        .build()
                )
                .build()
        )
        .build();

    assertTrue("Expected the reporter to be a remote reporter",
        configuration.getTracerBuilder().getReporter() instanceof RemoteReporter
    );

    RemoteReporter reporter = (RemoteReporter) configuration.getTracerBuilder().getReporter();
    assertTrue("Expected the sender to be a UdpSender",
        reporter.getSender() instanceof UdpSender
    );
  }

  @Test
  public void testWithAuthUserAndPasswordWithEndpoint() {
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withSenderConfiguration(
                    SenderConfiguration.builder()
                        .authUsername("jdoe")
                        .authPassword("pass")
                        .endpoint("https://jaeger-collector:14268/api/traces")
                        .build()
                )
                .build()
        )
        .build();

    assertTrue("Expected the reporter to be a remote reporter",
        configuration.getTracerBuilder().getReporter() instanceof RemoteReporter
    );

    RemoteReporter reporter = (RemoteReporter) configuration.getTracerBuilder().getReporter();
    assertTrue("Expected the sender to be a HttpSender",
        reporter.getSender() instanceof HttpSender
    );
  }

  @Test
  public void testWithAuthToken() {
    String pass = "the-token";

    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withSenderConfiguration(
                    SenderConfiguration.builder()
                        .authToken(pass)
                        .build()
                )
                .build()
        )
        .build();

    assertEquals("Expected to have set the auth token",
        pass,
        configuration.getReporterConfig().getSenderConfiguration().getAuthToken()
    );
  }

  @Test
  public void testWithAgentHostAndPort() {
    Configuration configuration = Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withSenderConfiguration(
                    SenderConfiguration.builder()
                        .agentHost("host")
                        .agentPort(42)
                        .build()
                )
                .build()
        )
        .build();

    assertEquals("Expected to have set the hostname for the agent",
        "host",
        configuration.getReporterConfig().getSenderConfiguration().getAgentHost()
    );

    assertEquals("Expected to have set the port for the agent",
        42,
        (long) configuration.getReporterConfig().getSenderConfiguration().getAgentPort()
    );
  }

  @Test
  public void testLastTakesPrecedence() {
    Configuration.SenderConfiguration senderConfiguration = mock(Configuration.SenderConfiguration.class);

    Configuration
        .builder("the-service")
        .withReporterConfiguration(
            ReporterConfiguration.builder()
                .withMaxQueueSize(1)
                .withSenderConfiguration(SenderConfiguration.builder().build())
                .withSenderConfiguration(senderConfiguration)
                .build()
        )
        .build()
        .getTracer()
        .buildSpan("my-span")
        .start()
        .finish();

    verify(senderConfiguration, times(1)).getSender();
  }

  static class TestTextMap implements TextMap {

    private Map<String,String> values = new HashMap<>();

    @Override
    public Iterator<Entry<String, String>> iterator() {
      return values.entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
      values.put(key, value);
    }

    public String get(String key) {
      return values.get(key);
    }
  }

  private static class CustomSender extends HttpSender {
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
