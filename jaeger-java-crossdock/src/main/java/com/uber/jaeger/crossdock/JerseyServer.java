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
package com.uber.jaeger.crossdock;

import java.net.URI;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.crossdock.resources.behavior.ExceptionMapper;
import com.uber.jaeger.crossdock.resources.behavior.http.TraceBehaviorResource;
import com.uber.jaeger.crossdock.resources.behavior.tchannel.TChannelServer;
import com.uber.jaeger.crossdock.resources.health.HealthResource;
import com.uber.jaeger.Configuration.ReporterConfiguration;
import com.uber.jaeger.Configuration.SamplerConfiguration;
import com.uber.jaeger.filters.jaxrs2.Configuration;
import com.uber.jaeger.filters.jaxrs2.TracingUtils;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class JerseyServer {
    private final HttpServer server;
    public static Client client;
    public final static String SERVICE_NAME = "java";


    public JerseyServer(String hostPort, Class... resourceClasses) {
        final String samplingType = SamplerConfiguration.CONST;
        final Number samplingParam = 0;
        final boolean disable = false;
        final boolean logging = true;

        final Configuration config = new Configuration(SERVICE_NAME, disable,
                new SamplerConfiguration(samplingType, samplingParam),
                new ReporterConfiguration(logging, null, null, null, null));

        // create a resource config that scans for JAX-RS resources and providers
        final ResourceConfig rc = new ResourceConfig();

        for (Class clz : resourceClasses) {
            rc.packages(clz.getPackage().getName());
        }

        rc.register(TracingUtils.serverFilter(config))
                .register(LoggingFilter.class)
                .register(ExceptionMapper.class)
                .register(JacksonFeature.class)
                .register(
                        new AbstractBinder() {
                            @Override
                            protected void configure() {
                                bind(config).to(Configuration.class);
                            }
                        });

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        String baseURI = String.format("http://%s/", hostPort);
        server = GrizzlyHttpServerFactory.createHttpServer(URI.create(baseURI), rc);
        client = initializeClient(config);
    }

    private static Client initializeClient(final Configuration config) {
        return ClientBuilder.newClient()
                .register(ExceptionMapper.class)
                .register(TracingUtils.clientFilter(config))
                .register(
                        new AbstractBinder() {
                            @Override
                            protected void configure() {
                                bind(TracingUtils.getTraceContext()).to(TraceContext.class);
                            }
                        })
                .register(JacksonFeature.class);
    }

    public void shutdown() {
        server.shutdown();
    }

    public static void main(String[] args) throws Exception {
        new JerseyServer("0.0.0.0:8081", TraceBehaviorResource.class);
        new TChannelServer(8082).start();
        new JerseyServer("0.0.0.0:8080", HealthResource.class);
    }
}