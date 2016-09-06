PROJECT=jaeger-crossdock
XDOCK_YAML=$(PROJECT)/docker-compose.yml

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
	docker-compose -f $(XDOCK_YAML) build
	docker-compose -f $(XDOCK_YAML) run crossdock

gradle-compile:
	./gradlew clean :jaeger-crossdock:shadowJar

.PHONY: crossdock-logs
crossdock-logs:
	docker-compose -f $(XDOCK_YAML) logs

