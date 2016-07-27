#!/bin/sh
set -e
set -x

pushd 'jaeger-java-crossdock'

export REPO=jaegertracing/xdock-java
export PR=https://api.github.com/repos/$TRAVIS_REPO_SLUG/pulls/$TRAVIS_PULL_REQUEST
export BRANCH=$(if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then echo $TRAVIS_BRANCH; else echo `curl -s $PR | jq -r .head.ref`; fi)
export TAG=`if [ "$BRANCH" == "master" ]; then echo "latest"; else echo $BRANCH; fi`
echo "TRAVIS_BRANCH=$TRAVIS_BRANCH, REPO=$REPO, PR=$PR, BRANCH=$BRANCH, TAG=$TAG"
docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
docker build -f Dockerfile -t $REPO:$COMMIT .
docker tag $REPO:$COMMIT $REPO:$TAG
docker tag $REPO:$COMMIT $REPO:travis-$TRAVIS_BUILD_NUMBER
docker push $REPO

popd
