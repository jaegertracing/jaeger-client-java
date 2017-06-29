# Release Process

## Automatic release (preferred)

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
