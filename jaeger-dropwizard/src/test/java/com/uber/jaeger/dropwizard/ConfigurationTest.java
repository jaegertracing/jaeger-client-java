/*
 * Copyright (c) 2016, Uber Technologies, Inc
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

package com.uber.jaeger.dropwizard;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.uber.jaeger.reporters.NoopReporter;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.samplers.RemoteControlledSampler;
import com.uber.jaeger.samplers.Sampler;
import java.lang.reflect.Field;
import org.junit.Test;

public class ConfigurationTest {

  @Test
  public void testInstantiableWithNulls() throws Exception {
    new Configuration("serviceName", null, null, null);
  }

  @Test
  public void testCanCloseTracerWhenDisabled() throws Exception {
    Configuration configuration = new Configuration("serviceName", true, null, null);
    configuration.closeTracer();
  }

  @Test
  public void testCanCloseTracer() throws Exception {
    Configuration configuration = new Configuration("serviceName", false, null, null);
    configuration.closeTracer();
  }

  @Test
  public void testGetTracerWhenDisabled() throws Exception {
    Configuration configuration = new Configuration("serviceName", true, null, null);
    io.opentracing.Tracer tracer = configuration.getTracer();
    assertThat(tracer, instanceOf(com.uber.jaeger.Tracer.class));

    Field reporterField = com.uber.jaeger.Tracer.class.getDeclaredField("reporter");
    reporterField.setAccessible(true);
    Reporter reporter = (Reporter) reporterField.get(tracer);
    assertThat(reporter, instanceOf(NoopReporter.class));

    Field samplerField = com.uber.jaeger.Tracer.class.getDeclaredField("sampler");
    samplerField.setAccessible(true);
    Sampler sampler = (Sampler) samplerField.get(tracer);
    assertThat(sampler, instanceOf(ConstSampler.class));

    Field samplerDecisionField = ConstSampler.class.getDeclaredField("decision");
    samplerDecisionField.setAccessible(true);
    Boolean decisionValue = (Boolean) samplerDecisionField.get(sampler);
    assertFalse(decisionValue);
  }

  @Test
  public void testMultipleGetTracerCallsReturnsSameInstanceOfTracer() {
    Configuration configuration = new Configuration("serviceName", true, null, null);
    io.opentracing.Tracer tracer = configuration.getTracer();

    assertEquals(tracer, configuration.getTracer());
  }

  @Test
  public void testGetTracerWhenEnabled() throws Exception {
    Configuration configuration = new Configuration("serviceName", false, null, null);
    io.opentracing.Tracer tracer = configuration.getTracer();
    assertThat(tracer, instanceOf(com.uber.jaeger.Tracer.class));

    Field reporterField = com.uber.jaeger.Tracer.class.getDeclaredField("reporter");
    reporterField.setAccessible(true);
    Reporter reporter = (Reporter) reporterField.get(tracer);
    assertThat(reporter, instanceOf(RemoteReporter.class));

    Field samplerField = com.uber.jaeger.Tracer.class.getDeclaredField("sampler");
    samplerField.setAccessible(true);
    Sampler sampler = (Sampler) samplerField.get(tracer);
    assertThat(sampler, instanceOf(RemoteControlledSampler.class));
  }
}
