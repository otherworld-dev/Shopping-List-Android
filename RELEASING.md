# Releasing

Shopping List for Nextcloud ships to **Google Play** and **F-Droid** from the
same git tag, so both stores stay on the same `versionCode`.

> **Golden rule: one git tag = one release, shipped to both stores at the same
> `versionCode`.**

Play permanently forbids reusing a `versionCode`; F-Droid builds whatever
`versionCode` is in the tagged `build.gradle.kts`. Keep those equal and the two
stores never drift apart.

## Prerequisites

- A JDK 17+. On Windows with no `java` on PATH, Android Studio's bundled JBR
  works:
  ```
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  ```
- `keystore.properties` + `upload-keystore.jks` at the repo root (both
  gitignored) for signing. Without them the release build is unsigned — fine for
  F-Droid (they sign with their own key) but not for a Play upload.
- For the automatic Play upload, four secrets on the GitHub repo, read by
  `.github/workflows/play-release.yml`:

  | Secret | Holds |
  |---|---|
  | `UPLOAD_KEYSTORE_BASE64` | `upload-keystore.jks`, base64 encoded |
  | `UPLOAD_KEYSTORE_PASSWORD` | the keystore's `storePassword` |
  | `UPLOAD_KEY_PASSWORD` | the `upload` key's `keyPassword` |
  | `PLAY_SERVICE_ACCOUNT_JSON` | the JSON key of a Google Cloud service account |

  The service account needs the Google Play Android Developer API enabled in its
  Cloud project, and an invite in Play Console → *Users and permissions* with
  release rights on this app. The upload key is only the upload key (Google holds
  the app signing key), so it can be reset in Play Console if it ever leaks.

## Translations

The app is translated on [Crowdin](https://crowdin.com/project/shopping-list-for-nextcloud),
in the same project as the web app. Crowdin can only watch one repo, so it works on
a copy of `strings.xml` in the web app's repo (`android/`), and
`scripts/sync-translations.sh` moves the text between the two. It expects the web
repo next to this one, or set `WEB_REPO`.

- After changing any text: `scripts/sync-translations.sh push`, then commit the copied
  file in the web repo so Crowdin sees the new English.
- Before a release: merge Crowdin's latest pull request in the web repo, then
  `scripts/sync-translations.sh pull` and commit the `values-*` folders here.

A partly translated language is fine to ship, anything missing shows in English.

## Steps

1. **Bump the version** in `app/build.gradle.kts`:
   - `versionCode` → next integer, higher than any `versionCode` ever pushed to
     Play (Play burns each one permanently).
   - `versionName` → the marketing version, e.g. `0.5.0`.

2. **Write the changelog**:
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` — the filename
   is the `versionCode` (e.g. `5.txt`). User-facing, ≤500 chars (Play's "What's
   new" limit). Both Play and F-Droid read this.

3. **Build & check**:
   ```
   ./gradlew :app:testDebugUnitTest :app:lintDebug
   ./gradlew :app:assembleRelease   # runs lintVitalRelease + R8 shrinking
   ```
   R8 is enabled for release builds. If R8 fails on missing classes, add the
   `-dontwarn` lines it prints (`app/build/outputs/mapping/release/missing_rules.txt`)
   to `app/proguard-rules.pro`, then rebuild.

4. **Commit** the version bump + changelog.

5. **Tag and push** — this is the release trigger for F-Droid:
   ```
   git tag -a vX.Y.Z -m "vX.Y.Z"
   git push origin vX.Y.Z
   git push github vX.Y.Z
   ```

6. **Play**: pushing the tag to GitHub runs the *Play release* workflow. It
   checks the tag matches `versionName`, runs the tests, builds and signs the
   bundle, and uploads it with the changelog as "What's new". The track it
   releases to is `TRACK` at the top of the workflow. Watch it with
   `gh run watch` or on the repo's Actions tab, and check the signing
   certificate it prints starts `4C:ED:9F:40`.

   To check the secrets without releasing anything, run the workflow by hand
   (Actions → *Play release* → *Run workflow*) with *dry run* ticked. It builds
   and signs the bundle and checks the Play credentials, from any branch.

7. **If the workflow can't be used**, build and upload by hand. Play needs the
   AAB, not the APK:
   ```
   ./gradlew :app:bundleRelease
   # -> app/build/outputs/bundle/release/app-release.aab
   "$JAVA_HOME/bin/keytool.exe" -printcert -jarfile \
       app/build/outputs/bundle/release/app-release.aab | grep SHA256
   # expect the upload cert fingerprint 4C:ED:9F:40:...
   ```
   Then Play Console → the app → the track → *Create new release* → upload the
   AAB.

8. **F-Droid**: nothing to do. `metadata/dev.otherworld.shoppinglist.yml` on
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata) uses `UpdateCheckMode: Tags`
   + `AutoUpdateMode: Version`, so fdroidbot detects the new tag, appends a build
   entry for the same `versionCode`, and builds from source. It publishes on
   F-Droid's own cycle (typically a day or few).

## Don't break sync

- **Never** upload an ad-hoc build to Play with a `versionCode` that has no
  matching git tag — Play jumps ahead of F-Droid.
- **Never** push a version tag you aren't also uploading to Play — F-Droid jumps
  ahead of Play.
- If a `versionCode` is ever accidentally consumed on Play, bump past it
  everywhere (tag included) so the next real release lines up again.

## Baseline

`0.4.0` = `versionCode 4` = commit tagged `v0.4.0` — live/queued on both stores.
`0.3.0` (`versionCode 3`) shipped to Play only and was intentionally left
un-tagged.
