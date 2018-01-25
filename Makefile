-include jaeger-crossdock/rules.mk

.PHONY: clean
clean:
	./gradlew clean

.PHONY: test
test:
	./gradlew check

.PHONY: test-travis
test-travis:
	./gradlew -is check --debug

.PHONY: release
release: clean
	./gradlew -i uploadArchives
