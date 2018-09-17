#!/bin/bash

git diff -s --exit-code
if [[ $? != 0 ]]; then
    echo "The repository isn't clean. We won't proceed, as we don't know if we should commit those changes or not."
    exit 1
fi

## this returns 'version=0.31.0', will be cleaned up afterwards
VERSION_FROM_PROPERTIES=$(grep "^version" version.properties)
VERSION_FROM_GIT=$(git describe --tags --exact-match 2>/dev/null)

## highest precedence is when the VERSION env var is set directly to this script
## second is whatever TRAVIS_BRANCH says it is, which should be something like 'release-v0.31.0'
## third in the list is the tag for the head, as per VERSION_FROM_GIT
## when nothing is specified, gets the one from the properties
VERSION=${VERSION:-${TRAVIS_BRANCH:-${VERSION_FROM_GIT:-${VERSION_FROM_PROPERTIES}}}}

# cleanup, leaving only the version part itself (0.31.0)
VERSION=$(echo ${VERSION} | grep -Po "([\d\.]+)")

echo "Releasing ${VERSION}"

## replace the version in the version.properties
sed "s/^version\=.*/version=${VERSION}/gi" -i version.properties

git diff -s --exit-code
if [[ $? == 0 ]]; then
    echo "We are releasing the version that is specified in the version.properties, no need to change anything."
else
    git add version.properties
    git commit -qm "Release ${VERSION}" --author="Jaeger Release <jaeger-release@jaegertracing.io>"
fi

GRADLE_OPTS=-Xmx1g ./gradlew -i ciPerformRelease