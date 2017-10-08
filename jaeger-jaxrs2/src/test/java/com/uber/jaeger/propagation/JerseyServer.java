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

package com.uber.jaeger.propagation;

import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.filters.jaxrs2.ServerFilter;
import io.opentracing.Tracer;
import java.io.IOException;
import java.net.URI;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class JerseyServer {
  // Base URI the Grizzly HTTP server will listen on
  public static final String BASE_URI = "http://localhost:8080/";
  private final Tracer tracer;
  private final TraceContext traceContext;
  private HttpServer server;

  public JerseyServer(Tracer tracer, TraceContext traceContext) throws IOException {
    this.tracer = tracer;
    this.traceContext = traceContext;
    server = getServer();
    server.start();
  }

  /**
   * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
   *
   * @return Grizzly HTTP server.
   */
  public HttpServer getServer() {
    // create a resource config that scans for JAX-RS resources and providers
    final ResourceConfig rc =
        new ResourceConfig()
            .register(new ServerFilter(tracer, traceContext))
            .register(
                new AbstractBinder() {
                  @Override
                  protected void configure() {
                    bind(tracer).to(Tracer.class);
                    bind(traceContext).to(TraceContext.class);
                  }
                })
            .packages(JerseyHandler.class.getPackage().toString())
            .register(JacksonFeature.class);

    // create and start a new instance of grizzly http server
    // exposing the Jersey application at BASE_URI
    return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
  }

  public void start() throws IOException {
    server.start();
  }

  public void stop() {
    server.shutdownNow();
  }
}
