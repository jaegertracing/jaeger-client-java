/*
 * Copyright (c) 2016, Uber Technologies, Inc
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

package com.uber.jaeger.dropwizard;

import com.uber.jaeger.filters.jaxrs2.TracingUtils;
import io.opentracing.Tracer;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

public class JaegerFeature implements Feature {
  private final Tracer tracer;

  public JaegerFeature(Configuration jaegerConfig) {
    this.tracer = jaegerConfig.getTracer();
  }

  @Override
  public boolean configure(FeatureContext featureContext) {
    if (featureContext.getConfiguration().isEnabled(this.getClass())) {
      return false;
    }

    switch (featureContext.getConfiguration().getRuntimeType()) {
      case SERVER:
        featureContext.register(
            new JerseyServerFilter(tracer, com.uber.jaeger.context.TracingUtils.getTraceContext()));
        break;
      case CLIENT:
      default:
        featureContext.register(TracingUtils.clientFilter(tracer));
        break;
    }

    return true;
  }
}
