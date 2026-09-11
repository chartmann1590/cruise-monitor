# Phase 4 — Play Store readiness

Goal: ship the app publicly, with releases published automatically by CI.

## Status — everything below is done

- [x] Privacy policy page — `website/privacy.html` (https://cruisewatch-app.web.app/privacy.html)
- [x] Firestore security rules — `firestore.rules` (per-user isolation on `trackedCruises`, `priceSnapshots`, `alerts`)
- [x] App icon — redesigned adaptive icon (ship + price-drop badge), applied identically to `app` and `wear`
- [x] Fixed the Wear packaging bug — the legacy `wearApp(project(":wear"))` Gradle mechanism embeds the wear
      APK as a "micro APK" inside the phone APK, which only works if both apps share one applicationId (a
      Wear-1.x-era requirement). Removed it; the wear module now has applicationId `com.cruisewatch.app`
      (matching the phone app) and its own release signing config, and is published as a separate AAB within
      the same Play Console listing — Google's current guidance for standalone Wear OS companion apps.
- [x] Store listing assets — icon, feature graphic, phone + Wear OS screenshots, promo video (with voiceover
      and burned-in captions), all in `fastlane/metadata/android/en-US/images/`
- [x] Store listing translated into 6 languages — `fastlane/metadata/android/{en-US,es-ES,fr-FR,de-DE,pt-BR,it-IT}/`
- [x] AdMob wired for production — App ID / ad unit IDs resolved from `local.properties` (local) or CI secrets
      (never hardcoded); see `app/build.gradle.kts`
- [x] Release signing config — both `app/build.gradle.kts` and `wear/build.gradle.kts` read the same
      `keystore.properties` (gitignored)
- [x] CI publish workflow — `.github/workflows/release-play-store.yml`, builds and uploads both AABs
- [x] Play publisher service account created via `gcloud` in the `cruisewatch-app` GCP project (same project as
      Firebase): `cruisewatch-play-publisher@cruisewatch-app.iam.gserviceaccount.com`
- [x] Service account granted Release manager access in Play Console (done manually, then verified by an
      actual authenticated API call against the app listing — confirmed working)
- [x] All 8 GitHub Actions secrets set: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
      `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, `ADMOB_APP_ID`,
      `ADMOB_BANNER_AD_UNIT_ID`, `ADMOB_INTERSTITIAL_AD_UNIT_ID`
- [x] First release uploaded via the Android Publisher API directly: both AABs (phone versionCode 1, wear
      versionCode 1), the full store listing (title/descriptions/images/video) in all 6 languages, and a
      **production-track release in `draft` status** — committed and live in Play Console, not rolled out to
      any users. (Note: contrary to older guidance that a brand-new app's first release must be uploaded
      manually through the console, this succeeded via the API on the first attempt — worth knowing in case a
      future app in this org behaves differently.)

## What's left — Play-Console-UI-only, requires a human decision

1. Open Play Console → **CruiseWatch → Production**. The draft release (both AABs, all metadata) is sitting
   there waiting.
2. Go through **App content** (data safety form, target audience, ads declaration, content rating
   questionnaire) if not already complete — this is a compliance step Google requires a human to attest to,
   not something that should be automated.
3. Review the draft release, then click **Review release → Start rollout to Production** (or promote through
   internal/closed testing first if you'd rather soak-test before a public release — the draft is on the
   `production` track already, but nothing goes live until you explicitly start the rollout).

## Releasing future updates

- Push a tag matching `v*.*.*` (e.g. `git tag v0.2.0 && git push origin v0.2.0`), or run the workflow manually
  from the Actions tab (**Release to Play Store → Run workflow**) and pick a track.
- Bump `versionCode` and `versionName` in both `app/build.gradle.kts` and `wear/build.gradle.kts` before each
  release — Play requires a strictly increasing `versionCode` across every AAB in a release, phone and wear
  included.
