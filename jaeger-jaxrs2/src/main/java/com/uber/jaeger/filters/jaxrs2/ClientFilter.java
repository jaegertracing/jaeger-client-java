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

package com.uber.jaeger.filters.jaxrs2;

import com.uber.jaeger.context.TraceContext;
import io.opentracing.Tracer;
import java.io.IOException;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@ConstrainedTo(RuntimeType.CLIENT)
@Slf4j
public class ClientFilter implements ClientRequestFilter, ClientResponseFilter {
  private final ClientSpanCreationFilter spanCreationFilter;
  private final ClientSpanInjectionFilter spanInjectionFilter;

  /**
   * @param tracer tracer
   * @param traceContext trace context
   * @deprecated use {@link ClientFilter(Tracer)}
   */
  @lombok.Generated  // ignore deprecated code in jacoco
  @Deprecated
  public ClientFilter(Tracer tracer, TraceContext traceContext) {
      this(tracer);
  }

  public ClientFilter(Tracer tracer) {
    this.spanCreationFilter = new ClientSpanCreationFilter(tracer);
    this.spanInjectionFilter = new ClientSpanInjectionFilter(tracer);
  }

  @Override
  public void filter(ClientRequestContext clientRequestContext) throws IOException {
    try {
      spanCreationFilter.filter(clientRequestContext);
      spanInjectionFilter.filter(clientRequestContext);
    } catch (Exception e) {
      log.error("Client Filter Request:", e);
    }
  }

  @Override
  public void filter(
      ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext)
      throws IOException {
    spanInjectionFilter.filter(clientRequestContext, clientResponseContext);
  }
}
