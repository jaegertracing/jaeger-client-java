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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Configuration {
  public static final double DEFAULT_SAMPLING_PROBABILITY = 0.001;

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
}
