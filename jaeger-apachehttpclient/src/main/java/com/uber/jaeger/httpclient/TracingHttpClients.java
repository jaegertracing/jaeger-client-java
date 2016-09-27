package com.uber.jaeger.httpclient;

import com.uber.jaeger.Tracer;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;

/**
 * Returns tracing enabled client builders for apache http clients.
 */
public class TracingHttpClients {
  public static HttpAsyncClientBuilder asyncBuilder(Tracer tracer) {
    return HttpAsyncClients.custom()
        .addInterceptorFirst(new TracingRequestInterceptor(tracer))
        .addInterceptorFirst(new TracingResponseInterceptor());
  }

  public static HttpClientBuilder builder(Tracer tracer) {
    return HttpClients.custom()
        .addInterceptorFirst(new TracingRequestInterceptor(tracer))
        .addInterceptorFirst(new TracingResponseInterceptor());
  }
}
