/*
 * Copyright (c) 2016, Uber Technologies, Inc
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

import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.propagation.B3TextMapCodec;
import com.uber.jaeger.propagation.Codec;
import com.uber.jaeger.propagation.CompositeCodec;
import com.uber.jaeger.propagation.TextMapCodec;
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
import com.uber.jaeger.senders.HttpSender;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;

@Slf4j
public class Configuration {
  public static final double DEFAULT_SAMPLING_PROBABILITY = 0.001;

  /**
   * Prefix for all properties used to configure the Jaeger tracer.
   */
  public static final String JAEGER_PREFIX = "JAEGER_";

  /**
   * The full URL to the {@code traces} endpoint, like https://jaeger-collector:14268/api/traces
   */
  public static final String JAEGER_ENDPOINT = JAEGER_PREFIX + "ENDPOINT";

  /**
   * The Auth Token to be added as "Bearer" on Authorization headers for requests sent to the endpoint
   */
  public static final String JAEGER_AUTH_TOKEN = JAEGER_PREFIX + "AUTH_TOKEN";

  /**
   * The Basic Auth username to be added on Authorization headers for requests sent to the endpoint
   */
  public static final String JAEGER_USER = JAEGER_PREFIX + "USER";

  /**
   * The Basic Auth password to be added on Authorization headers for requests sent to the endpoint
   */
  public static final String JAEGER_PASSWORD = JAEGER_PREFIX + "PASSWORD";

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
   * Comma separated list of formats to use for propagating the trace context. Default will the
   * standard Jaeger format. Valid values are jaeger and b3.
   */
  public static final String JAEGER_PROPAGATION = JAEGER_PREFIX + "PROPAGATION";

  /**
   * The supported trace context propagation formats.
   */
  public enum Propagation {

    /**
     * The default Jaeger trace context propagation format.
     */
    JAEGER,

    /**
     * The Zipkin B3 trace context propagation format.
     */
    B3
  }

  /**
   * The serviceName that the tracer will use
   */
  private final String serviceName;

  private final SamplerConfiguration samplerConfig;

  private final ReporterConfiguration reporterConfig;

  private final CodecConfiguration codecConfig;

  /**
   * A interface that wraps an underlying metrics generator in order to report Jaeger's metrics.
   */
  private StatsFactory statsFactory;

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
    this(serviceName, samplerConfig, reporterConfig, null);
  }

  private Configuration(
      String serviceName,
      SamplerConfiguration samplerConfig,
      ReporterConfiguration reporterConfig,
      CodecConfiguration codecConfig) {
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

    if (codecConfig == null) {
      codecConfig = new CodecConfiguration(Collections.<Format<?>, List<Codec<TextMap>>>emptyMap());
    }
    this.codecConfig = codecConfig;

    statsFactory = new StatsFactoryImpl(new NullStatsReporter());
  }

  public static Configuration fromEnv() {
    return new Configuration(
        getProperty(JAEGER_SERVICE_NAME),
        SamplerConfiguration.fromEnv(),
        ReporterConfiguration.fromEnv(),
        CodecConfiguration.fromEnv());
  }

  public Tracer.Builder getTracerBuilder() {
    Metrics metrics = new Metrics(statsFactory);
    Reporter reporter = reporterConfig.getReporter(metrics);
    Sampler sampler = samplerConfig.createSampler(serviceName, metrics);
    Tracer.Builder builder = new Tracer.Builder(serviceName,
        reporter, sampler).withMetrics(metrics).withTags(tracerTagsFromEnv());
    codecConfig.apply(builder);
    return builder;
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

  /**
   * CodecConfiguration can be used to support additional trace context propagation codec.
   */
  public static class CodecConfiguration {
    private Map<Format<?>, List<Codec<TextMap>>> codecs;

    private CodecConfiguration(Map<Format<?>, List<Codec<TextMap>>> codecs) {
      this.codecs = codecs;
    }

    public static CodecConfiguration fromEnv() {
      Map<Format<?>, List<Codec<TextMap>>> codecs = new HashMap<Format<?>, List<Codec<TextMap>>>();
      String propagation = getProperty(JAEGER_PROPAGATION);
      if (propagation != null) {
        for (String format : Arrays.asList(propagation.split(","))) {
          try {
            switch (Configuration.Propagation.valueOf(format.toUpperCase())) {
              case JAEGER:
                addCodec(codecs, Format.Builtin.HTTP_HEADERS, new TextMapCodec(true));
                addCodec(codecs, Format.Builtin.TEXT_MAP, new TextMapCodec(false));
                break;
              case B3:
                addCodec(codecs, Format.Builtin.HTTP_HEADERS, new B3TextMapCodec());
                addCodec(codecs, Format.Builtin.TEXT_MAP, new B3TextMapCodec());
                break;
              default:
                log.error("Unhandled propagation format '" + format + "'");
                break;
            }
          } catch (IllegalArgumentException iae) {
            log.error("Unknown propagation format '" + format + "'");
          }
        }
      }
      return new CodecConfiguration(codecs);
    }

    private static void addCodec(Map<Format<?>, List<Codec<TextMap>>> codecs, Format<?> format, Codec<TextMap> codec) {
      List<Codec<TextMap>> codecList = codecs.get(format);
      if (codecList == null) {
        codecList = new LinkedList<Codec<TextMap>>();
        codecs.put(format, codecList);
      }
      codecList.add(codec);
    }

    public void apply(Tracer.Builder builder) {
      // Replace existing TEXT_MAP and HTTP_HEADERS codec with one that represents the
      // configured propagation formats
      registerCodec(builder, Format.Builtin.HTTP_HEADERS);
      registerCodec(builder, Format.Builtin.TEXT_MAP);
    }

    protected void registerCodec(Tracer.Builder builder, Format<TextMap> format) {
      if (codecs.containsKey(format)) {
        List<Codec<TextMap>> codecsForFormat = codecs.get(format);
        Codec<TextMap> codec = codecsForFormat.size() == 1
            ? codecsForFormat.get(0) : new CompositeCodec<TextMap>(codecsForFormat);
        builder.registerInjector(format, codec);
        builder.registerExtractor(format, codec);
      }
    }
  }

  public static class ReporterConfiguration {
    private static final int DEFAULT_FLUSH_INTERVAL_MS = 1000;
    private static final int DEFAULT_MAX_QUEUE_SIZE = 100;

    private Boolean logSpans;
    private Integer flushIntervalMs;
    private Integer maxQueueSize;

    private SenderConfiguration senderConfiguration;

    public ReporterConfiguration() {
      this(null, null, null, null, null);
    }

    public ReporterConfiguration(Sender sender) {
      this.senderConfiguration = new Configuration.SenderConfiguration.Builder().sender(sender).build();
    }

    public ReporterConfiguration(
        Boolean logSpans,
        String agentHost,
        Integer agentPort,
        Integer flushIntervalMs,
        Integer maxQueueSize) {
      this.logSpans = logSpans;
      this.flushIntervalMs = flushIntervalMs;
      this.maxQueueSize = maxQueueSize;
      this.senderConfiguration = new Configuration.SenderConfiguration.Builder()
              .agentHost(agentHost)
              .agentPort(agentPort)
              .build();
    }

    public ReporterConfiguration(
        Boolean logSpans,
        Integer flushIntervalMs,
        Integer maxQueueSize,
        SenderConfiguration senderConfiguration) {
      this.logSpans = logSpans;
      this.flushIntervalMs = flushIntervalMs;
      this.maxQueueSize = maxQueueSize;
      this.senderConfiguration = senderConfiguration;
    }

    public static ReporterConfiguration fromEnv() {
      return new ReporterConfiguration(
          getPropertyAsBool(JAEGER_REPORTER_LOG_SPANS),
          getPropertyAsInt(JAEGER_REPORTER_FLUSH_INTERVAL),
          getPropertyAsInt(JAEGER_REPORTER_MAX_QUEUE_SIZE),
          SenderConfiguration.fromEnv());
    }

    private Reporter getReporter(Metrics metrics) {
      Reporter reporter =
          new RemoteReporter(
              this.senderConfiguration.getSender(),
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
      if (null == this.senderConfiguration) {
        return null;
      }

      return this.senderConfiguration.agentHost;
    }

    public Integer getAgentPort() {
      if (null == this.senderConfiguration) {
        return null;
      }

      return this.senderConfiguration.agentPort;
    }

    public Integer getFlushIntervalMs() {
      return flushIntervalMs;
    }

    public Integer getMaxQueueSize() {
      return maxQueueSize;
    }
  }

  /**
   * Holds the configuration related to the sender. A sender can be a {@link HttpSender} or {@link UdpSender}
   *
   */
  @Getter
  public static class SenderConfiguration {
    /**
     * A custom sender set by our consumers. If set, nothing else has effect. Optional.
     */
    private Sender sender;

    /**
     * The Agent Host. Has no effect if the sender is set. Optional.
     */
    private String agentHost;

    /**
     * The Agent Port. Has no effect if the sender is set. Optional.
     */
    private Integer agentPort;

    /**
     * The endpoint, like https://jaeger-collector:14268/api/traces
     */
    private String endpoint;

    /**
     * The Auth Token to be added as "Bearer" on Authorization headers for requests sent to the endpoint
     */
    private String authToken;

    /**
     * The Basic Auth username to be added on Authorization headers for requests sent to the endpoint
     */
    private String authUsername;

    /**
     * The Basic Auth password to be added on Authorization headers for requests sent to the endpoint
     */
    private String authPassword;

    /**
     * New instances of this class are provided via the Builder
     */
    private SenderConfiguration() {
    }

    private SenderConfiguration(SenderConfiguration.Builder builder) {
      this.sender = builder.sender;
      this.agentHost = builder.agentHost;
      this.agentPort = builder.agentPort;
      this.endpoint = builder.endpoint;
      this.authToken = builder.authToken;
      this.authUsername = builder.authUsername;
      this.authPassword = builder.authPassword;
    }

    /**
     * Returns a sender if one was given when creating the configuration, or attempts to create a sender based on the
     * configuration's state.
     * @return the sender passed via the constructor or a properly configured sender
     */
    public Sender getSender() {
      // if we have a sender, that's the one we return
      if (null != this.sender) {
        return this.sender;
      }

      if (null != endpoint && !endpoint.isEmpty()) {
        log.debug("The endpoint is set. Attempting to configure HttpSender for it.");
        Interceptor authInterceptor = null;

        if (null != authUsername && !authUsername.isEmpty()
                && null != authPassword && !authPassword.isEmpty()) {
          log.debug("Using HTTP Basic authentication with data from the environment variables.");
          authInterceptor = getAddBasicAuthInterceptor(authUsername, authPassword);
        } else if (null != authToken && !authToken.isEmpty()) {
          log.debug("Auth Token environment variable found.");
          authInterceptor = getAddTokenInterceptor(authToken);
        }

        // at this point, the authInterceptor can still be null
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        if (null != authInterceptor) {
          log.debug("All requests to the endpoint will have an Authentication header set.");
          builder.addInterceptor(authInterceptor);
        }

        log.debug("Using the HTTP Sender to send spans directly to the endpoint.");
        return new HttpSender(endpoint, builder.build());
      }

      log.debug("Using the UDP Sender to send spans to the agent.");
      return new UdpSender(
              stringOrDefault(this.agentHost, UdpSender.DEFAULT_AGENT_UDP_HOST),
              numberOrDefault(this.agentPort, UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT).intValue(),
              0 /* max packet size */);
    }

    /**
     * Attempts to create a new {@link SenderConfiguration} based on the environment variables
     * @return a new sender configuration based on environment variables
     */
    public static SenderConfiguration fromEnv() {
      String agentHost = getProperty(JAEGER_AGENT_HOST);
      Integer agentPort = getPropertyAsInt(JAEGER_AGENT_PORT);

      String collectorEndpoint = getProperty(JAEGER_ENDPOINT);
      String authToken = getProperty(JAEGER_AUTH_TOKEN);
      String authUsername = getProperty(JAEGER_USER);
      String authPassword = getProperty(JAEGER_PASSWORD);

      return new Configuration.SenderConfiguration.Builder()
              .agentHost(agentHost)
              .agentPort(agentPort)
              .endpoint(collectorEndpoint)
              .authToken(authToken)
              .authUsername(authUsername)
              .authPassword(authPassword)
              .build();
    }

    private Interceptor getAddTokenInterceptor(final String authToken) {
      return getAuthInterceptor("Bearer " + authToken);
    }

    private Interceptor getAddBasicAuthInterceptor(final String username, final String password) {
      return getAuthInterceptor(Credentials.basic(username, password));
    }

    private Interceptor getAuthInterceptor(final String headerValue) {
      return new Interceptor() {
        @Override
        public Response intercept(Chain chain) throws IOException {
          return chain.proceed(
                  chain.request()
                          .newBuilder()
                          .addHeader("Authorization", headerValue)
                          .build()
          );
        }
      };
    }

    public static class Builder {
      private Sender sender;
      private String agentHost = null;
      private Integer agentPort = null;
      private String endpoint = null;
      private String authToken = null;
      private String authUsername = null;
      private String authPassword = null;

      public Builder sender(Sender sender) {
        this.sender = sender;
        return this;
      }

      public Builder agentHost(String agentHost) {
        this.agentHost = agentHost;
        return this;
      }

      public Builder agentPort(Integer agentPort) {
        this.agentPort = agentPort;
        return this;
      }

      public Builder endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
      }

      public Builder authToken(String authToken) {
        this.authToken = authToken;
        return this;
      }

      public Builder authUsername(String authUsername) {
        this.authUsername = authUsername;
        return this;
      }

      public Builder authPassword(String authPassword) {
        this.authPassword = authPassword;
        return this;
      }

      public Configuration.SenderConfiguration build() {
        return new Configuration.SenderConfiguration(this);
      }
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
