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

The classes `TracingRequestInterceptor` and `TracingResponseInterceptor`
allow subclassing as a way of adding custom behavior to the tracing spans,
such as setting non-standard tags or writing logs:

```java
public class CustomRequestInterceptor extends TracingRequestInterceptor {

  /**
   * When the client Span is started, record a custom tag and log the
   * request content.
   */
  protected void onSpanStarted(Span clientSpan, HttpRequest httpRequest, HttpContext httpContext) {
    clientSpan.setTag("some-tag", "some-value");
    clientSpan.log("request.json", asJSON(httpRequest));
  }

  /**
   * If `operation-name` HTTP header is set by the caller, use it as the
   * Span's operation name. Otherwise fall back to default behavior.
   * 
   * @param httpRequest the request for the http operation being executed
   * @return the operation name
   */
  protected String getOperationName(HttpRequest httpRequest) {
    Header opNameHeader = httpRequest.getFirstHeader("operation-name");
    if (opNameHeader != null) {
      return opNameHeader.getValue();
    }
    return super.getOperationName(httpRequest);
  }
}

...

public class CustomResponseInterceptor extends TracingRequestInterceptor {

  /**
   * Before span is finished, log the response as JSON.
   */
  protected void beforeSpanFinish(Span clientSpan, HttpResponse httpResponse, HttpContext httpContext) {
    clientSpan.log("request.json", asJSON(httpResponse));
  }
}

HttpClientBuilder builder = ...;
builder.addInterceptorFirst(new CustomRequestInterceptor(tracer))
       .addInterceptorFirst(new CustomResponseInterceptor());
```

