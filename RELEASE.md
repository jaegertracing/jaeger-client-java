# Release Process

## Automatic release (preferred)

The release process consists of these steps:
  1. Create a pull request with:
     * Change the version in [`build.gradle`](build.gradle) to match the release version e.g. `0.20.0`
     * Add an entry to [`CHANGELOG`](CHANGELOG.rst) with changes since the last release
  1. Commit your `CHANGELOG` changes
  1. Create and push tag with the new version `git tag v0.20.0 && git push origin v0.20.0`
  1. Once the *tag* build finishes in Travis, the artifacts should have been uploaded to Sonatype staging,
     the staging repository closed, and the artifacts on the way to Maven Central (it takes 20min+ to get there).
     In case of failures it is safe to retry by restarting the CROSSDOCK step of the build. If it keeps
     failing, sometimes it may be necessary to close the staging repository manually.
  1. Create a release on GitHub for the new tag. Use the changes from the `CHANGELOG` as the description.
  1. Once the artifacts are available on Maven
     * bump the version in `build.gradle` to `major.minor.(patch+1)-SNAPSHOT`
     * add a new section to the CHANGELOG with that version as `(unreleased)` and a bullet point `- Nothing yet`
     * commit with the comment "Back to development" (for example, https://github.com/jaegertracing/jaeger-client-java/commit/da9726d3ba7309947882e3c621516c70b4bc83dc)

## Local setup and release

The Travis build execute the `uploadArchives` task, and this task performs the signing of the artifacts.
While the Travis build is ready for that, releasing locally requires the following steps:

 * install GPG: `brew install gpg`
 * generate keys: `gpg --gen-key`
 * see installed keys with `gpg --list-keys` or `gpg --list-secret-keys`
 * create an account with Sonatype, get repo permissions (e.g. https://issues.sonatype.org/browse/OSSRH-23572)
 * create `$HOME/.gradle/gradle.properties` file
```
    signing.keyId={key ID from gpg --list-secret-keys}
    signing.password={password you used to encrypt keys via gpg --gen-key}
    signing.secretKeyRingFile={e.g. [home path]/.gnupg/secring.gpg}

    ossrhUsername={your user name at Sonatype}
    ossrhPassword={your password at Sonatype}
```
 * upload your public key to Ubuntu servers, e.g.
    * `gpg --keyserver http://keyserver.ubuntu.com:11371 --send-keys {pub key ID}`
    * you can also use Web UI and upload plain test key that you can obtain via
      * `gpg --armor --export {your email used for the keys}`

Run the following command to upload to Nexus. Note that it might be necessary to close and release via Nexus UI.
```bash
./gradlew upload -Psigning.keyId=<id> -Psigning.password=<pass> -Psigning.secretKeyRingFile=<home/user>/.gnupg/secring.gpg -PossrhUsername=<name> -PossrhPassword=<pas>
```

## Closing Staging Repository Manually

 * https://oss.sonatype.org/, log in, go to Staging Repositories
 * In the top-right corner search box type `uber`, and find your uploaded repository
 * Hit Close button. Monitor status on Activity tab at the bottom, hitting Refresh.
 * Once Close is successful, the Release button will become available, so hit it
 * Keep hitting Refresh while sync to Maven is in progress. Once it's complete, the repository will disappear.

