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

package com.uber.jaeger.tracerresolver;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.Configuration.ReporterConfiguration;
import com.uber.jaeger.Configuration.SamplerConfiguration;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.Sampler;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;

import io.opentracing.contrib.tracerresolver.TracerResolver;

import java.text.NumberFormat;
import java.text.ParseException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JaegerTracerResolver extends TracerResolver {

  /**
   * Prefix for all properties used to configure the {@link TracerResolver}.
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

  @Override
  protected io.opentracing.Tracer resolve() {
    Configuration config = new Configuration(getProperty(JAEGER_SERVICE_NAME),
        getSamplerConfiguration(), getReporterConfiguration());

    return config.getTracer();
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
