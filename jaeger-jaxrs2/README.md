# JAX-RS #

This module provides  general JAX-RS 2.0 instrumentation for a framework such as Jersey 

## Maven Coordinates ##
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-jaxrs2</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

## Instrumentation ##
This project includes instrumentation for everything that implements the JAX-RS 2.0
standard. Implementations such as Jersey and Dropwizard allow you to specify client
and server filters to do some computational work before every request or response. These filters
can then be combined into a JAX-RS [Feature](https://jersey.java.net/apidocs/2.9/jersey/javax/ws/rs/core/Feature.html)

You will need to add a `JaegerFeature` to both the server and all JAX-RS clients that you use.
If you use other clients, like an Apache Http Client, make sure to instrument them as well.

## Example ##
```java
Final ResourceConfig rc = new ResourceConfig()
    /* Arbitrary configuration code… */
    .register(TracingUtils.serverFilter(jaegerConfig));

return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
```
