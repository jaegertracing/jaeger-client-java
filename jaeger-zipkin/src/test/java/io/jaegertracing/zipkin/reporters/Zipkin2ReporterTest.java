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

import io.jaegertracing.Span;
import io.jaegertracing.Tracer;
import io.jaegertracing.metrics.InMemoryMetricsFactory;
import io.jaegertracing.reporters.Reporter;
import io.jaegertracing.samplers.ConstSampler;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin.junit.ZipkinRule;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;


public class Zipkin2ReporterTest {
  @Rule public ZipkinRule zipkinRule = new ZipkinRule();

  Sender sender;
  Reporter reporter;
  Tracer tracer;

  @Before
  public void setUp() throws Exception {
    sender = URLConnectionSender.newBuilder()
        .encoding(Encoding.JSON)
        .endpoint(zipkinRule.httpUrl() + "/api/v2/spans")
        .build();

    reporter = new Zipkin2Reporter(
        zipkin2.reporter.AsyncReporter.builder(sender)
        .messageTimeout(10, TimeUnit.SECONDS)
        .closeTimeout(10, TimeUnit.SECONDS)
        .build());

    tracer = new Tracer.Builder("test-sender")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .withMetricsFactory(new InMemoryMetricsFactory())
            .build();
  }

  @Test
  public void testConvertsAndSendsSpan() throws Exception {
    Span jaegerSpan = (Span)tracer.buildSpan("raza").start();
    jaegerSpan.finish();

    reporter.report(jaegerSpan);
    reporter.close();

    List<List<zipkin.Span>> spans = zipkinRule.getTraces();
    assertEquals(spans.get(0).get(0).name, "raza");
  }
}
