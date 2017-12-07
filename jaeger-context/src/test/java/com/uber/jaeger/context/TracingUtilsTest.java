/*
 * Copyright (c) 2017, The Jaeger Authors
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

package com.uber.jaeger.context;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.utils.TestUtils;
import io.opentracing.Tracer;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

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
    Assert.assertNotNull(tracer);

    TracingUtils.setTracer(tracer);
    Assert.assertNotNull(TracingUtils.getTraceContext());
  }

  @Test(expected = IllegalStateException.class)
  public void tracedExecutorWithoutGlobalTracer() throws Exception {
    TracingUtils.tracedExecutor(null);
  }

  @Test()
  public void tracedExecutor() throws Exception {
    Tracer tracer = new Configuration("boop").getTracer();
    Assert.assertNotNull(tracer);
    TracingUtils.setTracer(tracer);
    Assert.assertNotNull(TracingUtils.tracedExecutor(Executors.newSingleThreadExecutor()));
  }
}
