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

package io.jaegertracing.crossdock.resources.behavior;

import io.jaegertracing.JaegerTracer;
import io.jaegertracing.crossdock.api.CreateTracesRequest;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.metrics.NoopMetricsFactory;
import io.jaegertracing.internal.reporters.RemoteReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.internal.samplers.HttpSamplingManager;
import io.jaegertracing.internal.samplers.ProbabilisticSampler;
import io.jaegertracing.internal.samplers.RemoteControlledSampler;
import io.jaegertracing.spi.Reporter;
import io.jaegertracing.spi.Sampler;
import io.jaegertracing.spi.Sender;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.HashMap;
import java.util.Map;

public class EndToEndBehavior {

  private final Map<String, Tracer> tracers;

  public EndToEndBehavior(Map<String, Tracer> tracers) {
    this.tracers = new HashMap<>(tracers);
  }

  public EndToEndBehavior(String samplingHostPort, String serviceName, Sender sender) {
    Metrics metrics = new Metrics(new NoopMetricsFactory());
    Reporter reporter = new RemoteReporter.Builder()
        .withSender(sender)
        .withFlushInterval(1000)
        .withMaxQueueSize(100)
        .withMetrics(metrics)
        .build();

    ConstSampler constSampler = new ConstSampler(true);

    tracers = new HashMap<>();
    tracers.put(RemoteControlledSampler.TYPE, getRemoteTracer(metrics, reporter, serviceName, samplingHostPort));
    tracers.put(ConstSampler.TYPE,
        new JaegerTracer.Builder(serviceName).withReporter(reporter).withSampler(constSampler).build());
  }

  private Tracer getRemoteTracer(Metrics metrics, Reporter reporter, String serviceName, String samplingHostPort) {
    Sampler initialSampler = new ProbabilisticSampler(1.0);
    HttpSamplingManager manager = new HttpSamplingManager(samplingHostPort);

    RemoteControlledSampler remoteSampler = new RemoteControlledSampler.Builder(serviceName)
        .withSamplingManager(manager)
        .withInitialSampler(initialSampler)
        .withMetrics(metrics)
        .withPollingInterval(5000)
        .build();

    return new JaegerTracer.Builder(serviceName)
        .withReporter(reporter)
        .withSampler(remoteSampler)
        .build();
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
      Span span = builder.start();
      span.finish();
    }
  }
}
