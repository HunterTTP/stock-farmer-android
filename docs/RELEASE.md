# Release to testers

Testers get the app from the Google Play **internal testing** track. A production release has not been started and will be planned after testing. Keep the upload keystore, service account key and passwords out of Git.

## Release inputs

- `versionName` is maintained by hand in `app/build.gradle.kts` (currently `0.1.0`). Bump it for a new release.
- `versionCode` comes from the tag: `node tools/release.mjs version-code 2026.11.6-ga-1` prints `2026110601`. The build gets it as `-PversionCode=<code>`. Play accepts each versionCode only once.
- Release notes ("what's new") come from `app/src/main/assets/information/Changelog.md`. `node tools/release.mjs notes` writes the lines added since the previous release tag (falling back to the newest day), cut at Play's 500-character limit.
- Signing: Play App Signing holds the app key. The build signs with an upload keystore that lives outside Git, read from `STOCKFARMER_UPLOAD_KEYSTORE` (path), `STOCKFARMER_UPLOAD_KEYSTORE_PASSWORD`, `STOCKFARMER_UPLOAD_KEY_ALIAS` and `STOCKFARMER_UPLOAD_KEY_PASSWORD`. With them unset the build is unsigned.

## Change to tester

Everyday work is committed and pushed to `main`; those pushes ship nothing. A release to testers is a tag named `YYYY.M.D-ga-N` pushed to GitHub: the date without leading zeros, then a counter that starts at 1 each day (`2026.11.6-ga-1`, `2026.11.6-ga-2`). Any pushed tag runs `.github/workflows/release.yml`, so tags are only ever release tags.

1. Work lands on `main`: change, local checks from [DEVELOPMENT.md](DEVELOPMENT.md), changelog line, commit, `git pull --rebase origin main`, `git push origin main` (see [AGENTS.md](../AGENTS.md)).
2. Ship to testers, only when the owner asks, from a clean, pushed `main`:

   ```bash
   git switch main && git pull --ff-only origin main && git fetch --tags origin
   day=$(date +%Y.%-m.%-d); n=1
   while git rev-parse -q --verify "refs/tags/$day-ga-$n" >/dev/null; do n=$((n+1)); done
   git tag -a "$day-ga-$n" -m "Release to testers" && git push origin "$day-ga-$n"
   ```

3. The workflow rejects a tag not matching `YYYY.M.D-ga-N`, runs `node --test tools/` and the gate (`spotlessCheck`, `:core:test`, `:app:testDebugUnitTest`, `:app:lintDebug`), builds a signed bundle with `:app:bundleRelease -PversionCode=<code>`, and uploads it to the internal testing track (status completed) with the R8 mapping and the notes. The AAB and mapping are also kept as a workflow artifact for 90 days.
4. The build appears in Play within minutes. Testers update from the Play Store.

Testers need a Google account and open the opt-in link once. Internal testing allows up to 100 testers and needs no Google review.

### Rebuild an existing tag

In GitHub, Actions, Release, Run workflow: enter the existing tag in `tag`. It rebuilds that tag the same way. Play rejects a versionCode it already has, so this helps only after a failure that happened before the upload.

### Failures

The failed run's log shows which step broke. Fix it on `main` and push a new tag (the next `-ga-N`). Never move, reuse or delete a tag. A versionCode can only be uploaded once, so a retry that reached Play needs a new tag.

## Settings

| Where | Setting |
| --- | --- |
| GitHub secret `UPLOAD_KEYSTORE_BASE64` | Upload keystore, base64 |
| GitHub secret `UPLOAD_KEYSTORE_PASSWORD` | Keystore password |
| GitHub secret `UPLOAD_KEY_ALIAS` | Key alias |
| GitHub secret `UPLOAD_KEY_PASSWORD` | Key password |
| GitHub secret `PLAY_SERVICE_ACCOUNT_JSON` | Service account JSON key, whole file |
| Play Console, Testing, Internal testing | Track the workflow uploads to |
| Play Console, Internal testing, Testers | Email list of testers (Google accounts) |
| Play Console, Internal testing, Testers | Opt-in link to send testers |
| Play Console, Setup, App signing | Play App Signing on |

## One-time setup (owner)

- [ ] Create a Google Play developer account and finish identity verification.
- [ ] Create the app in Play Console with package `com.hunter.stockfarmer`. The package name cannot change later.
- [ ] Confirm Play App Signing is on.
- [ ] Create the internal testing tester list (emails) and note the opt-in link.
- [ ] Create a Google Cloud project and enable the Google Play Android Developer API.
- [ ] Create a service account in it and download a JSON key.
- [ ] In Play Console, Users and permissions, invite the service account email and grant "Release to testing tracks" and "View app information". It can take up to 24 hours to apply.
- [ ] Generate the upload keystore, and store it and both passwords outside the repository with a backup:

  ```bash
  keytool -genkeypair -v -keystore stockfarmer-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
  ```

- [ ] Add the five GitHub secrets (repository Settings, Secrets and variables, Actions). Base64 of the keystore:

  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("stockfarmer-upload.jks")) | Set-Clipboard
  ```

  ```bash
  base64 -w0 stockfarmer-upload.jks
  ```

  The other four are the two passwords, the alias and the contents of the service account JSON file.
- [ ] Upload the first AAB by hand in Play Console (internal testing, Create new release), because the API cannot publish an app's first release. Build it locally, signed:

  ```bash
  export STOCKFARMER_UPLOAD_KEYSTORE=/path/to/stockfarmer-upload.jks
  export STOCKFARMER_UPLOAD_KEYSTORE_PASSWORD=...
  export STOCKFARMER_UPLOAD_KEY_ALIAS=upload
  export STOCKFARMER_UPLOAD_KEY_PASSWORD=...
  ./gradlew :app:bundleRelease -PversionCode=2026110601
  ```

  The bundle is in `app/build/outputs/bundle/release/`. Play requires 16 KB page-size support and checks it at this first upload, so fix any complaint before going on. Later tags must produce a higher versionCode than this one.
- [ ] Push the first tag as described above and record the result here (date, tag, versionCode, what Play showed).

## Later

A closed testing track needs Google review and comes after internal testing. A personal developer account must run a closed test with at least 12 testers for 14 days before it can apply for production access.
