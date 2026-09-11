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
- [x] Play publisher service account created via `gcloud` in the `cruisewatch-app` GCP project (same project as
      Firebase): `cruisewatch-play-publisher@cruisewatch-app.iam.gserviceaccount.com`. Android Publisher API
      enabled on that project. Key generated and uploaded directly to the `PLAY_SERVICE_ACCOUNT_JSON` GitHub
      secret; no copy was left on disk.
- [x] All 8 GitHub Actions secrets are set: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
      `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, `ADMOB_APP_ID`,
      `ADMOB_BANNER_AD_UNIT_ID`, `ADMOB_INTERSTITIAL_AD_UNIT_ID`.
- [ ] Google Play developer account — you need an existing account for this ($25 one-time fee if not already paid)
- [ ] Create the app listing in Play Console (one-time, manual — see below)
- [ ] **Grant the service account access in Play Console** (one-time, manual, no API exists for this step — see
      below). This is the one remaining setup step; everything else is done.
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

## 2. Play Console service account — done, one manual step left

The service account itself is already created (via `gcloud`, in the `cruisewatch-app` GCP project — the same
project that backs Firebase): `cruisewatch-play-publisher@cruisewatch-app.iam.gserviceaccount.com`. The Android
Publisher API is enabled on that project, and its JSON key is already uploaded to the `PLAY_SERVICE_ACCOUNT_JSON`
GitHub secret (no copy was left on disk).

**What's left is Play-Console-only and has no API** — Google does not expose an endpoint to grant a service
account access to a Play Console app, so this one click-through has to happen in the console UI:

1. Play Console → **Setup → API access**. It should detect the `cruisewatch-app` GCP project automatically
   (link it if prompted — accept/select that project).
2. Find `cruisewatch-play-publisher@cruisewatch-app.iam.gserviceaccount.com` in the service accounts list →
   **Grant access**.
3. Give it the **Release manager** permission (minimum needed to upload and publish releases) for the
   CruiseWatch app → **Invite user**.
4. Allow a few minutes for the permission to propagate.

## 3. GitHub Actions secrets — already set

All 8 secrets below are already in the repo (`RELEASE_KEYSTORE_BASE64` from `C:\Users\Charles\Key.jks`,
`RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` from `keystore.properties`, the three
`ADMOB_*` values from `local.properties`, and `PLAY_SERVICE_ACCOUNT_JSON` from step 2 above) — nothing further
to do here unless a value needs rotating:

| Secret | Source |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | base64 of the release keystore |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | signing key alias |
| `RELEASE_KEY_PASSWORD` | signing key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | service account key from step 2 |
| `ADMOB_APP_ID` | AdMob App ID (`ca-app-pub-...~...`) |
| `ADMOB_BANNER_AD_UNIT_ID` | AdMob banner ad unit ID |
| `ADMOB_INTERSTITIAL_AD_UNIT_ID` | AdMob interstitial ad unit ID |

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
