-include jaeger-crossdock/rules.mk

GRADLE=GRADLE_OPTS=-Xmx1g ./gradlew

.PHONY: clean
clean:
	$(GRADLE) clean

.PHONY: test
test:
	$(GRADLE) check

.PHONY: test-ci
test-ci:
	$(GRADLE) -is check

.PHONY: release
release: clean
	$(GRADLE) -i uploadArchives
