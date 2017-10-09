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
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;
import io.opentracing.util.GlobalTracer;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

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
   * The tracer level tags.
   */
  public static final String JAEGER_TAGS = JAEGER_PREFIX + "TAGS";

  /**
   * Disables registration with {@link GlobalTracer}.
   */
  public static final String JAEGER_DISABLE_GLOBAL_TRACER = JAEGER_PREFIX + "DISABLE_GLOBAL_TRACER";

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
   * Don't use {@link GlobalTracer} to store the tracer.
   * Use the local {@link #tracer} instead.
   */
  private final boolean disableGlobalTracer;

  /**
   * lazy singleton Tracer initialized in getTracer() method.
   */
  private Tracer tracer;

  public Configuration(String serviceName) {
    this(serviceName, null, null);
  }

  public Configuration(
      String serviceName,
      SamplerConfiguration samplerConfig,
      ReporterConfiguration reporterConfig) {
    this(serviceName, samplerConfig, reporterConfig, false);
  }

  private Configuration(
      String serviceName,
      SamplerConfiguration samplerConfig,
      ReporterConfiguration reporterConfig,
      boolean disableGlobalTracer) {
    if (serviceName == null || serviceName.isEmpty()) {
      throw new IllegalArgumentException("Must provide a service name for Jaeger Configuration");
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

    this.disableGlobalTracer = disableGlobalTracer;
  }

  public static Configuration fromEnv() {
    boolean disableGlobalTracer = getPropertyAsBool(JAEGER_DISABLE_GLOBAL_TRACER);
    if (GlobalTracer.isRegistered() && !disableGlobalTracer) {
      throw new IllegalStateException(
          String.format(
              "There is already a current global Tracer registered. "
                  + "Set %s to disable automatic registration.",
              JAEGER_DISABLE_GLOBAL_TRACER));
    }
    return new Configuration(
        getProperty(JAEGER_SERVICE_NAME),
        SamplerConfiguration.fromEnv(),
        ReporterConfiguration.fromEnv(),
        disableGlobalTracer);
  }

  public Tracer.Builder getTracerBuilder() {
    Metrics metrics = new Metrics(statsFactory);
    Reporter reporter = reporterConfig.getReporter(metrics);
    Sampler sampler = samplerConfig.createSampler(serviceName, metrics);
    return new Tracer.Builder(serviceName, reporter, sampler).withMetrics(metrics).withTags(tracerTagsFromEnv());
  }

  public synchronized io.opentracing.Tracer getTracer() {
    if (tracer != null) {
      return tracer;
    }

    tracer = getTracerBuilder().build();
    log.info("Initialized tracer={}", tracer);

    if (!(disableGlobalTracer || GlobalTracer.isRegistered())) {
      GlobalTracer.register(tracer);
    }

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

    public static SamplerConfiguration fromEnv() {
      return new SamplerConfiguration(
          getProperty(JAEGER_SAMPLER_TYPE),
          getPropertyAsNum(JAEGER_SAMPLER_PARAM),
          getProperty(JAEGER_SAMPLER_MANAGER_HOST_PORT));
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
    private static final int DEFAULT_FLUSH_INTERVAL_MS = 1000;
    private static final int DEFAULT_MAX_QUEUE_SIZE = 100;

    private Boolean logSpans;
    private String agentHost;
    private Integer agentPort;
    private Integer flushIntervalMs;
    private Integer maxQueueSize;

    private Sender sender;

    public ReporterConfiguration() {
      this(null, null, null, null, null);
    }

    public ReporterConfiguration(Sender sender) {
      this.sender = sender;
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

    public static ReporterConfiguration fromEnv() {
      return new ReporterConfiguration(
          getPropertyAsBool(JAEGER_REPORTER_LOG_SPANS),
          getProperty(JAEGER_AGENT_HOST),
          getPropertyAsInt(JAEGER_AGENT_PORT),
          getPropertyAsInt(JAEGER_REPORTER_FLUSH_INTERVAL),
          getPropertyAsInt(JAEGER_REPORTER_MAX_QUEUE_SIZE));
    }

    private Sender getSender() {
      return this.sender != null ? this.sender :
          new UdpSender(
              stringOrDefault(this.agentHost, UdpSender.DEFAULT_AGENT_UDP_HOST),
              numberOrDefault(this.agentPort, UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT).intValue(),
              0 /* max packet size */);
    }

    private Reporter getReporter(Metrics metrics) {
      Reporter reporter =
          new RemoteReporter(
              getSender(),
              numberOrDefault(this.flushIntervalMs, DEFAULT_FLUSH_INTERVAL_MS).intValue(),
              numberOrDefault(this.maxQueueSize, DEFAULT_MAX_QUEUE_SIZE).intValue(),
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

  /**
   * Gets the system property defined by the name , and returns a boolean value represented by
   * the name. This method defaults to returning false for a name that doesn't exist.
   * @param name The name of the system property
   */
  private static boolean getPropertyAsBool(String name) {
    return Boolean.valueOf(getProperty(name));
  }

  private static Map<String, String> tracerTagsFromEnv() {
    Map<String, String> tracerTagMaps = null;
    String tracerTags = getProperty(JAEGER_TAGS);
    if (tracerTags != null) {
      String[] tags = tracerTags.split("\\s*,\\s*");
      for (String tag : tags) {
        String[] tagValue = tag.split("\\s*=\\s*");
        if (tagValue.length == 2) {
          if (tracerTagMaps == null) {
            tracerTagMaps = new HashMap<String, String>();
          }
          tracerTagMaps.put(tagValue[0], resolveValue(tagValue[1]));
        } else {
          log.error("Tracer tag incorrectly formatted: " + tag);
        }
      }
    }
    return tracerTagMaps;
  }

  private static String resolveValue(String value) {
    if (value.startsWith("${") && value.endsWith("}")) {
      String[] ref = value.substring(2, value.length() - 1).split("\\s*:\\s*");
      if (ref.length > 0) {
        String propertyValue = getProperty(ref[0]);
        if (propertyValue == null && ref.length > 1) {
          propertyValue = ref[1];
        }
        return propertyValue;
      }
    }
    return value;
  }
}
