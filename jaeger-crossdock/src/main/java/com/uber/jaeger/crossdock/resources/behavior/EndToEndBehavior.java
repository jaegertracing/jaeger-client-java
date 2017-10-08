/*
 * Copyright (c) 2017, Uber Technologies, Inc
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
package com.uber.jaeger.crossdock.resources.behavior;

import com.uber.jaeger.crossdock.api.CreateTracesRequest;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.HttpSamplingManager;
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.samplers.RemoteControlledSampler;
import com.uber.jaeger.samplers.Sampler;
import com.uber.jaeger.senders.Sender;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.HashMap;
import java.util.Map;

public class EndToEndBehavior {

  private final Map<String, Tracer> tracers;

  public EndToEndBehavior(Map<String, Tracer> tracers) {
    this.tracers = new HashMap<>(tracers);
  }

  public EndToEndBehavior(String jaegerHost, String serviceName, Sender sender) {
    StatsFactory statsFactory = new StatsFactoryImpl(new NullStatsReporter());
    Metrics metrics = new Metrics(statsFactory);
    Reporter reporter = new RemoteReporter(sender, 1000, 100, metrics);

    ConstSampler constSampler = new ConstSampler(true);

    tracers = new HashMap<>();
    tracers.put(RemoteControlledSampler.TYPE, getRemoteTracer(metrics, reporter, serviceName, jaegerHost));
    tracers.put(ConstSampler.TYPE, new com.uber.jaeger.Tracer.Builder(serviceName, reporter, constSampler).build());
  }

  private Tracer getRemoteTracer(Metrics metrics, Reporter reporter, String serviceName, String jaegerHost) {
    Sampler initialSampler = new ProbabilisticSampler(1.0);
    HttpSamplingManager manager = new HttpSamplingManager(jaegerHost + ":5778");

    RemoteControlledSampler remoteSampler = new RemoteControlledSampler(serviceName, manager, initialSampler,
        metrics, 5000);

    com.uber.jaeger.Tracer.Builder remoteTracerBuilder = new com.uber.jaeger.Tracer.Builder(serviceName, reporter,
        remoteSampler);
    return remoteTracerBuilder.build();
  }

  public void generateTraces(CreateTracesRequest request) {
    String samplerType = request.getType();
    Tracer tracer = tracers.get(samplerType);
    for (int i = 0; i < request.getCount(); i++) {
      Tracer.SpanBuilder builder = tracer.buildSpan(request.getOperation());
      if (request.getTags() != null) {
        for (Map.Entry<String, String> kv: request.getTags().entrySet()) {
          builder.withTag(kv.getKey(), kv.getValue());
        }
      }
      Span span = builder.startManual();
      span.finish();
    }
  }
}
