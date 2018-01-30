-include jaeger-crossdock/rules.mk

.PHONY: clean
clean:
	./gradlew clean

.PHONY: test
test:
	./gradlew -is check

.PHONY: test-travis
test-travis:
	./gradlew -is check --info

.PHONY: release
release: clean
	./gradlew -i uploadArchives
