package com.uber.jaeger.context;

import io.opentracing.ActiveSpanSource;
import io.opentracing.Span;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ActiveSpanSourceTraceContextTest {

  private ActiveSpanSource activeSpanSource = new ThreadLocalActiveSpanSource();

  @Mock private Span span;

  private ActiveSpanSourceTraceContext traceContext =
      new ActiveSpanSourceTraceContext(activeSpanSource);

  @Test
  public void pushAndPop() throws Exception {
    traceContext.push(span);
    Span actual = traceContext.pop();
    Assert.assertEquals(span, actual);
  }

  @Test
  public void getCurrentSpan() throws Exception {
    traceContext.push(span);
    Span actual = traceContext.getCurrentSpan();
    Assert.assertEquals(span, actual);
  }

  @Test
  public void isEmpty() throws Exception {
    Assert.assertTrue(traceContext.isEmpty());
  }
}
