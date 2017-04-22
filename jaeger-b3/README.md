# jaeger-b3
These components allow you to join traces with other Zipkin (B3)
instrumented applications (such as Brave or zipkin-go-opentracing).

# Usage
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
