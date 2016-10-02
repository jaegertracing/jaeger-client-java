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

## Custom extensions
First, declare the interceptors in the http client builder
```java
HttpClientBuilder builder;
builder.addInterceptorFirst(new CustomRequestInterceptor(tracer))
       .addInterceptorFirst(new CustomResponseInterceptor());
```

Then customize the request or the response interceptor

```java
public class CustomRequestInterceptor extends TracingRequestInterceptor {

protected void onSpanStarted(Span clientSpan, HttpRequest httpRequest, HttpContext httpContext) {
    clientSpan.setTag(DEBUG_ID_HEADER_KEY, "debug-me");
    Tags.SAMPLING_PRIORITY.set(clientSpan, (short) 1);
    ...
  }

  /**
   * Get the http operation name to log into jaeger. Defaults to the HTTP verb
   * @param httpRequest the request for the http operation being executed
   * @return the operation name
   */
  protected String getOperationName(HttpRequest httpRequest) {
    ...
    return httpRequest.getFirstHeader("operation-name").getValue();
  }
}

...

public class CustomResponseInterceptor extends TracingRequestInterceptor {

  protected void beforeSpanFinish(Span clientSpan, HttpResponse httpResponse, HttpContext httpContext) {
    Tags.COMPONENT.set(clientSpan, httpResponse.getFirstHeader("component").getValue());
    ...
  }
}

```