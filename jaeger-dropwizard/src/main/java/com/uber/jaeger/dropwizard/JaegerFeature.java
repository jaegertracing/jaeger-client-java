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
