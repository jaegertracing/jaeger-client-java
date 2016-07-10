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

import com.google.common.base.Strings;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.reporters.*;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.HTTPSamplingManager;
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.samplers.RateLimitingSampler;
import com.uber.jaeger.samplers.RemoteControlledSampler;
import com.uber.jaeger.samplers.Sampler;
import com.uber.jaeger.senders.UDPSender;

public class Configuration {

    /**
     * The serviceName that the tracer will use
     */
    private final String serviceName;

    private final SamplerConfiguration samplerConfig;

    private final ReporterConfiguration reporterConfig;

    public Configuration(String serviceName,
                         SamplerConfiguration samplerConfig,
                         ReporterConfiguration reporterConfig) {
        if (Strings.isNullOrEmpty(serviceName)) {
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
    }

    public Tracer.Builder getTracerBuilder(StatsFactory statsFactory) {
        Metrics metrics = new Metrics(statsFactory);
        Reporter reporter = reporterConfig.getReporter(metrics);
        Sampler sampler = samplerConfig.createSampler(serviceName, metrics);
        return new Tracer.Builder(serviceName, reporter, sampler).withMetrics(metrics);
    }

    public Tracer getNoopTracer() {
        Reporter reporter = new NoopReporter();
        Sampler sampler = new ConstSampler(false);
        return new Tracer.Builder(serviceName, reporter, sampler).build();
    }

    /**
     * SamplerConfiguration allows to configure which sampler the tracer will use.
     */
    public static class SamplerConfiguration {

        private final static String defaultManagerHostPort = "localhost:5778";

        public final static String PROBABILISTIC = "probabilistic";
        public final static String RATE_LIMITING = "ratelimiting";
        public final static String CONST = "const";
        public final static String REMOTE = "remote";

        /**
         * The type of sampler to use in the tracer. Optional.
         * Valid values: remote (default), ratelimiting, probabilistic, const.
         */
        private final String type;

        /**
         * The integer or floating point value that makes sense for the correct samplerType.
         * Optional.
         */
        private final Number param;

        /**
         * HTTP host:port of the sampling manager that can provide sampling strategy
         * to this service. Optional.
         */
        private final String managerHostPort;

        public SamplerConfiguration(String type, Number param) {
            this(type, param, null);
        }

        public SamplerConfiguration(String type, Number param, String managerHostPort) {
            this.type = type;
            this.param = param;
            this.managerHostPort = managerHostPort;
        }

        private Sampler createSampler(String serviceName, Metrics metrics) {
            String samplerType = stringOrDefault(this.type, REMOTE);
            Number samplerParam = numberOrDefault(this.param, 0.001);
            String hostPort = stringOrDefault(this.managerHostPort, defaultManagerHostPort);

            if (samplerType.equals(CONST)) {
                return new ConstSampler(samplerParam.intValue() == 1);
            }

            if (samplerType.equals(PROBABILISTIC)) {
                return new ProbabilisticSampler(samplerParam.doubleValue());
            }

            if (samplerType.equals(RATE_LIMITING)) {
                return new RateLimitingSampler(samplerParam.intValue());
            }

            if (samplerType.equals(REMOTE)) {
                Sampler initialSampler = new ProbabilisticSampler(samplerParam.doubleValue());

                HTTPSamplingManager manager = new HTTPSamplingManager(hostPort);

                return new RemoteControlledSampler(serviceName, manager, initialSampler, metrics);
            }

            throw new IllegalStateException(String.format("Invalid sampling strategy %s", samplerType));
        }
    }

    public static class ReporterConfiguration {

        private final static String defaultAgentHost = "localhost";
        private final static int defaultAgentPort = 5775;
        private final static int defaultFlushIntervalMs = 1000;
        private final static int defaultMaxQueueSize = 100;

        private final Boolean logSpans;

        private final String agentHost;

        private final Integer agentPort;

        private final Integer flushIntervalMs;

        private final Integer maxQueueSize;

        public ReporterConfiguration() {
            this(null, null, null, null, null);
        }

        public ReporterConfiguration(Boolean logSpans, String agentHost, Integer agentPort, Integer flushIntervalMs, Integer maxQueueSize) {
            this.logSpans = logSpans;
            this.agentHost = agentHost;
            this.agentPort = agentPort;
            this.flushIntervalMs = flushIntervalMs;
            this.maxQueueSize = maxQueueSize;
        }

        private Reporter getReporter(Metrics metrics) {
            UDPSender sender = new UDPSender(
                    stringOrDefault(this.agentHost, defaultAgentHost),
                    numberOrDefault(this.agentPort, defaultAgentPort).intValue(),
                    0 /* max packet size */);

            Reporter reporter = new RemoteReporter(
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
    }

    private static String stringOrDefault(String value, String defaultValue) {
        return value != null && value.length() > 0 ? value : defaultValue;
    }

    private static Number numberOrDefault(Number value, Number defaultValue) {
        return value != null ? value : defaultValue;
    }
}
