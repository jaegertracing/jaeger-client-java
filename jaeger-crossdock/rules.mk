PROJECT=jaeger-crossdock
XDOCK_YAML=$(PROJECT)/docker-compose.yml
COMMIT=$(shell git log --pretty=format:'%H' -n 1)

.PHONY: crossdock
crossdock: gradle-compile
	docker-compose -f $(XDOCK_YAML) kill java
	docker-compose -f $(XDOCK_YAML) rm -f java
	docker-compose -f $(XDOCK_YAML) build java
	docker-compose -f $(XDOCK_YAML) run crossdock

.PHONY: crossdock-fresh
crossdock-fresh: gradle-compile
	docker-compose -f $(XDOCK_YAML) kill
	docker-compose -f $(XDOCK_YAML) rm --force
	docker-compose -f $(XDOCK_YAML) pull
	docker-compose -f $(XDOCK_YAML) build --no-cache
	docker-compose -f $(XDOCK_YAML) run crossdock

upload-image: gradle-compile
	cd ./jaeger-crossdock && docker build -f Dockerfile -t jaegertracing/xdock-java:$(COMMIT) .
	docker push jaegertracing/xdock-java


gradle-compile:
	./gradlew :jaeger-crossdock:shadowJar

.PHONY: crossdock-logs
crossdock-logs:
	docker-compose -f $(XDOCK_YAML) logs

