#!/bin/bash

set -e

docker version

# Install docker-compose
sudo rm -f /usr/local/bin/docker-compose
curl -L https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-`uname -s`-`uname -m` > docker-compose
chmod +x docker-compose
sudo mv docker-compose /usr/local/bin
docker-compose version

sudo rm -rf /usr/local/bin/thrift
sudo cp travis/docker-thrift/thrift /usr/local/bin/
