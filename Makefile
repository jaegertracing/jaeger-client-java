-include jaeger-java-crossdock/rules.mk

.PHONY: clean
clean:
	./gradlew clean

.PHONY: test
test:
	./gradlew test
