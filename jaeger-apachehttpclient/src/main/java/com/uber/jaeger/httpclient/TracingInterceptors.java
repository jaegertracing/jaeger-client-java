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
package com.uber.jaeger.httpclient;

import io.opentracing.Tracer;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

/**
 * Returns tracing enabled client builders for apache http clients.
 */
public class TracingInterceptors {
  public static HttpAsyncClientBuilder addTo(HttpAsyncClientBuilder clientBuilder, Tracer tracer) {
    return clientBuilder
        .addInterceptorFirst(new TracingRequestInterceptor(tracer))
        .addInterceptorFirst(new TracingResponseInterceptor());
  }

  public static HttpClientBuilder addTo(HttpClientBuilder clientBuilder, Tracer tracer) {
    return clientBuilder
        .addInterceptorFirst(new TracingRequestInterceptor(tracer))
        .addInterceptorFirst(new TracingResponseInterceptor());
  }
}
