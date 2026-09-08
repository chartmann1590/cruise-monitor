# Phase 2 — Android app MVP

**Status: DONE.** Verified with a real, successful `./gradlew assembleDebug` build producing a 23MB debug APK.

## What's built

- Kotlin + Jetpack Compose, Gradle wrapper 8.6, AGP 8.3.2, Kotlin 1.9.24, Compose BOM 2024.06.00, compileSdk/targetSdk 34, minSdk 26.
- Firebase: Auth (email/password), Firestore (real-time listeners via `callbackFlow`), Cloud Messaging.
- Registered in the `cruisewatch-app` Firebase project as `com.cruisewatch.app`; `app/google-services.json` checked in (this file is meant to be public — it's not a secret, unlike the scraper's service account key).

### Screens (all implemented)

- **Sign In** (`ui/screens/SignInScreen.kt`) — email/password sign-in or sign-up via `AuthViewModel`.
- **Tracked Cruises** (`ui/screens/TrackedCruisesScreen.kt`) — list of active tracked cruises from Firestore, FAB to add one, banner ad pinned at the bottom.
- **Add Cruise** (`ui/screens/AddCruiseScreen.kt`) — line/cabin-category dropdowns, ship/dates/fare fields, writes a `trackedCruises` doc; shows an interstitial ad on successful save (see Ads below).
- **Price History** (`ui/screens/PriceHistoryScreen.kt`) — simple Canvas line chart + list, backed by the `priceSnapshots` subcollection.
- **Alerts** (`ui/screens/AlertsScreen.kt`) — live `alerts` collection, "mark as claimed" action.
- **Claims reference** (`ui/screens/ClaimsScreen.kt`) — browses all 5 lines' policies from bundled `assets/cruise-line-policies.json` (a manually-synced copy of `docs/cruise-line-policies.json` — no build-time sync step yet, keep them in sync by hand when a policy changes).

Bottom nav switches between Cruises / Alerts / Policies (`ui/CruiseWatchNavHost.kt`).

### Push notifications

`fcm/CruiseWatchMessagingService.kt` receives the pushes sent by `scraper/src/fcm.ts`, shows a system notification, and registers/refreshes this device's FCM token in `users/{userId}.fcmTokens` on `onNewToken`. `MainActivity` requests `POST_NOTIFICATIONS` on API 33+ and registers the initial token after sign-in.

### Ads (AdMob — banner + interstitial)

Added per explicit request. `ads/AdIds.kt`, `ads/BannerAd.kt`, `ads/InterstitialAdManager.kt`.

- **Banner**: shown at the bottom of the Tracked Cruises screen.
- **Interstitial**: preloaded on app start, shown after a user successfully adds a cruise (a natural break point — never mid-task), then immediately preloads the next one.
- **Currently wired to Google's public TEST ad unit IDs** (`ca-app-pub-3940256099942544/...`) and the public TEST App ID in `AndroidManifest.xml`. These are safe to ship in debug builds but serve only test creatives and earn no revenue.

**Before a Play Store release**, you must:
1. Create a real AdMob account/app at [admob.google.com](https://admob.google.com) (no CLI/API exists for this — it's a manual console step requiring you to accept AdMob's terms and link payment info).
2. Replace the App ID in `app/src/main/AndroidManifest.xml`'s `com.google.android.gms.ads.APPLICATION_ID` meta-data.
3. Replace `AdIds.BANNER` / `AdIds.INTERSTITIAL` in `app/src/main/java/com/cruisewatch/app/ads/AdIds.kt` with your real ad unit IDs.
4. Add an `AD_ID` / ads-related disclosure to the Play Store data-safety form (Phase 4).

## Firebase infrastructure (also done, via CLI)

- Firebase project `cruisewatch-app` created (`firebase projects:create`).
- Firestore database created in Native mode, region `nam5` (`gcloud firestore databases create`).
- Security rules (`firestore.rules`) and composite indexes (`firestore.indexes.json`) deployed (`firebase deploy --only firestore`).
- Android app registered in the project; `google-services.json` downloaded via `firebase apps:sdkconfig`.
- Admin SDK service account key generated (`gcloud iam service-accounts keys create`) and stored as the `FIREBASE_SERVICE_ACCOUNT` GitHub Actions secret (`gh secret set`).
- **End-to-end verified against real Firestore**: seeded a test `trackedCruises` doc, ran the scraper (`scraper/src/index.ts`), confirmed a real `priceSnapshots` doc and a real `alerts` doc were written with correct data, then cleaned up the test data.
- GitHub Actions workflow manually triggered (`gh workflow run` / `gh run watch`) and confirmed green end-to-end (48s run).

## Known gaps / next steps

- No automated test suite yet (manual build verification only).
- `assets/cruise-line-policies.json` is a manual copy of `docs/cruise-line-policies.json` — no sync tooling.
- Not yet run on an actual device/emulator — only verified via `gradle assembleDebug` (compiles, links resources, packages correctly) and `compileDebugKotlin`. Installing and clicking through the real UI (sign-up flow, adding a cruise, seeing the banner/interstitial render) is still open — see `plan/07-verification.md`.
- `local.properties` (SDK path) and Gradle wrapper are machine-specific/standard respectively; `local.properties` is gitignored as usual.

## Status checklist

- [x] Kotlin + Compose project scaffolded, builds successfully (`assembleDebug`)
- [x] Firebase Auth, Firestore, FCM wired
- [x] All 5 planned screens implemented
- [x] AdMob banner + interstitial wired (test ad units)
- [x] Firebase project, Firestore, security rules, indexes — created and deployed via CLI
- [x] GitHub Actions CI verified green end-to-end
- [x] Full scraper-to-Firestore pipeline verified against real data
- [ ] Installed and manually tested on a device/emulator
- [ ] Real AdMob account/App ID/ad units (deferred to Phase 4, Play Store readiness)
