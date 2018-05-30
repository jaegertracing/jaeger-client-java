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

import io.jaegertracing.JaegerSpan;
import io.jaegertracing.reporters.Reporter;
import io.jaegertracing.zipkin.V2SpanConverter;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;

/**
 * Wrapper around a zipkin v2 AsyncReporter that reports spans using the newer v2 JaegerSpan class
 */
public class ZipkinV2Reporter implements Reporter {
  public final AsyncReporter<zipkin2.Span> reporter;

  public ZipkinV2Reporter(AsyncReporter<Span> reporter) {
    this.reporter = reporter;
  }

  @Override
  public void report(JaegerSpan span) {
    reporter.report(V2SpanConverter.convertSpan(span));
  }

  @Override
  public void close() {
    reporter.close();
  }
}
