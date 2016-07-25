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
