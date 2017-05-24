# Dockerized permutations testing for Jaeger

This repository implments docker testing for jaeger-client-java.  To be more specific Jaeger 
uses a frame work called [Crossdock](https://github.com/crossdock/crossdock) for testing
propagation between Jaeger clients in different languages.

# Usage #

## Run Crossdock tests ##

In order to run crossdock tests running `make crossdock-fresh` in the root directory of this
project is sufficient.

# Contributors #

To push a new crossdock image run `make upload-image`.
