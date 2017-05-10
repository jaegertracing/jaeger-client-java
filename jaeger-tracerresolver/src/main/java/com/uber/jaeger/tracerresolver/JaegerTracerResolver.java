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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JaegerTracerResolver extends TracerResolver {

  /**
   * Prefix for all properties used to configure the {@link TracerResolver}.
   */
  public static final String JAEGER_PREFIX = "JAEGER_";

  /**
   * The UDP maximum packet size used when communicating with the agent.
   */
  public static final String JAEGER_AGENT_UDP_MAX_PACKET_SIZE = JAEGER_PREFIX + "AGENT_UDP_MAX_PACKET_SIZE";

  /**
   * The UDP port used to locate the agent.
   */
  public static final String JAEGER_AGENT_UDP_PORT = JAEGER_PREFIX + "AGENT_UDP_PORT";

  /**
   * The UDP host used to locate the agent.
   */
  public static final String JAEGER_AGENT_UDP_HOST = JAEGER_PREFIX + "AGENT_UDP_HOST";

  /**
   * The maximum queue size for use when reporting spans remotely.
   */
  public static final String JAEGER_REPORTER_MAX_QUEUE_SIZE = JAEGER_PREFIX + "REPORTER_MAX_QUEUE_SIZE";

  /**
   * The flush interval when reporting spans remotely.
   */
  public static final String JAEGER_REPORTER_FLUSH_INTERVAL = JAEGER_PREFIX + "REPORTER_FLUSH_INTERVAL";

  /**
   * The service name.
   */
  public static final String JAEGER_SERVICE_NAME = JAEGER_PREFIX + "SERVICE_NAME";

  static final int DEFAULT_REPORTER_FLUSH_INTERVAL = 500;
  static final int DEFAULT_REPORTER_MAX_QUEUE_SIZE = 1000;

  @Override
  protected io.opentracing.Tracer resolve() {
    String serviceName = getProperty(JAEGER_SERVICE_NAME);

    return new Tracer.Builder(serviceName, getReporter(), getSampler()).build();
  }

  protected static Reporter getReporter() {
    return new RemoteReporter(getSender(),
        getPropertyAsInt(JAEGER_REPORTER_FLUSH_INTERVAL, DEFAULT_REPORTER_FLUSH_INTERVAL),
        getPropertyAsInt(JAEGER_REPORTER_MAX_QUEUE_SIZE, DEFAULT_REPORTER_MAX_QUEUE_SIZE),
        getMetrics());
  }

  protected static Sender getSender() {
    return new UdpSender(getProperty(JAEGER_AGENT_UDP_HOST),
        getPropertyAsInt(JAEGER_AGENT_UDP_PORT, 0),
        getPropertyAsInt(JAEGER_AGENT_UDP_MAX_PACKET_SIZE, 0));
  }

  protected static Metrics getMetrics() {
    // TODO: Support other stats reporters
    return Metrics.fromStatsReporter(new NullStatsReporter());
  }

  protected static Sampler getSampler() {
    // TODO: Support other samplers
    return new ConstSampler(true);
  }

  private static String getProperty(String name) {
    return System.getProperty(name, System.getenv(name));
  }

  private static int getPropertyAsInt(String name, int def) {
    String value = getProperty(name);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        log.error("Failed to parse integer for property '" + name + "' with value '" + value + "'", e);
      }
    }
    return def;
  }

}
