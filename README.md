[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov]

# Jaeger Bindings for Java OpenTracing API

This is a client side library that implements
[Java OpenTracing API](https://github.com/opentracing/opentracing-java),
with Zipkin-compatible data model.

**This project is currently WIP and not ready for use. Do not use it until this notice goes away.**

## Developing

 1. `git submodule init update`
 2. `./gradlew clean test`


  [ci-img]: https://travis-ci.org/uber/jaeger-client-java.svg?branch=master
  [cov-img]: https://coveralls.io/repos/github/uber/jaeger-client-java/badge.svg?branch=master
  [ci]: https://travis-ci.org/uber/jaeger-client-java
  [cov]: https://coveralls.io/github/uber/jaeger-client-java?branch=master

# Jaeger Instrumentation #

## Dependencies ##
Add only one of the following dependencies to your pom.xml file.

### Dropwizard ###
If you are using dropwizard then you can add the following:
```
        <dependency>
            <groupId>com.uber.jaeger</groupId>
            <artifactId>jaeger-dropwizard</artifactId>
            <version>0.5.0</version>
        </dependency>
```

### JAX-RS ###

If you just want general JAX-RS 2.0 instrumentation for a framework such as Jersey then add the following:
```
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-jaxrs2</artifactId>
    <version>0.5.0</version>
</dependency>
```

### Core ###

If you only want to do custom instrumentation using the core tracing functionality then add the following:
```
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-core</artifactId>
    <version>0.5.0</version>
</dependency>
```

## Configuration: ##

The minimal configuration in base.yaml should look like this:
```
jaeger:
  serviceName: geocache
```
In a yaml configuration file you must specify a `serviceName`.  It is also possible to specify a ‘disable’ flag set to true, or false.  Jaeger should ALWAYS be ENABLED except in case of emergencies where you are sure there is a problem with Jaeger.  If the `disable` field is left out then Jaeger will be enabled by default.


## JAX-RS Instrumentation: ##

As of right now we have provided instrumentation for everything that follows the JAX-RS 2.0 standard.  JAX-RS based implementations such as Jersey and Dropwizard allow you to specify client and server filters to do some computational work before every request or response.  Thus to add Jaeger to your service you just need to have a server filter set on your server, and a client filter set on you outbound client requests.  Note it is important for all Java services to have a central place to configure Clients, for outgoing requests, as well as a central place for servers.

### Dropwizard: ###

Jaeger’s dropwizard jar exposes a configuration object `com.uber.jaeger.dropwizard.Configuration`  If your service reads in its configuration through a pojo object then you can use this Configuration class to read the yaml format specified above.
The following is an example of adding a client filter to a JAX-RS based framework that creates a dropwizard/jersey client.

Client instrumentation example:
```
import com.uber.jaeger.dropwizard.TracingUtils;

// JaegerConfig gets initialized by whichever dropwizard configuration reader you are using.
com.uber.jaeger.dropwizard.Configuration jaegerConfig;

final Client client = new JerseyClientBuilder(env)
    /* Arbitrary configuration code … */
    .register(TracingUtils.clientFilter(jaegerConfig);
```

Your server configuration will probably not look exactly like the one below.  However JAX-RS server configurations always expose a `register` function that can be used to register a server filter like below.
Server instrumentation example:
```
Final ResourceConfig rc = new ResourceConfig()
    /* Arbitrary configuration code… */
    .register(TracingUtils.serverFilter(jaegerConfig));

return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
```

### Metrics ###
In order for Jaeger to set metrics properly it needs to be configured with a metric registry.  
```
import com.uber.jaeger.dropwizard.TracingUtils;

// Dropwizard's metrics registry class
MetricsRegistry registry = new MetricsRegistry();

// JaegerConfig gets initialized by whichever dropwizard configuration reader you are using.
com.uber.jaeger.dropwizard.Configuration jaegerConfig;

jaegerConfig.setMetricRegistry(registry);

final Client client = new JerseyClientBuilder(env)
    /* Arbitrary configuration code … */
    .register(TracingUtils.clientFilter(jaegerConfig);
```

## Thread Pooling: ##
Because Jaeger uses thread local storage in order to propagate information without changing your code.  The creation of new thread, or the reuse of threads needs to be wrapped in our instrumented tools in order to preserve the structure of a Trace in the UI.

An executor service needs to be wrapped in our TracedExecutorService. 

Executor Service
```
ExecutorService instrumentedExecutorService = TracingUtils.tracedExecutor(wrappedExecutorService);
```

## Testing ##
At times you may need to force spans to be sampled in order to test your instrumentation, or diagnose a problem in production.  The following format allows you to specify the sampler to use:

```
sampler:
   type: const # can either be const, probabilistic, or ratelimiting
   param: 1  # can either be an integer, a double, or an integer in the same order as above.
```

The valid values for `type` are: const, probabilistic, and ratelimiting.  The valid respective values for `param` are an integer which can be 1 to sample all spans, or not 1 to sample no spans, a double between 0.0 and 1.0 inclusive that specifies the percentage of spans to accept, and an integer that specifies the number of spans to accept per second (this is good for low traffic services.)
