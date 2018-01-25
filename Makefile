-include jaeger-crossdock/rules.mk

.PHONY: clean
clean:
	./gradlew clean

.PHONY: test
test:
	./gradlew -is check

.PHONY: release
release: clean
	./gradlew -i uploadArchives
