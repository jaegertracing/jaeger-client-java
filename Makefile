-include jaeger-crossdock/rules.mk

GRADLE=GRADLE_OPTS=-Xmx1g ./gradlew

.PHONY: clean
clean:
	$(GRADLE) clean

.PHONY: test
test:
	$(GRADLE) check

.PHONY: test-travis
test-travis:
	$(GRADLE) -is check

.PHONY: release
release: clean
	$(GRADLE) -i uploadArchives
