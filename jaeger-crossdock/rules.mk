PROJECT=jaeger-crossdock
XDOCK_YAML=$(PROJECT)/docker-compose.yml

.PHONY: crossdock
crossdock: gradle-compile
	docker-compose -f $(XDOCK_YAML) kill java-udp java-http
	docker-compose -f $(XDOCK_YAML) rm -f java-udp java-http
	docker-compose -f $(XDOCK_YAML) build java-udp java-http
	docker-compose -f $(XDOCK_YAML) run crossdock

.PHONY: crossdock-fresh
crossdock-fresh: gradle-compile
	docker-compose -f $(XDOCK_YAML) down --rmi all
	docker-compose -f $(XDOCK_YAML) run crossdock

gradle-compile:
	./gradlew :jaeger-crossdock:shadowJar

.PHONY: crossdock-logs
crossdock-logs:
	docker-compose -f $(XDOCK_YAML) logs

.PHONY: crossdock-clean
crossdock-clean:
	docker-compose -f $(XDOCK_YAML) down
