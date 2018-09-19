# Release Process

This project uses [Shipkit](http://shipkit.org) for most of the release management. A release can be performed either manually or via Travis.

## Automated

1. Update the `CHANGELOG.md` with the changes for the last release. Most of the changes should have been added there already, but at least the release date has to be set.
1. Get the change above merged
1. Tag the release, like, `git tag release/v0.31.1` and push the tag to the main repository (`git push upstream v0.31.0`, if your `jaegertracing/jaeger-client-java` remote is called `upstream`)

Once these steps are done, Travis will trigger a `make release`, our `travis/release.sh` will update the `version.properties` used by Shipkit, which then builds and uploads the release to Bintray. The artifacts then end up in Maven Central in a few minutes.

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

