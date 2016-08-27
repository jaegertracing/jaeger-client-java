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
tracer = new Tracer.Builder(serviceName, reporter, sampler)
                   .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
                   .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
                   ...
```

## Sending data to Zipkin
Zipkin supports transports including Http and Kafka. You can configure Jaeger to send to a Zipkin server with
`ZipkinSender`.

For example:
```java
reporter = new RemoteReporter(ZipkinSender.create("http://localhost:9411/api/v1/spans"));
tracer = new Tracer.Builder(serviceName, reporter, sampler)
                   ...
```
