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

package io.jaegertracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.Configuration.CodecConfiguration;
import io.jaegertracing.Configuration.ReporterConfiguration;
import io.jaegertracing.Configuration.SamplerConfiguration;
import io.jaegertracing.metrics.InMemoryMetricsFactory;
import io.jaegertracing.metrics.Metrics;
import io.jaegertracing.metrics.MockMetricsFactory;
import io.jaegertracing.propagation.Codec;
import io.jaegertracing.samplers.ConstSampler;
import io.jaegertracing.samplers.ProbabilisticSampler;
import io.jaegertracing.samplers.RateLimitingSampler;
import io.jaegertracing.samplers.Sampler;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
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
    assertEquals("MyHost", reporterConfig.getSenderConfiguration().getAgentHost());
    assertEquals(1234, reporterConfig.getSenderConfiguration().getAgentPort().intValue());
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
    io.jaegertracing.Tracer tracer = (io.jaegertracing.Tracer) Configuration.fromEnv().getTracer();
    assertEquals("testValue1", tracer.tags().get("testTag1"));
    assertEquals("testValue2", tracer.tags().get("testTag2"));
  }

  @Test
  public void testTracerTagslistFormatError() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    System.setProperty(Configuration.JAEGER_TAGS, "testTag1, testTag2 = testValue2");
    io.jaegertracing.Tracer tracer = (io.jaegertracing.Tracer) Configuration.fromEnv().getTracer();
    assertEquals("testValue2", tracer.tags().get("testTag2"));
  }

  @Test
  public void testTracerTagsSubstitutionDefault() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    System.setProperty(Configuration.JAEGER_TAGS, "testTag1=${" + TEST_PROPERTY + ":hello}");
    io.jaegertracing.Tracer tracer = (io.jaegertracing.Tracer) Configuration.fromEnv().getTracer();
    assertEquals("hello", tracer.tags().get("testTag1"));
  }

  @Test
  public void testTracerTagsSubstitutionSpecified() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    System.setProperty(TEST_PROPERTY, "goodbye");
    System.setProperty(Configuration.JAEGER_TAGS, "testTag1=${" + TEST_PROPERTY + ":hello}");
    io.jaegertracing.Tracer tracer = (io.jaegertracing.Tracer) Configuration.fromEnv().getTracer();
    assertEquals("goodbye", tracer.tags().get("testTag1"));
  }

  @Test
  public void testSenderBackwardsCompatibilityGettingAgentHostAndPort() {
    System.setProperty(Configuration.JAEGER_AGENT_HOST, "jaeger-agent");
    System.setProperty(Configuration.JAEGER_AGENT_PORT, "6832");
    assertEquals("jaeger-agent", Configuration.ReporterConfiguration.fromEnv()
        .getSenderConfiguration().getAgentHost());
    assertEquals(Integer.valueOf(6832), Configuration.ReporterConfiguration.fromEnv()
        .getSenderConfiguration().getAgentPort());
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

  @Test(expected = RuntimeException.class)
  public void testNoServiceName() {
    new Configuration(null);
  }

  @Test(expected = RuntimeException.class)
  public void testEmptyServiceName() {
    new Configuration("");
  }

  @Test
  public void testOverrideServiceName() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");
    Configuration configuration = Configuration.fromEnv()
        .withServiceName("bar");
    assertEquals("bar", configuration.getServiceName());
  }

  @Test
  public void testDefaultTracer() {
    Configuration configuration = new Configuration("name");
    assertNotNull(configuration.getTracer());
    assertNotNull(configuration.getTracer());
    configuration.closeTracer();
  }

  @Test(expected = IllegalStateException.class)
  public void testUnknownSampler() {
    SamplerConfiguration samplerConfiguration = new SamplerConfiguration();
    samplerConfiguration.withType("unknown");
    new Configuration("name")
        .withSampler(samplerConfiguration)
        .getTracer();
  }

  @Test
  public void testConstSampler() {
    SamplerConfiguration samplerConfiguration = new SamplerConfiguration()
        .withType(ConstSampler.TYPE);
    Sampler sampler = samplerConfiguration.createSampler("name",
        new Metrics(new InMemoryMetricsFactory()));
    assertTrue(sampler instanceof ConstSampler);
  }

  @Test
  public void testProbabilisticSampler() {
    SamplerConfiguration samplerConfiguration = new SamplerConfiguration()
        .withType(ProbabilisticSampler.TYPE);
    Sampler sampler = samplerConfiguration.createSampler("name",
        new Metrics(new InMemoryMetricsFactory()));
    assertTrue(sampler instanceof ProbabilisticSampler);
  }

  @Test
  public void testRateLimitingSampler() {
    SamplerConfiguration samplerConfiguration = new SamplerConfiguration()
        .withType(RateLimitingSampler.TYPE);
    Sampler sampler = samplerConfiguration.createSampler("name",
        new Metrics(new InMemoryMetricsFactory()));
    assertTrue(sampler instanceof RateLimitingSampler);
  }

  @Test
  public void testMetrics() {
    InMemoryMetricsFactory inMemoryMetricsFactory = new InMemoryMetricsFactory();
    Configuration configuration = new Configuration("foo")
        .withMetricsFactory(inMemoryMetricsFactory);
    assertEquals(inMemoryMetricsFactory, configuration.getMetricsFactory());
  }

  @Test
  public void testAddedCodecs() {
    Codec<TextMap> codec1 = new Codec<TextMap>() {
      @Override
      public SpanContext extract(TextMap carrier) {
        return null;
      }

      @Override
      public void inject(SpanContext spanContext, TextMap carrier) {
      }
    };

    Codec<TextMap> codec2 = new Codec<TextMap>() {
      @Override
      public SpanContext extract(TextMap carrier) {
        return null;
      }

      @Override
      public void inject(SpanContext spanContext, TextMap carrier) {
      }
    };

    CodecConfiguration codecConfiguration = new CodecConfiguration()
        .withCodec(Builtin.HTTP_HEADERS, codec1)
        .withCodec(Builtin.HTTP_HEADERS, codec2);
    assertEquals(2, codecConfiguration.getCodecs().get(Builtin.HTTP_HEADERS).size());
    assertEquals(codec1, codecConfiguration.getCodecs().get(Builtin.HTTP_HEADERS).get(0));
    assertEquals(codec2, codecConfiguration.getCodecs().get(Builtin.HTTP_HEADERS).get(1));

    Configuration configuration = new Configuration("foo")
        .withCodec(codecConfiguration);
    SpanContext spanContext = new SpanContext(2L, 11L, 22L, (byte) 0);
    assertInjectExtract(configuration.getTracer(), Builtin.TEXT_MAP, spanContext, false);
    // added codecs above overrides the default implementation
    assertInjectExtract(configuration.getTracer(), Builtin.HTTP_HEADERS, spanContext, true);
  }

  @Test
  public void testDefaultCodecs() {
    Configuration configuration = new Configuration("foo");
    SpanContext spanContext = new SpanContext(2L, 11L, 22L, (byte) 0);
    assertInjectExtract(configuration.getTracer(), Builtin.TEXT_MAP, spanContext, false);
    assertInjectExtract(configuration.getTracer(), Builtin.HTTP_HEADERS, spanContext, false);
  }

  private <C> void assertInjectExtract(io.opentracing.Tracer tracer, Format<C> format, SpanContext contextToInject,
      boolean injectMapIsEmpty) {
    HashMap<String, String> injectMap = new HashMap<>();
    tracer.inject(contextToInject, format, (C)new TextMapInjectAdapter(injectMap));
    assertEquals(injectMapIsEmpty, injectMap.isEmpty());
    if (injectMapIsEmpty) {
      return;
    }
    SpanContext extractedContext = (io.jaegertracing.SpanContext) tracer.extract(format,
        (C)new TextMapExtractAdapter(injectMap));
    assertEquals(contextToInject.getTraceId(), extractedContext.getTraceId());
    assertEquals(contextToInject.getSpanId(), extractedContext.getSpanId());
  }

  @Test
  public void testMetricsFactoryFromServiceLoader() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "Test");

    int instances = MockMetricsFactory.instances.size();
    Configuration.fromEnv().getTracer();
    assertEquals(++instances, MockMetricsFactory.instances.size());
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
}
