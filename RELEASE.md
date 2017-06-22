# Release Process

The release process consists of these steps:
  1. Add an entry to [`CHANGELOG.md`](CHANGELOG.rst) with description of changes in the new release
  1. Commit your `CHANGELOG` changes
  1. Tag as a version (`git tag v0.20.0`). Use the one from [`build.gradle`](build.gradle) as reference.
  1. Push the tag (`git push v0.20.0`)
  1. Once the *tag* build finishes in Travis, the artifacts should have been uploaded to Sonatype staging. 
  At this stage, you'll need to close and release the staging repository. After that, the artifacts should 
  soon reach Maven Central.
     * https://oss.sonatype.org/, log in, go to Staging Repositories
     * In the top-right corner search box type `uber`, and find your uploaded repository
     * Hit Close button. Monitor status on Activity tab at the bottom, hitting Refresh.
     * Once Close is successful, the Release button will become available, so hit it
     * Keep hitting Refresh while sync to Maven is in progress. Once it's complete, the repository will disappear.
  1. Bump the version in `build.gradle` to the next release and commit it
