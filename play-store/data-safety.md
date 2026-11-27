# CruiseWatch — Play Console Data Safety answers

Source of truth for the Play Console "Data safety" form for
com.cruisewatch.app (phone + Wear OS companion — one form covers both).
Derived from the code on 2026-11-26 (app/build.gradle.kts and
wear/build.gradle.kts dependencies, manifests, CruiseWatchApp.kt,
ads/ConsentManager.kt, data/feedback/, ai/AiReportRepository.kt,
crosspromo module). If the SDK set or any data flow changes, update this
file and the Console form together.

This form was the subject of a Play rejection on 2026-09-24 (version
code 1012): "user data transmitted off devices that
you have not disclosed… Personal Info Data Type — Email Address." The
Email address row below is therefore called out explicitly: **email IS
collected** (Firebase Authentication, on phone and Wear) and must be
declared.

## Form-level answers

- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**
  (Firebase, AdMob, Cloudflare Workers and Hugging Face are all HTTPS;
  neither manifest enables cleartext traffic.)
- Account creation methods: **Username and password** (email),
  **OAuth** (Google Sign-In).
- Account deletion URL:
  https://cruisewatch-app.web.app/delete-account.html
  (In-app path: Settings → "Delete account". The web page offers
  sign-in-and-delete for users who no longer have the app.)
- Can users request data deletion without deleting their account?
  **Yes** — individual tracked cruises, price history and alerts can be
  deleted in-app at any time; full deletion removes the account and all
  associated data together.

## Behaviour the answers rest on (verified in code)

- The app is sign-in gated: account data (email, UID, cruises) exists
  only for signed-in users. Firebase Auth is used on phone AND Wear
  (Wear sign-in calls `requestEmail()`).
- AdMob runs only in the phone app (the Wear app has no ads SDK). As of
  0.5.1 the Mobile Ads SDK initializes only after UMP consent gathering
  completes and `canRequestAds()` is true, and every ad load is gated
  the same way (ads/ConsentManager.kt). AdMob is never removed — it is
  declared here and in the Ads declaration (Ads = Yes).
- Firebase Analytics has no custom events in code (no `logEvent` calls);
  collection is the SDK's automatic usage/session data. Crashlytics and
  Performance Monitoring are in both modules.
- The AI refund assistant runs fully on-device (MediaPipe, Qwen2.5).
  The model file is a one-time download from Hugging Face
  (litert-community), pinned to an immutable commit and verified by
  SHA-256 before use; no user data is sent with that download. The only
  assistant data that ever leaves the device is the single exchange a
  user explicitly flags via the in-app report flow.
- In-app feedback and AI reports are filed as issues in the PUBLIC
  GitHub repo chartmann1590/cruise-monitor via Cloudflare Workers —
  disclosed in the privacy policy and in the in-app feedback UI.
- Cross-promotion (Settings screen) fetches recommendations from a
  Cloudflare Worker and sends back anonymous view/click counts only.
- Firestore rules are owner-only (users/trackedCruises/alerts);
  priceSnapshots are not client-writable.

## Data types

### Personal info

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Email address | Yes | No | Required to create an account (email/password or Google Sign-In, phone + Wear) | App functionality, Account management |
| User IDs (Firebase UID) | Yes | No | Required for an account | App functionality, Account management, Fraud prevention, security, and compliance |

### Location

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Approximate location (IP-derived, via the AdMob SDK) | Yes | Yes (Google AdMob) | Required | Advertising or marketing, Analytics, Fraud prevention, security, and compliance |

The app itself requests no location permission and collects no precise
location.

### App activity

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| App interactions (Firebase Analytics automatic usage/session data) | Yes | No | Required | Analytics |
| Other user-generated content — tracked cruises & alerts (Firestore) | Yes | No | Required for the app's core function | App functionality |
| Other user-generated content — feedback text the user submits | Yes | No (filed in the developer's public GitHub repo; see privacy policy) | Optional (only if the user submits feedback) | App functionality |
| Other user-generated content — an AI exchange the user flags via the report flow | Yes | No (stored in Cloudflare D1; filed in the developer's public GitHub repo) | Optional (only on explicit report) | App functionality |

### Photos and videos

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Photos (a screenshot the user chooses to attach to a feedback report) | Yes | No (filed with the report in the developer's public GitHub repo) | Optional | App functionality |

### Financial info

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Purchase history (ad_free_monthly subscription status, via Google Play Billing 8.0.0) | Yes | No | Optional (only if the user subscribes) | App functionality, Account management |

Payment is processed entirely by Google Play; the app never sees card
details.

### App info and performance

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Crash logs (Firebase Crashlytics, phone + Wear) | Yes | No | Required | Analytics |
| Diagnostics (Firebase Performance Monitoring, phone + Wear) | Yes | No | Required | Analytics |

### Device or other IDs

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Device or other IDs — advertising ID (AdMob SDK) | Yes | Yes (Google AdMob) | Required | Advertising or marketing, Analytics, Fraud prevention, security, and compliance |
| Device or other IDs — Firebase installation ID / FCM registration token | Yes | No | Required | App functionality (push price-drop alerts), Analytics |

### Not collected

Contacts, calendar, health & fitness data, messages, audio, files &
documents (other than the optional feedback screenshot above), browsing
history, precise location, and web browsing are **not** collected by the
app or any SDK it bundles.
