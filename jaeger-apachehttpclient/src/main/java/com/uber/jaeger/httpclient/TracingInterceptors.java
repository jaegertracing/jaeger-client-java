package com.uber.jaeger.httpclient;

import com.uber.jaeger.Tracer;
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
