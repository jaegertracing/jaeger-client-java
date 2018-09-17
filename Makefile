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
release:
	./travis/release.sh

.PHONY: coverage
coverage: SHELL:=/bin/bash
coverage:
	$(GRADLE) codeCoverageReport
	bash <(curl -s https://codecov.io/bash)
