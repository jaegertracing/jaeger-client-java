# Release Process

This project uses [Shipkit](http://shipkit.org) for most of the release management. A release can be performed either manually or via Travis.

## Automated

Assuming we have a `version.properties` with `0.31.1` as the next version, this is what's to be done:

1. Update the `CHANGELOG.md` with the changes for `0.31.1`. Most of the changes should have been added there already, but at least the release date has to be set
1. Get the change above merged
1. Tag the release build, like, `git tag release/v0.31.1` and push the tag to the main repository (`git push upstream release/v0.31.1`, if your `jaegertracing/jaeger-client-java` remote is called `upstream`)

Once these steps are done, here's what happens:
* Travis will trigger a `make release`
* Our `travis/release.sh` is called, possibly updating the `version.properties` to match the release tag `0.31.1`
* The Shipkit Gradle task `ciPerformRelease` is called, which builds, tags the version as `v0.31.1` and uploads the release `0.31.1` to Bintray
* Bintray publishes the artifacts to Maven Central in a few minutes

Not automated yet:
* [GitHub release](https://github.com/jaegertracing/jaeger-client-java/releases)

## Manual

To do a manual release, you'll first need a few environment variables:

* `GH_READ_TOKEN`
* `GH_WRITE_TOKEN` (with `repo:public_repo` permission only)
* `BINTRAY_API_KEY`
* `BINTRAY_API_USER`

Those are self-explanatory, but check the [Shipkit Getting Started](https://github.com/mockito/shipkit/blob/master/docs/getting-started.md) for more information on how to obtain those. 

Do everything like the "Automated" session but instead of pushing the tag to the remote repository, run:

```bash
make release
```

