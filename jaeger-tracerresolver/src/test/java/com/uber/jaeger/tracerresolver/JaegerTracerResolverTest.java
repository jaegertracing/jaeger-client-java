/*
 * Copyright (c) 2017, Uber Technologies, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.uber.jaeger.tracerresolver;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.Configuration;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;

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
    assertTrue(tracer instanceof com.uber.jaeger.Tracer);
  }

}
