-include jaeger-crossdock/rules.mk

.PHONY: clean
clean:
	./gradlew clean

.PHONY: test
test:
	./gradlew check

.PHONY: release
release: clean
	./gradlew uploadArchives
