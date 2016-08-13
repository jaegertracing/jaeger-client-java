-include jaeger-java-crossdock/rules.mk

.PHONY: clean
clean:
	./gradlew clean

.PHONY: test
test:
	PATH=$(PWD)/travis/docker-thrift:$$PATH ./gradlew test
