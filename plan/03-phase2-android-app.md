# Phase 2 — Android app MVP

Goal: a working Kotlin app that lets a user track cruises and see prices/alerts/claim info sourced from Firestore. Can start in parallel with Phase 1 once the Firestore schema (Phase 0) is fixed, using hand-written test documents before real scraper data exists.

## Steps

1. Scaffold `/app` as a Kotlin + Jetpack Compose Android Studio project.
2. Integrate Firebase: Auth (email or Google sign-in), Firestore SDK, Cloud Messaging.
3. Screens:
   - **Add Cruise** — line, ship, sail date, cabin category, fare paid, final payment date
   - **Tracked Cruises list** — current price vs. paid, countdown to final payment
   - **Price History** — chart per cruise, backed by `priceSnapshots`
   - **Alerts** — push-triggered, shows drop amount + which policy applies + claim link/phone (from `docs/cruise-line-policies.json`)
   - **Claims reference screen** — browse all 5 lines' policies even without an active alert
4. FCM integration: receiver for push when a new `alerts` doc is written for the signed-in user; deep-link the notification into the relevant Alert detail screen.

## Status

- [ ] Not started
