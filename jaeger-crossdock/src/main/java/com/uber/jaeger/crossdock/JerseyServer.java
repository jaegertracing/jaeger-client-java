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

package com.uber.jaeger.crossdock;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.Configuration.ReporterConfiguration;
import com.uber.jaeger.Configuration.SamplerConfiguration;
import com.uber.jaeger.crossdock.resources.behavior.EndToEndBehavior;
import com.uber.jaeger.crossdock.resources.behavior.ExceptionMapper;
import com.uber.jaeger.crossdock.resources.behavior.TraceBehavior;
import com.uber.jaeger.crossdock.resources.behavior.http.EndToEndBehaviorResource;
import com.uber.jaeger.crossdock.resources.behavior.http.TraceBehaviorResource;
import com.uber.jaeger.crossdock.resources.behavior.tchannel.TChannelServer;
import com.uber.jaeger.crossdock.resources.health.HealthResource;
import com.uber.jaeger.filters.jaxrs2.ClientFilter;
import com.uber.jaeger.filters.jaxrs2.ServerFilter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.HttpSender;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;
import com.uber.tchannel.api.TChannel.Builder;
import io.opentracing.Tracer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import org.apache.log4j.BasicConfigurator;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class JerseyServer {
  private static final String SAMPLING_HOST_PORT = "SAMPLING_HOST_PORT";
  private static final String AGENT_HOST = "AGENT_HOST";
  private static final String COLLECTOR_HOST_PORT = "COLLECTOR_HOST_PORT";

  // TODO should not be static, should be final
  public static Client client;

  private final HttpServer server;

  private final Configuration config;

  public JerseyServer(String hostPort, Configuration config, List<Object> resources) {
    this.config = config;

    final String samplingType = ConstSampler.TYPE;
    final Number samplingParam = 0;
    final boolean logging = true;

    // create a resource config that scans for JAX-RS resources and providers
    final ResourceConfig rc = new ResourceConfig();

    resources.forEach(rc::register);

    // register a tracer with TracingUtils before proceeding
    rc.register(new ServerFilter(config.getTracer()))
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
    String baseUri = String.format("http://%s/", hostPort);
    server = GrizzlyHttpServerFactory.createHttpServer(URI.create(baseUri), rc);
    client = initializeClient(config);
  }

  public List<NetworkListener> getNetworkListeners() {
    return new ArrayList<>(server.getListeners());
  }

  public void addNetworkListener(NetworkListener networkListener) {
    server.addListener(networkListener);
  }

  private static Client initializeClient(final Configuration config) {
    return ClientBuilder.newClient()
        .register(ExceptionMapper.class)
        .register(new ClientFilter(config.getTracer()))
        .register(JacksonFeature.class);
  }

  public void shutdown() throws ExecutionException, InterruptedException {
    server.shutdown().get();
  }

  public Tracer getTracer() {
    return config.getTracer();
  }

  public static void main(String[] args) throws Exception {
    BasicConfigurator.configure();

    String serviceName = serviceNameFromEnv();

    String samplingType = ConstSampler.TYPE;
    Number samplingParam = 0;

    Configuration config = new Configuration(
        serviceName,
        new SamplerConfiguration(samplingType, samplingParam),
        new ReporterConfiguration(true, null, null, null, null));

    Tracer tracer = config.getTracer();

    JerseyServer server = new JerseyServer("0.0.0.0:8081", config,
        Arrays.asList(new TraceBehaviorResource(tracer),
            new EndToEndBehaviorResource(new EndToEndBehavior(getEvn(SAMPLING_HOST_PORT, "jaeger-agent:5778"),
                "crossdock-" + serviceName,
                senderFromEnv(getEvn(COLLECTOR_HOST_PORT, "jaeger-collector:14268"),
                    getEvn(AGENT_HOST, "jaeger-agent")))),
            new HealthResource()));

    server.addNetworkListener(new NetworkListener("health", "0.0.0.0", 8080));
    Builder tchannelBuilder = new Builder(serviceName);
    tchannelBuilder.setServerPort(8082);
    new TChannelServer(tchannelBuilder, new TraceBehavior(tracer), tracer).start();
  }

  private static String getEvn(String envName, String defaultValue) {
    String env = System.getenv(envName);
    if (env == null) {
      return defaultValue;
    }
    return env;
  }

  private static Sender senderFromEnv(String collectorHostPort, String agentHost) {
    String senderEnvVar = System.getenv(Constants.ENV_PROP_SENDER_TYPE);
    if ("http".equalsIgnoreCase(senderEnvVar)) {
      return new HttpSender(String.format("http://%s/api/traces", collectorHostPort));
    } else if ("udp".equalsIgnoreCase(senderEnvVar) || senderEnvVar == null || senderEnvVar.isEmpty()) {
      return new UdpSender(agentHost, 0, 0);
    }

    throw new IllegalStateException("Env variable " + Constants.ENV_PROP_SENDER_TYPE
        + ", is not valid, choose 'udp' or 'http'");
  }

  private static String serviceNameFromEnv() {
    String senderEnvVar = System.getenv(Constants.ENV_PROP_SENDER_TYPE);
    if (senderEnvVar == null || senderEnvVar.isEmpty()) {
      return "java";
    }
    return "java-" + senderEnvVar;
  }
}
