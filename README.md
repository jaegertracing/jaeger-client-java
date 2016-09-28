[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Released Version][maven-img]][maven]

# Jaeger Bindings for Java OpenTracing API

This is a client side library that implements
[Java OpenTracing API](https://github.com/opentracing/opentracing-java),
with Zipkin-compatible data model.

## Modules ##

Click through for more detailed docs on specific modules.

 * [jaeger-dropwizard](./jaeger-dropwizard): a feature to initialize Jaeger from [Dropwizard](http://www.dropwizard.io/) apps (including binding to stats/metrics) 
 * [jaeger-apachehttpclient](./jaeger-apachehttpclient): instrumentation for apache http clients
 * [jaeger-jaxrs2](./jaeger-jaxrs2): instrumentation for JAXRS2 filters
 * [jaeger-core](./jaeger-core): the core implementation of the OpenTracing API
 * [jaeger-context](./jaeger-context): in-process context propagation
 * [jaeger-zipkin](./jaeger-zipkin): compatibility layer for using Jaeger tracer as Zipkin-compatible implementation

## Importing Dependencies ##
All artifacts are published to Maven Central. 
Snapshot artifacts are also published to
[Sonatype](https://oss.sonatype.org/content/repositories/snapshots/com/uber/jaeger/).
Follow these [instructions](http://stackoverflow.com/questions/7715321/how-to-download-snapshot-version-from-maven-snapshot-repository)
to add the snapshot repository to your build system. 

Add the required dependencies to your project. Usually, this would only be a single dependency.
**Please use the latest version:** [![Released Version][maven-img]][maven]

## Common concerns ##

### Thread Pooling: ###
`jaeger-context` defines
[ThreadLocalTraceContext](./jaeger-context/src/main/java/com/uber/jaeger/context)
implementation of `TraceContext` that can be used for propagating the current tracing `Span`
throughout the application without changing the application's code. However, if the application
is starting new threads or is using thread pools, the thread-local context is not going to be
carried over into the execution in the next thread. To maintain context propagation,
a wrapper `TracedExecutorService` is provided that automatically transfers the context
onto the new threads.

```java
ExecutorService instrumentedExecutorService = TracingUtils.tracedExecutor(wrappedExecutorService);
```

### Testing ###

When testing tracing instrumentation it is often useful to make sure
that all spans are being captured, which is not the case in production
configurations where heavy sampling is applied by default.
The following configuration can be provided to affect which sampling
is applied to the new traces:

```yaml
sampler:
   type: const # can either be const, probabilistic, or ratelimiting
   param: 1  # can either be an integer, a double, or an integer
```

The valid values for `type` are: 
 * `const`: configures a sampler that always makes the same decision
    for new traces depending on the `param`: always no for `param=0`,
    always yes otherwise.
 * `probabilistic`: configures a sampler that samples traces with
    probability equal to `param` (must be between `0.0` and `1.0`)
 * `ratelimiting`: configures a samlper that samples traces with a
    certain rate per second equal to `param`

#### Debug Traces (Forced Sampling) ####

##### Programmatically #####

The OpenTracing API defines a `sampling.priority` standard tag that
can be used to affect the sampling of a span and its children:

```java
import io.opentracing.tag.Tags;

Tags.SAMPLING_PRIORITY.set(span, (short) 1);
```

##### Via HTTP Headers #####

Jaeger Tracer also understands a special HTTP Header `jaeger-debug-id`,
which can be set in the incoming request, e.g.

```sh
curl -H "jaeger-debug-id: some-correlation-id" http://myhost.com
```

When Jaeger sees this header in the request that otherwise has no
tracing context, it ensures that the new trace started for this
request will be sampled in the "debug" mode (meaning it should survive
all downsampling that might happen in the collection pipeline), and
the root span will have a tag as if this statement was executed:

```java
span.setTag("jaeger-debug-id", "some-correlation-id")
```

This allows using Jaeger UI to find the trace by this tag.

## Developing ##

 1. `git submodule init update`
 2. `./gradlew googleJavaFormat clean test`
 
### Code Style

This project uses [google java style](https://google.github.io/styleguide/javaguide.html).
It is recommended to set up a git precommit hook as follows.
```
cat>.git/hooks/pre-commit
#!/bin/sh
#
# Pre-commit hooks
# Format code using the google formatter
echo "pre-commit code format"
./gradlew googleJavaFormat
^D
```
```
chmod a+x .git/hooks/pre-commit
```

You can also setup the [IntelliJ plugin](https://plugins.jetbrains.com/plugin/8527)
to reformat code from within your IDE

## License ##
  
  [The MIT License](LICENSE).



  [ci-img]: https://travis-ci.org/uber/jaeger-client-java.svg?branch=master
  [ci]: https://travis-ci.org/uber/jaeger-client-java
  [cov-img]: https://coveralls.io/repos/github/uber/jaeger-client-java/badge.svg?branch=master
  [cov]: https://coveralls.io/github/uber/jaeger-client-java?branch=master
  [maven-img]: https://img.shields.io/maven-central/v/com.uber.jaeger/jaeger-core.svg?maxAge=2000
  [maven]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.uber.jaeger%22
