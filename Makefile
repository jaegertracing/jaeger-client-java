-include jaeger-java-crossdock/rules.mk

.PHONY: clean
clean:
	./gradlew clean
	rm -rf jaeger-core/src/main/gen-java


.PHONY: compile-thrift
compile-thrift:
	PATH=$(PWD)/travis/docker-thrift:$$PATH ./gradlew :jaeger-core:compileThrift :jaeger-core:licenseFormatMain

.PHONY: test
test:
	./gradlew test
