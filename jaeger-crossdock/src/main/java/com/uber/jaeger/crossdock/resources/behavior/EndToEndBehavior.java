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

import com.uber.jaeger.Configuration;
import com.uber.jaeger.crossdock.api.CreateTracesRequest;
import io.opentracing.Span;
import io.opentracing.Tracer;

import java.util.Map;

public class EndToEndBehavior {

  private static final Configuration cfg = new Configuration(
      "crossdock-java",
      // TODO make polling interval available for sampler
      new Configuration.SamplerConfiguration("remote", 1, "test_driver:5778"),
      new Configuration.ReporterConfiguration(null, "test_driver", 5775, 1000, null));

  private io.opentracing.Tracer tracer;

  public EndToEndBehavior() {
    this(cfg.getTracer());
  }

  public EndToEndBehavior(io.opentracing.Tracer tracer) {
    this.tracer = tracer;
  }

  public void GenerateTraces(CreateTracesRequest request) {
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
