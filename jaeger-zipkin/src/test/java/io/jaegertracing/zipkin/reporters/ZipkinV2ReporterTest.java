/*
 * Copyright (c) 2018, The Jaeger Authors
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

package io.jaegertracing.zipkin.reporters;

import static org.junit.Assert.assertEquals;

import io.jaegertracing.JaegerSpan;
import io.jaegertracing.JaegerTracer;
import io.jaegertracing.metrics.InMemoryMetricsFactory;
import io.jaegertracing.reporters.Reporter;
import io.jaegertracing.samplers.ConstSampler;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.codec.Encoding;
import zipkin2.junit.ZipkinRule;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

public class ZipkinV2ReporterTest {
  @Rule public ZipkinRule zipkinRule = new ZipkinRule();

  Sender sender;
  AsyncReporter<zipkin2.Span> zipkinReporter;
  Reporter reporter;
  JaegerTracer tracer;

  @Before
  public void setup() throws Exception {
    sender = URLConnectionSender.newBuilder()
        .encoding(Encoding.JSON)
        .endpoint(zipkinRule.httpUrl() + "/api/v2/spans")
        .build();

    zipkinReporter = zipkin2.reporter.AsyncReporter.builder(sender)
        .messageTimeout(0, TimeUnit.MILLISECONDS)
        .build();

    reporter = new ZipkinV2Reporter(zipkinReporter);

    tracer = new JaegerTracer.Builder("test-sender")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .withMetricsFactory(new InMemoryMetricsFactory())
            .build();
  }

  @Test
  public void testConvertsAndSendsSpan() throws Exception {
    JaegerSpan span = (JaegerSpan) tracer.buildSpan("raza").start();
    span.finish();

    reporter.report(span);
    zipkinReporter.flush();

    List<List<zipkin2.Span>> spans = zipkinRule.getTraces();
    assertEquals(spans.get(0).get(0).name(), "raza");
  }
}
