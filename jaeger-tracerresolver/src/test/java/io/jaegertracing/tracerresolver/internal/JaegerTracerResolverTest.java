/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package io.jaegertracing.tracerresolver.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerFactory;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import java.util.ServiceLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JaegerTracerResolverTest {

  @Before
  @After
  public void clearProperties() {
    // Explicitly clear all TracerResolver properties
    System.clearProperty(Configuration.JAEGER_SERVICE_NAME);
  }

  @Test
  public void testResolveTracerNoServiceName() {
    assertNull(TracerResolver.resolveTracer());
  }

  @Test
  public void testResolveTracerDefault() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "MyService");
    Tracer tracer = TracerResolver.resolveTracer();
    assertNotNull(tracer);
    assertTrue(tracer instanceof JaegerTracer);
  }

  @Test
  public void canFindTracerFactory() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "canFindTracerFactory");
    Tracer tracer = ServiceLoader.load(TracerFactory.class).iterator().next().getTracer();
    assertNotNull(tracer);
    assertTrue(tracer instanceof JaegerTracer);
  }

  @Test
  public void canFindTracerResolver() {
    System.setProperty(Configuration.JAEGER_SERVICE_NAME, "canFindTracerResolver");
    TracerResolver tracerResolver = ServiceLoader.load(TracerResolver.class).iterator().next();
    assertNotNull(tracerResolver);
    assertTrue(tracerResolver instanceof JaegerTracerResolver);

    JaegerTracer tracer = ((JaegerTracerResolver) tracerResolver).resolve();
    assertNotNull(tracer);
  }

}
