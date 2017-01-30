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
package com.uber.jaeger.crossdock.resources.behavior;

import com.uber.jaeger.crossdock.api.CreateTracesRequest;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.*;
import com.uber.jaeger.senders.UDPSender;
import io.opentracing.Span;
import io.opentracing.Tracer;

import java.util.HashMap;
import java.util.Map;

public class EndToEndBehavior {
  private static final String SERVICE_NAME = "crossdock-java";

  private Map<String, Tracer> tracers;

  public EndToEndBehavior() {
    StatsFactory statsFactory = new StatsFactoryImpl(new NullStatsReporter());
    Metrics metrics = new Metrics(statsFactory);
    Reporter reporter = getReporter(metrics);

    ConstSampler constSampler = new ConstSampler(true);

    tracers = new HashMap<String, Tracer>();
    tracers.put(RemoteControlledSampler.TYPE, getRemoteTracer(metrics, reporter));
    tracers.put(ConstSampler.TYPE, new com.uber.jaeger.Tracer.Builder(SERVICE_NAME, reporter, constSampler).build());
  }

  private Reporter getReporter(Metrics metrics) {
    UDPSender sender = new UDPSender("test_driver", 5775, 0);
    return new RemoteReporter(sender, 1000, 100, metrics);
  }

  private Tracer getRemoteTracer(Metrics metrics, Reporter reporter) {
    Sampler initialSampler = new ProbabilisticSampler(1.0);
    HTTPSamplingManager manager = new HTTPSamplingManager("test_driver:5778");

    RemoteControlledSampler remoteSampler = new RemoteControlledSampler(SERVICE_NAME, manager, initialSampler, metrics, 5000);

    com.uber.jaeger.Tracer.Builder remoteTracerBuilder = new com.uber.jaeger.Tracer.Builder(SERVICE_NAME, reporter, remoteSampler);
    return remoteTracerBuilder.build();
  }

  public EndToEndBehavior(Map<String, Tracer> tracers) {
    this.tracers = tracers;
  }

  public void GenerateTraces(CreateTracesRequest request) {
    String samplerType = request.getType();
    Tracer tracer = tracers.get(samplerType);
    for (int i = 0; i < request.getCount(); i++) {
      Tracer.SpanBuilder builder = tracer.buildSpan(request.getOperation());
      for (Map.Entry<String, String> kv: request.getTags().entrySet()) {
        builder.withTag(kv.getKey(), kv.getValue());
      }
      Span span = builder.start();
      span.finish();
    }
  }
}
