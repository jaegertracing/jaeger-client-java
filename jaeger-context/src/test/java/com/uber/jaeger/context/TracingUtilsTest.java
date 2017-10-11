package com.uber.jaeger.context;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.utils.TestUtils;
import io.opentracing.Tracer;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.Executors;

import static org.junit.Assert.assertNotNull;

public class TracingUtilsTest {

  @After
  public void tearDown() throws Exception {
    TestUtils.resetGlobalTracer();
  }

  @Test(expected = IllegalStateException.class)
  public void getTraceContextWithoutGlobalTracer() throws Exception {
    TracingUtils.getTraceContext();
  }

  @Test
  public void getTraceContext() {
    Tracer tracer = new Configuration("boop").getTracer();
    assertNotNull(tracer);
    assertNotNull(TracingUtils.getTraceContext());
  }

  @Test(expected = IllegalStateException.class)
  public void tracedExecutorWithoutGlobalTracer() throws Exception {
    TracingUtils.tracedExecutor(null);
  }

  @Test()
  public void tracedExecutor() throws Exception {
    Tracer tracer = new Configuration("boop").getTracer();
    assertNotNull(tracer);
    assertNotNull(TracingUtils.tracedExecutor(Executors.newSingleThreadExecutor()));
  }
}
