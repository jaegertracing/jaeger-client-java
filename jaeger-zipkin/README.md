# jaeger-zipkin
These components make Jaeger compatible with Zipkin instrumentation and transports.

For example, you can join traces with other Zipkin (B3) instrumented applications (such as Brave), and send spans to
Zipkin on standard transports such as Http or Kafka.

# Usage
To make Jaeger Zipkin-compatible, you need to register injectors and extractors, and configure a remote reporter.

## B3 Propagation
Zipkin systems most often use B3 propagation. This includes setting and reading headers like "X-B3-TraceId". Once you
register `B3TextMapCodec`, Jaeger can join traces started by other Zipkin instrumented applications.

For example:
```java
b3Codec = new B3TextMapCodec();
tracer = new JaegerTracer.Builder(serviceName)
  .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
  .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
  ...
```

## Sending data to Zipkin
There are two ways to send spans to a Zipkin server:

### Thrift
If you want to send Zipkin v1 Thrift-encoded spans, you should use the `ZipkinSender` sender, which
wraps a Zipkin sender class to enable the use of various transports such as HTTP and Kafka.

For example:
```java
import io.jaegertracing.zipkin.ZipkinSender;

reporter = new RemoteReporter.Builder()
  .withSender(ZipkinSender.create("http://localhost:9411/api/v1/spans"))
  .build();

tracer = new JaegerTracer.Builder(serviceName)
  .withReporter(reporter)
  .build()
```

### Zipkin 2 Reporters
You can reuse a Zipkin 2 reporter instance as-is by using `ZipkinV2Reporter`, which adapts a Zipkin
2 reporter to the Jaeger reporter interface and deals with converting Jaeger spans to the Zipkin 2 
model.

The following example requires the artifact `io.zipkin.reporter2:zipkin-sender-urlconnection` and shows
how to accomplish that:
```java
import io.jaegertracing.zipkin.ZipkinV2Reporter;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

reporter = new ZipkinV2Reporter(AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v2/spans")));

tracer = new JaegerTracer.Builder(serviceName)
  .withReporter(reporter)
  .build()
```

This will send spans to the Zipkin v2 endpoint using the v2 JSON encoding.
