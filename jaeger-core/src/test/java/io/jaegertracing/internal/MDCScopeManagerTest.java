package io.jaegertracing.internal;

import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

import static org.junit.Assert.*;

public class MDCScopeManagerTest {

  InMemoryReporter reporter;
  JaegerTracer defaultTracer;

  @Before
  public void setUp() {
    reporter = new InMemoryReporter();
    defaultTracer = createTracer(new MDCScopeManager.Builder().build());
  }

  @Test
  public void testActiveSpan() {
    Span mockSpan = Mockito.mock(JaegerSpan.class);
    try (Scope scope = defaultTracer.activateSpan(mockSpan)) {
      assertEquals(mockSpan, defaultTracer.activeSpan());
    }

  }

  @Test
  public void testDefaultCreation() {
    Span span = defaultTracer.buildSpan("test Default").start();
    Scope scope = defaultTracer.activateSpan(span);
    assertNotNull(MDC.get("traceId"));
    assertNotNull(MDC.get("spanId"));
    assertNotNull(MDC.get("sampled"));

    scope.close();

    assertNull(MDC.get("traceId"));
    assertNull(MDC.get("spanId"));
    assertNull(MDC.get("sampled"));
  }


  @Test
  public void testCustomKeysCreation() {

    ScopeManager mdcScopeManager = new MDCScopeManager.
            Builder()
            .withMDCTraceIdKey("CustomTraceId")
            .withMDCSampledKey("customSampled")
            .withMDCSpanIdKey("customSpanId")
            .build();

    Tracer tracer = createTracer(mdcScopeManager);
    Span span = tracer.buildSpan("testCustomKeysCreation").start();
    Scope scope = tracer.activateSpan(span);

    assertNotNull(MDC.get("CustomTraceId"));
    assertNotNull(MDC.get("customSampled"));
    assertNotNull(MDC.get("customSpanId"));

    scope.close();

  }

  @Test
  public void testCustomAndDefaultKeysCreation() {

    ScopeManager mdcScopeManager = new MDCScopeManager.
            Builder()
            .withMDCSampledKey("customSampled")
            .withMDCSpanIdKey("customSpanId")
            .build();

    Tracer tracer = createTracer(mdcScopeManager);
    Span span = tracer.buildSpan("testCustomAndDefaultKeysCreation").start();
    Scope scope = tracer.activateSpan(span);

    assertNotNull(MDC.get("traceId"));
    assertNotNull(MDC.get("customSampled"));
    assertNotNull(MDC.get("customSpanId"));

    scope.close();

  }

  private JaegerTracer createTracer(ScopeManager scopeManager) {
    return new JaegerTracer.Builder("MDCScopeManagerTest")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .withScopeManager(scopeManager)
            .build();
  }

}