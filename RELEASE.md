# Release Process

## Automatic release (preferred)

The release process consists of these steps:
  1. Create a pull request with:
     * Add an entry to [`CHANGELOG`](CHANGELOG.md) with changes since the last release
     * add a new section to the CHANGELOG with that version as `(unreleased)` and a bullet point `- Nothing yet`
  1. Commit your `CHANGELOG` changes as `git commit -m "Preparing release 0.20.0" -s`
  1. Create and push tag with the new version `git tag release-0.20.0 && git push origin release-0.20.0`. 
  Do not update the version in `gradle.properties` manually.
  1. The build for the `release-` tag does following:
     * It updates the version in `gradle.properties` and creates commit and tag `v0.20.0`
     * It updates version to the next `SNAPSHOT` and commits it
     * The next build for the tag publishes artifact to Nexus. If the build fails it might 
     be necessary to drop staging repositories and restart the build or just close the repository.
  1. Create a release on GitHub for the new tag `v0.20.0`. Use the changes from the `CHANGELOG` as the description.


Maintenance branches should follow naming convention: `release-major.minor` (e.g.`release-0.20`).

## Local setup and release

The Travis build execute the `uploadArchives` task, and this task performs the signing of the artifacts.
While the Travis build is ready for that, releasing locally requires the following steps:

 * install GPG: `brew install gpg`
 * generate keys: `gpg --gen-key`
 * see installed keys with `gpg --list-keys` or `gpg --list-secret-keys`
 * create an account with Sonatype, get repo permissions (e.g. https://issues.sonatype.org/browse/OSSRH-23572)
 * create `$HOME/.gradle/gradle.properties` file or add parameters directly as it is shown at the last step.
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
 * Run gradle upload to Nexus. Note that it might be necessary to close and release via Nexus UI.
    * `./gradlew upload -Psigning.keyId=<key ID from gpg --list-secret-keys> -Psigning.password=<password you used to encrypt keys via gpg --gen-key> -Psigning.secretKeyRingFile=<home/user>/.gnupg/secring.gpg -PossrhUsername=<name> -PossrhPassword=<pas>`

## Closing Staging Repository Manually

 * https://oss.sonatype.org/, log in, go to Staging Repositories
 * In the top-right corner search box type `uber`, and find your uploaded repository
 * Hit Close button. Monitor status on Activity tab at the bottom, hitting Refresh.
 * Once Close is successful, the Release button will become available, so hit it
 * Keep hitting Refresh while sync to Maven is in progress. Once it's complete, the repository will disappear.

