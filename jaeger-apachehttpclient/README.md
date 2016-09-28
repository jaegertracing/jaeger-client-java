# Apache Http Client
## Maven Dependency
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-apachehttpclient</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

## Instrumentation
This component allows you to instrument your apache http client for tracing. We make use of
Apache Http Clients's [RequestInterceptors](https://hc.apache.org/httpcomponents-core-ga/httpcore/apidocs/org/apache/http/HttpRequestInterceptor.html) 
and [ResponseInterceptors](https://hc.apache.org/httpcomponents-core-ga/httpcore/apidocs/org/apache/http/HttpResponseInterceptor.html) 
to instrument the client. 
On a high level, the RequestInterceptor starts a span and puts it into a HttpContext, and the ResponseInterceptor
requests completes the span and reports it.

To obtain an apache http client that already has the interceptors set, simply use `TracingInterceptors`

## Example
```java
Tracer tracer = jaegerConfig.getTracer();
HttpClientBuilder clientBuilder = HttpClients.custom();
CloseableHttpClient client = TracingInterceptors.addTo(clientBuilder, tracer).build();
```
