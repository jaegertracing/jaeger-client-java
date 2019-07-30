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

package io.jaegertracing.internal;

import io.jaegertracing.Configuration;
import io.opentracing.Scope;

import io.opentracing.Span;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class JaegerSubclassTest {
  private static class CustomConfiguration extends Configuration {
    private CustomConfiguration(String serviceName) {
      super(serviceName);
    }

    @Override
    public CustomTracer.CustomBuilder getTracerBuilder() {
      return (CustomTracer.CustomBuilder) super.getTracerBuilder();
    }

    @Override
    public synchronized CustomTracer getTracer() {
      return (CustomTracer) super.getTracer();
    }

    @Override
    protected CustomTracer.CustomBuilder createTracerBuilder(String serviceName) {
      return new CustomTracer.CustomBuilder(serviceName);
    }
  }

  private static class CustomTracer extends JaegerTracer {
    public static class CustomBuilder extends JaegerTracer.Builder {
      private CustomBuilder(String serviceName) {
        super(serviceName, new CustomObjectFactory());
      }

      @Override
      public CustomTracer build() {
        return (CustomTracer) super.build();
      }

      @Override
      protected JaegerTracer createTracer() {
        return new CustomTracer(this);
      }
    }

    public class CustomSpanBuilder extends JaegerTracer.SpanBuilder {
      protected CustomSpanBuilder(String operationName) {
        super(operationName);
      }

      @Override
      public CustomSpan start() {
        return (CustomSpan) super.start();
      }
    }

    private CustomTracer(CustomBuilder builder) {
      super(builder);
    }
  }

  private static class CustomSpan extends JaegerSpan {
    private CustomSpan(
        CustomTracer tracer,
        String operationName,
        CustomSpanContext context,
        long startTimeMicroseconds,
        long startTimeNanoTicks,
        boolean computeDurationViaNanoTicks,
        Map<String, Object> tags,
        List<Reference> references) {
      super(
          tracer,
          operationName,
          context,
          startTimeMicroseconds,
          startTimeNanoTicks,
          computeDurationViaNanoTicks,
          tags,
          references);
    }

    @Override
    public CustomSpanContext context() {
      return (CustomSpanContext) super.context();
    }
  }

  private static class CustomSpanContext extends JaegerSpanContext {
    private CustomSpanContext(
        long traceIdHigh,
        long traceIdLow,
        long spanId,
        long parentId,
        byte flags,
        Map<String, String> baggage,
        String debugId,
        CustomObjectFactory objectFactory) {
      super(traceIdHigh, traceIdLow, spanId, parentId, flags, baggage, debugId, objectFactory);
    }
  }

  private static class CustomObjectFactory extends JaegerObjectFactory {
    @Override
    public CustomSpan createSpan(
        JaegerTracer tracer,
        String operationName,
        JaegerSpanContext context,
        long startTimeMicroseconds,
        long startTimeNanoTicks,
        boolean computeDurationViaNanoTicks,
        Map<String, Object> tags,
        List<Reference> references) {
      return new CustomSpan(
          (CustomTracer) tracer,
          operationName,
          (CustomSpanContext) context,
          startTimeMicroseconds,
          startTimeNanoTicks,
          computeDurationViaNanoTicks,
          tags,
          references);
    }

    @Override
    public CustomSpanContext createSpanContext(
        long traceIdHigh,
        long traceIdLow,
        long spanId,
        long parentId,
        byte flags,
        Map<String, String> baggage,
        String debugId) {
      return new CustomSpanContext(traceIdHigh, traceIdLow, spanId, parentId, flags, baggage, debugId, this);
    }

    @Override
    public CustomTracer.CustomSpanBuilder createSpanBuilder(
        JaegerTracer tracer, String operationName) {
      return ((CustomTracer) tracer).new CustomSpanBuilder(operationName);
    }
  }

  @Test
  public void testTracer() {
    final CustomConfiguration config = new CustomConfiguration("test-service");
    final CustomTracer.CustomBuilder builder = config.getTracerBuilder();
    final CustomTracer tracer = builder.build();
    final Span span = tracer.buildSpan("test-operation").start();
    try (Scope scope = tracer.activateSpan(span)) {
      Assert.assertNotNull(tracer.scopeManager().activeSpan());
      Assert.assertTrue(tracer.scopeManager().activeSpan() instanceof CustomSpan);
      Assert.assertTrue(tracer.scopeManager().activeSpan().context() instanceof CustomSpanContext);
    }
    config.closeTracer();
  }
}
