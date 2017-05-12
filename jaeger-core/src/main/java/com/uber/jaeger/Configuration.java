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

package com.uber.jaeger;

import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.CompositeReporter;
import com.uber.jaeger.reporters.LoggingReporter;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.HttpSamplingManager;
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.samplers.RateLimitingSampler;
import com.uber.jaeger.samplers.RemoteControlledSampler;
import com.uber.jaeger.samplers.Sampler;
import com.uber.jaeger.senders.UdpSender;

import java.text.NumberFormat;
import java.text.ParseException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Configuration {
  public static final double DEFAULT_SAMPLING_PROBABILITY = 0.001;

  /**
   * Prefix for all properties used to configure the Jaeger tracer.
   */
  public static final String JAEGER_PREFIX = "JAEGER_";

  /**
   * The host name used to locate the agent.
   */
  public static final String JAEGER_AGENT_HOST = JAEGER_PREFIX + "AGENT_HOST";

  /**
   * The port used to locate the agent.
   */
  public static final String JAEGER_AGENT_PORT = JAEGER_PREFIX + "AGENT_PORT";

  /**
   * Whether the reporter should log the spans.
   */
  public static final String JAEGER_REPORTER_LOG_SPANS = JAEGER_PREFIX + "REPORTER_LOG_SPANS";

  /**
   * The maximum queue size for use when reporting spans remotely.
   */
  public static final String JAEGER_REPORTER_MAX_QUEUE_SIZE = JAEGER_PREFIX + "REPORTER_MAX_QUEUE_SIZE";

  /**
   * The flush interval when reporting spans remotely.
   */
  public static final String JAEGER_REPORTER_FLUSH_INTERVAL = JAEGER_PREFIX + "REPORTER_FLUSH_INTERVAL";

  /**
   * The sampler type.
   */
  public static final String JAEGER_SAMPLER_TYPE = JAEGER_PREFIX + "SAMPLER_TYPE";

  /**
   * The sampler parameter (number).
   */
  public static final String JAEGER_SAMPLER_PARAM = "JAEGER_SAMPLER_PARAM";

  /**
   * The sampler manager host:port.
   */
  public static final String JAEGER_SAMPLER_MANAGER_HOST_PORT = JAEGER_PREFIX + "SAMPLER_MANAGER_HOST_PORT";

  /**
   * The service name.
   */
  public static final String JAEGER_SERVICE_NAME = JAEGER_PREFIX + "SERVICE_NAME";

  /**
   * The serviceName that the tracer will use
   */
  private final String serviceName;

  private final SamplerConfiguration samplerConfig;

  private final ReporterConfiguration reporterConfig;

  /**
   * A interface that wraps an underlying metrics generator in order to report Jaeger's metrics.
   */
  private StatsFactory statsFactory;

  /**
   * lazy singleton Tracer initialized in getTracer() method
   */
  private Tracer tracer;

  public Configuration(
      String serviceName,
      SamplerConfiguration samplerConfig,
      ReporterConfiguration reporterConfig) {
    if (serviceName == null || serviceName.length() == 0) {
      throw new RuntimeException("Must provide a service name for Jaeger Configuration");
    }

    this.serviceName = serviceName;

    if (samplerConfig == null) {
      samplerConfig = new SamplerConfiguration(null, null, null);
    }
    this.samplerConfig = samplerConfig;

    if (reporterConfig == null) {
      reporterConfig = new ReporterConfiguration(null, null, null, null, null);
    }
    this.reporterConfig = reporterConfig;

    statsFactory = new StatsFactoryImpl(new NullStatsReporter());
  }

  public static Configuration fromEnv() {
    return new Configuration(getProperty(JAEGER_SERVICE_NAME),
        getSamplerConfiguration(), getReporterConfiguration());
  }

  protected static ReporterConfiguration getReporterConfiguration() {
    return new ReporterConfiguration(
        getPropertyAsBoolean(JAEGER_REPORTER_LOG_SPANS),
        getProperty(JAEGER_AGENT_HOST),
        getPropertyAsInt(JAEGER_AGENT_PORT),
        getPropertyAsInt(JAEGER_REPORTER_FLUSH_INTERVAL),
        getPropertyAsInt(JAEGER_REPORTER_MAX_QUEUE_SIZE));
  }

  protected static SamplerConfiguration getSamplerConfiguration() {
    return new SamplerConfiguration(
        getProperty(JAEGER_SAMPLER_TYPE),
        getPropertyAsNum(JAEGER_SAMPLER_PARAM),
        getProperty(JAEGER_SAMPLER_MANAGER_HOST_PORT));
  }

  public Tracer.Builder getTracerBuilder() {
    Metrics metrics = new Metrics(statsFactory);
    Reporter reporter = reporterConfig.getReporter(metrics);
    Sampler sampler = samplerConfig.createSampler(serviceName, metrics);
    return new Tracer.Builder(serviceName, reporter, sampler).withMetrics(metrics);
  }

  public synchronized io.opentracing.Tracer getTracer() {
    if (tracer != null) {
      return tracer;
    }

    tracer = getTracerBuilder().build();
    log.info("Initialized tracer={}", tracer);
    return tracer;
  }

  public synchronized void closeTracer() {
    if (tracer != null) {
      tracer.close();
    }
  }

  /**
   * @deprecated Use {@link #setStatsFactory(StatsFactory)} instead
   */
  @Deprecated
  public void setStatsFactor(StatsFactory statsFactory) {
    this.statsFactory = statsFactory;
  }

  public void setStatsFactory(StatsFactory statsFactory) {
    this.statsFactory = statsFactory;
  }

  /**
   * SamplerConfiguration allows to configure which sampler the tracer will use.
   */
  public static class SamplerConfiguration {

    private static final String defaultManagerHostPort = "localhost:5778";

    /**
     * The type of sampler to use in the tracer. Optional. Valid values: remote (default),
     * ratelimiting, probabilistic, const.
     */
    private final String type;

    /**
     * The integer or floating point value that makes sense for the correct samplerType. Optional.
     */
    private final Number param;

    /**
     * HTTP host:port of the sampling manager that can provide sampling strategy to this service.
     * Optional.
     */
    private final String managerHostPort;

    public SamplerConfiguration(String type, Number param) {
      this(type, param, null);
    }

    public SamplerConfiguration() {
      this(null, null, null);
    }

    public SamplerConfiguration(String type, Number param, String managerHostPort) {
      this.type = type;
      this.param = param;
      this.managerHostPort = managerHostPort;
    }

    private Sampler createSampler(String serviceName, Metrics metrics) {
      String samplerType = stringOrDefault(this.getType(), RemoteControlledSampler.TYPE);
      Number samplerParam = numberOrDefault(this.getParam(), DEFAULT_SAMPLING_PROBABILITY);
      String hostPort = stringOrDefault(this.getManagerHostPort(), defaultManagerHostPort);

      if (samplerType.equals(ConstSampler.TYPE)) {
        return new ConstSampler(samplerParam.intValue() != 0);
      }

      if (samplerType.equals(ProbabilisticSampler.TYPE)) {
        return new ProbabilisticSampler(samplerParam.doubleValue());
      }

      if (samplerType.equals(RateLimitingSampler.TYPE)) {
        return new RateLimitingSampler(samplerParam.intValue());
      }

      if (samplerType.equals(RemoteControlledSampler.TYPE)) {
        Sampler initialSampler = new ProbabilisticSampler(samplerParam.doubleValue());

        HttpSamplingManager manager = new HttpSamplingManager(hostPort);

        return new RemoteControlledSampler(serviceName, manager, initialSampler, metrics);
      }

      throw new IllegalStateException(String.format("Invalid sampling strategy %s", samplerType));
    }

    public String getType() {
      return type;
    }

    public Number getParam() {
      return param;
    }

    public String getManagerHostPort() {
      return managerHostPort;
    }
  }

  public static class ReporterConfiguration {

    private static final String defaultAgentHost = "localhost";
    private static final int defaultAgentPort = 5775;
    private static final int defaultFlushIntervalMs = 1000;
    private static final int defaultMaxQueueSize = 100;

    private final Boolean logSpans;

    private final String agentHost;

    private final Integer agentPort;

    private final Integer flushIntervalMs;

    private final Integer maxQueueSize;

    public ReporterConfiguration() {
      this(null, null, null, null, null);
    }

    public ReporterConfiguration(
        Boolean logSpans,
        String agentHost,
        Integer agentPort,
        Integer flushIntervalMs,
        Integer maxQueueSize) {
      this.logSpans = logSpans;
      this.agentHost = agentHost;
      this.agentPort = agentPort;
      this.flushIntervalMs = flushIntervalMs;
      this.maxQueueSize = maxQueueSize;
    }

    private Reporter getReporter(Metrics metrics) {
      UdpSender sender =
          new UdpSender(
              stringOrDefault(this.agentHost, defaultAgentHost),
              numberOrDefault(this.agentPort, defaultAgentPort).intValue(),
              0 /* max packet size */);

      Reporter reporter =
          new RemoteReporter(
              sender,
              numberOrDefault(this.flushIntervalMs, defaultFlushIntervalMs).intValue(),
              numberOrDefault(this.maxQueueSize, defaultMaxQueueSize).intValue(),
              metrics);

      if (Boolean.TRUE.equals(this.logSpans)) {
        Reporter loggingReporter = new LoggingReporter();
        reporter = new CompositeReporter(reporter, loggingReporter);
      }
      return reporter;
    }

    public Boolean getLogSpans() {
      return logSpans;
    }

    public String getAgentHost() {
      return agentHost;
    }

    public Integer getAgentPort() {
      return agentPort;
    }

    public Integer getFlushIntervalMs() {
      return flushIntervalMs;
    }

    public Integer getMaxQueueSize() {
      return maxQueueSize;
    }
  }

  private static String stringOrDefault(String value, String defaultValue) {
    return value != null && value.length() > 0 ? value : defaultValue;
  }

  private static Number numberOrDefault(Number value, Number defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static String getProperty(String name) {
    return System.getProperty(name, System.getenv(name));
  }

  private static Integer getPropertyAsInt(String name) {
    String value = getProperty(name);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        log.error("Failed to parse integer for property '" + name + "' with value '" + value + "'", e);
      }
    }
    return null;
  }

  private static Number getPropertyAsNum(String name) {
    String value = getProperty(name);
    if (value != null) {
      try {
        return NumberFormat.getInstance().parse(value);
      } catch (ParseException e) {
        log.error("Failed to parse number for property '" + name + "' with value '" + value + "'", e);
      }
    }
    return null;
  }

  private static Boolean getPropertyAsBoolean(String name) {
    String value = getProperty(name);
    if (value != null) {
      return Boolean.valueOf(value);
    }
    return null;
  }

}
