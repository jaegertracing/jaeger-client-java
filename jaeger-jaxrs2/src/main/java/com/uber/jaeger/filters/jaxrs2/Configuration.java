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
package com.uber.jaeger.filters.jaxrs2;

import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.propagation.ExtractorFactory;
import com.uber.jaeger.propagation.JerseyClientRequestInjector;
import com.uber.jaeger.propagation.JerseyContainerRequestExtractor;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.container.ContainerRequestContext;

public class Configuration extends com.uber.jaeger.Configuration {
    /**
     * lazy singleton Tracer initialized in getTracer()` method
     */
    private Tracer tracer;

    /**
     * A flag that sets the tracer to disabled.
     */
    private Boolean disable = false;

    /**
     * A interface that wraps an underlying metrics generatior in order to report Jaeger's metrics.
     */
    private StatsFactory statsFactory;

    public Configuration(String serviceName,
                         Boolean disable,
                         SamplerConfiguration samplerConfig,
                         ReporterConfiguration reporterConfig) {
        super(serviceName, samplerConfig, reporterConfig);

        if (disable != null) {
            this.disable = disable;
        }
    }

    public void setStatsFactory(StatsFactory statsFactory) {
        this.statsFactory = statsFactory;
    }

    public StatsFactory getStatsFactory() {
       return statsFactory;
    }

    public io.opentracing.Tracer getTracer() {
        if (disable) {
            getNoopTracer();
        }

        if (tracer != null) {
            return tracer;
        }

        if (statsFactory == null) {
            statsFactory = new StatsFactoryImpl(new NullStatsReporter());
        }

        tracer = this.getTracerBuilder(statsFactory)
                .register(ContainerRequestContext.class, new ExtractorFactory<ContainerRequestContext>() {
                    public JerseyContainerRequestExtractor provide(Tracer tracer) {
                        return new JerseyContainerRequestExtractor(tracer);
                    }
                })
                .register(ClientRequestContext.class, new JerseyClientRequestInjector()).build();
        return tracer;
    }
}
