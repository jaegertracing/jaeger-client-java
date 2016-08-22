[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov]

# Jaeger Bindings for Java OpenTracing API

This is a client side library that implements
[Java OpenTracing API](https://github.com/opentracing/opentracing-java),
with Zipkin-compatible data model.

**This project is currently WIP and not ready for use. Do not use it until this notice goes away.**

## Developing

 1. `git submodule init update`
 2. `./gradlew clean test`

## Releasing

TODO: the release process is too manual and needs to be automated

The release process consists of these steps:
 1. Edit build.gradle and remove the `-SNAPSHOT` suffix from the `version` parameter
 1. Add an entry to CHANGELOG.pm with description of changes in the new release
 1. Run `./gradlew uploadArchives`
 1. Bump the version in `build.gradle` to the next release and add `-SNAPSHOT suffix
 1. Once the artifacts are uploaded to Sonatype staging, release them to Maven Central
   * TODO: need more details, UI is confusing

The `uploadArchives` step performs signing of the artifacts. For that to work the script
requires private/public keys.

 * install GPG: `brew install gpg`
 * generate keys: `gpg --gen-key`
 * see installed keys with `gpg --list-keys` or `gpg --list-secret-keys`
 * create an account with Sonatype, get repo permissions (e.g. https://issues.sonatype.org/browse/OSSRH-23572)
 * create `$HOME/.gradle/gradle.properties` file
    signing.keyId={key ID from gpg --list-secret-keys}
    signing.password={password you used to encrypt keys via gpg --gen-key}
    signing.secretKeyRingFile={e.g. [home path]/.gnupg/secring.gpg}

    ossrhUsername={your user name at Sonatype}
    ossrhPassword={your password at Sonatype}
 * upload your public key to Ubuntu servers, e.g.
    * `gpg --keyserver http://keyserver.ubuntu.com:11371 --send-keys {pub key ID}`
    * you can also use Web UI and upload plain test key that you can obtain via
      * `gpg --armor --export {your email used for the keys}`


  [ci-img]: https://travis-ci.org/uber/jaeger-client-java.svg?branch=master
  [cov-img]: https://coveralls.io/repos/github/uber/jaeger-client-java/badge.svg?branch=master
  [ci]: https://travis-ci.org/uber/jaeger-client-java
  [cov]: https://coveralls.io/github/uber/jaeger-client-java?branch=master
