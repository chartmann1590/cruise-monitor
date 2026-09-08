# CruiseWatch (Android) — Free CruiseSignal-style price-drop tracker

## Context

CruiseSignal (https://cruisesignal.app) is an iOS-only app that watches a cruise you already booked (same ship, sail date, cabin category), detects when the public fare drops below what you paid, and tells you which cruise line's price-protection policy applies so you can file the claim yourself for a refund/OBC/upgrade. It doesn't file claims automatically — it's a watcher + policy-rules cheat sheet + alerting system.

There's no Android equivalent. The goal is to build one, Android-only, published to the Play Store, covering all 5 cruise lines CruiseSignal covers (Royal Caribbean, Carnival, Princess, Celebrity, Norwegian) from day one, using automated price scraping wherever possible, and built entirely on free infrastructure (the only real-world cost is the one-time $25 Google Play developer registration fee).

## Research findings that shape this plan

- No free multi-line cruise pricing API exists. Paid scraping-as-a-service exists (Apify listings for Royal Caribbean/Cruisemapper), but nothing free.
- **Royal Caribbean & Celebrity** have public JSON pricing endpoints that are already reverse-engineered in an MIT-licensed open-source project: [jdeath/CheckRoyalCaribbeanPrice](https://github.com/jdeath/CheckRoyalCaribbeanPrice) — explicitly "not a hack," calls the same public endpoints the website's browser uses, no login needed for public fare lookup. This is our fastest path for 2 of the 5 lines.
- A second MIT project, [fzheng/cruise-price-tracker](https://github.com/fzheng/cruise-price-tracker), scrapes Royal Caribbean via headless Playwright against the DOM (not JSON) and documents real anti-bot friction ("Royal Caribbean occasionally deploys extra bot detection... consider slowing the interval, using a residential network, or refreshing the user agent"). This is our fallback pattern for lines without a found JSON endpoint.
- Carnival, Princess, and NCL have no known open-source prior art — their JSON endpoints (if any) need to be discovered via browser DevTools network-tab inspection, following the same method the RC project used.
- Scraping a site's public pricing pages sits in a legal/ToS gray zone (most cruise lines' ToS prohibit automated access even though the data itself is public). This is a real risk for a Play-Store-published app — mitigate via low request rates, per-user (not shared) polling intervals, and being ready to drop a line's auto-scraping if a cruise line blocks/threatens. See [`06-risks.md`](06-risks.md).

## Architecture (all free-tier)

```
GitHub Actions (scheduled cron, free)
   -> runs scraper jobs per cruise line (Node/Playwright or direct HTTP where a JSON endpoint exists)
   -> writes price snapshots to Firebase Firestore (free Spark tier)
        |
Cloud Function / Actions job compares new price vs. tracked "price paid" per user
   -> on drop within that line's policy window: writes an alert doc + sends push via Firebase Cloud Messaging (free)
        |
Android app (Kotlin, Jetpack Compose)
   -> Firebase Auth (free) for accounts
   -> Firestore listeners for tracked cruises / price history / alerts
   -> FCM receiver for push notifications
   -> "Claim" screen: per-line policy rules, claim form links, phone numbers (static content, see docs/cruise-line-policies.json)
```

Why this shape: GitHub Actions gives free scheduled compute without needing to run/pay for a server. Firestore free tier easily covers a personal-scale app (50K reads/20K writes per day). FCM is free at any volume. The Android app itself never talks to cruise line sites directly — it only reads Firestore — so scraping logic, cruise-line-specific fragility, and any future IP-blocking risk stay isolated in the backend repo and can be iterated on without app releases.

## Phases

1. [Phase 0 — Skeleton & data model](01-phase0-skeleton.md)
2. [Phase 1 — Royal Caribbean + Celebrity scraping](02-phase1-rc-celebrity-scraping.md)
3. [Phase 2 — Android app MVP](03-phase2-android-app.md)
4. [Phase 3 — Remaining 3 lines (Carnival, Princess, NCL)](04-phase3-remaining-lines.md)
5. [Phase 4 — Play Store readiness](05-phase4-play-store.md)
6. [Key risks](06-risks.md)
7. [Verification checklist](07-verification.md)

## Repo layout

```
cruise-monitor/
  app/       Android Kotlin project
  scraper/   Node/Playwright scraper + GitHub Actions workflows
  docs/      Firestore schema, cruise-line policy reference data
  plan/      This plan, one file per phase
```
