package io.jaegertracing.internal;

import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

import static org.junit.Assert.*;

public class MDCScopeManagerTest {

  private InMemoryReporter reporter;
  private JaegerTracer defaultTracer;
  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";
  private static final String SAMPLED = "sampled";


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
  public void testNestedSpans() {
    Span parentSpan = defaultTracer.buildSpan("parent").start();
    try (Scope scope = defaultTracer.activateSpan(parentSpan)) {
      assertSpanContextEqualsToMDC((JaegerSpanContext) parentSpan.context(), TRACE_ID, SPAN_ID, SAMPLED);
      Span childSpan = defaultTracer.buildSpan("child").start();
      try (Scope childScope = defaultTracer.activateSpan(childSpan)) {
        assertSpanContextEqualsToMDC((JaegerSpanContext) childSpan.context(), TRACE_ID, SPAN_ID, SAMPLED);
      }
      assertSpanContextEqualsToMDC((JaegerSpanContext) parentSpan.context(), TRACE_ID, SPAN_ID, SAMPLED);
    }
    assertNullMDCKeys(TRACE_ID, SPAN_ID, SAMPLED);
  }


  @Test
  public void testDefaultCreation() {
    Span span = defaultTracer.buildSpan("test Default").start();
    Scope scope = defaultTracer.activateSpan(span);

    assertSpanContextEqualsToMDC((JaegerSpanContext) span.context(), TRACE_ID, SPAN_ID, SAMPLED);

    scope.close();
    assertNullMDCKeys(TRACE_ID, SPAN_ID, SAMPLED);
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

    assertSpanContextEqualsToMDC((JaegerSpanContext)span.context(), "CustomTraceId","customSpanId" ,"customSampled");

    scope.close();

    assertNullMDCKeys("CustomTraceId", "customSampled", "customSpanId");
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

    assertSpanContextEqualsToMDC((JaegerSpanContext)span.context(), TRACE_ID, "customSpanId","customSampled");

    scope.close();

    assertNullMDCKeys(TRACE_ID, "customSpanId", "customSampled");
  }

  private JaegerTracer createTracer(ScopeManager scopeManager) {
    return new JaegerTracer.Builder("MDCScopeManagerTest")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .withScopeManager(scopeManager)
            .build();
  }

  private void assertSpanContextEqualsToMDC(JaegerSpanContext context, String traceIDKey, String spanIdKey, String sampledKey) {
    assertEquals(context.toTraceId(), MDC.get(traceIDKey));
    assertEquals(context.toSpanId(), MDC.get(spanIdKey));
    assertEquals(String.valueOf(context.isSampled()), MDC.get(sampledKey));
  }

  private void assertNullMDCKeys(String traceIDKey, String spanIdKey, String sampleKey) {
    assertNull(MDC.get(traceIDKey));
    assertNull(MDC.get(spanIdKey));
    assertNull(MDC.get(sampleKey));
  }

}