-include jaeger-crossdock/rules.mk

.PHONY: clean
clean:
	./gradlew clean

.PHONY: test
test:
	./gradlew test

.PHONY: release
release:
	./gradlew uploadArchives
