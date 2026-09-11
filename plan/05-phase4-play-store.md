# Phase 4 — Play Store readiness

Goal: ship the app publicly, with releases published automatically by CI.

## Status

- [x] Privacy policy page — `website/privacy.html` (https://cruisewatch-app.web.app/privacy.html)
- [x] Firestore security rules — `firestore.rules` (per-user isolation on `trackedCruises`, `priceSnapshots`, `alerts`)
- [x] App icon — redesigned adaptive icon (ship + price-drop badge), applied identically to `app` and `wear`
- [x] Store listing assets — icon, feature graphic, phone + Wear OS screenshots, promo video, title/short/full
      description, all in `fastlane/metadata/android/en-US/`
- [x] AdMob wired for production — App ID / ad unit IDs resolved from `local.properties` (local) or CI secrets
      (never hardcoded); see `app/build.gradle.kts`
- [x] Release signing config — `app/build.gradle.kts` reads `keystore.properties` (gitignored)
- [x] CI publish workflow — `.github/workflows/release-play-store.yml`
- [ ] Google Play developer account — you need an existing account for this ($25 one-time fee if not already paid)
- [ ] Create the app listing in Play Console (one-time, manual — see below)
- [ ] Create the Play Console service account for CI (one-time, manual — see below)
- [ ] Add the GitHub Actions secrets listed below
- [ ] First manual release to Internal testing (Play rejects the very first release of a new app via API)
- [ ] Promote internal → closed testing → production once stable

## 1. Create the app in Play Console

1. Go to [play.google.com/console](https://play.google.com/console) → **Create app**.
2. App name: `CruiseWatch`. Default language: English (US). App type: App. Free.
3. Fill in the **App content** section (data safety, target audience, ads declaration — yes, this app shows
   ads via AdMob; content rating questionnaire; privacy policy URL: `https://cruisewatch-app.web.app/privacy.html`).
4. Under **Store presence → Main store listing**, either fill in the fields manually from
   `fastlane/metadata/android/en-US/` (title.txt, short_description.txt, full_description.txt) and upload the
   images in `fastlane/metadata/android/en-US/images/` (icon.png, featureGraphic.png, phoneScreenshots/,
   wearScreenshots/), or let `fastlane supply` do it later — either works.
5. Upload the promo video: `fastlane/metadata/android/en-US/images/promo_video.mp4` needs to be uploaded to a
   YouTube channel you control first (Play Store only accepts a YouTube link, not a direct file upload). Once
   uploaded, put the YouTube URL in `fastlane/metadata/android/en-US/video.txt` (create this file) and paste the
   same URL into the store listing's "Video" field in Play Console.
6. Under **Testing → Internal testing**, create a release and **upload the AAB manually once** — Google Play
   does not allow the very first release of a brand-new app to come through the Publishing API, so this one
   upload has to happen through the console UI. Build it locally with `./gradlew :app:bundleRelease` (after
   setting up `keystore.properties`, see below) or grab the artifact from a CI run of the workflow before the
   Play upload step.
7. After that first manual release exists, every subsequent release can go through
   `.github/workflows/release-play-store.yml`.

## 2. Create the Play Console service account (for CI publishing)

1. In Play Console: **Setup → API access**. If this is the first time, it'll prompt you to link a Google Cloud
   project — accept the default (or pick an existing project).
2. Click **Create new service account** — this opens the Google Cloud Console with a pre-filled service account
   creation page. Give it a name like `cruisewatch-play-publisher`, no roles needed at the GCP IAM level, click
   **Done**.
3. Back in that GCP service account's page, go to **Keys → Add key → Create new key → JSON**. This downloads a
   `.json` file — **do not commit this file anywhere**.
4. Back in Play Console **API access**, find the service account you just created and click **Grant access**.
   Give it the **Release manager** permission (minimum needed to upload and publish releases) for this app, then
   **Invite user**.
5. It can take a few minutes for the permission to propagate.

## 3. Add GitHub Actions secrets

In the GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**. Add all of these:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 Key.jks` (or on Windows: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("Key.jks"))`) of your release keystore |
| `RELEASE_KEYSTORE_PASSWORD` | Your keystore password (same as `storePassword` in local `keystore.properties`) |
| `RELEASE_KEY_ALIAS` | `key0` (same as local `keystore.properties`) |
| `RELEASE_KEY_PASSWORD` | Your key password (same as `keyPassword` in local `keystore.properties`) |
| `PLAY_SERVICE_ACCOUNT_JSON` | The full contents of the service account JSON file from step 2.3 above |
| `ADMOB_APP_ID` | Your real AdMob App ID (`ca-app-pub-...~...`) |
| `ADMOB_BANNER_AD_UNIT_ID` | Your real AdMob banner ad unit ID |
| `ADMOB_INTERSTITIAL_AD_UNIT_ID` | Your real AdMob interstitial ad unit ID |

None of these values are ever hardcoded in source — `app/build.gradle.kts` resolves the AdMob IDs from an
environment variable (CI) or `local.properties` (local dev), falling back to Google's public test IDs if
neither is set. The keystore is assembled fresh from secrets on every CI run and deleted immediately after the
build step.

## 4. Releasing

- Push a tag matching `v*.*.*` (e.g. `git tag v0.2.0 && git push origin v0.2.0`), or run the workflow manually
  from the Actions tab (**Release to Play Store → Run workflow**) and pick a track (`internal`, `alpha`, `beta`,
  `production`).
- Bump `versionCode` and `versionName` in both `app/build.gradle.kts` and `wear/build.gradle.kts` before each
  release — Play requires a strictly increasing `versionCode` across every APK/AAB in a release, phone and
  bundled Wear app included.
- Start on `internal`, promote to `production` once verified.
