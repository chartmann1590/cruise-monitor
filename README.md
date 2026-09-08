# CruiseWatch

A free, Android-only, CruiseSignal-style cruise fare price-drop tracker. See [`plan/00-overview.md`](plan/00-overview.md) for the full plan, architecture, and phase breakdown.

## Layout

```
app/       Android Kotlin project (Phase 2)
scraper/   Node/TypeScript scraper + GitHub Actions workflow (Phase 1/3)
docs/      Firestore schema, cruise-line policy reference data
plan/      Plan, one file per phase
```

## Status

- Phase 0 (skeleton & data model): done — see [`plan/01-phase0-skeleton.md`](plan/01-phase0-skeleton.md)
- Phase 1 (Royal Caribbean + Celebrity scraping): done, both lines live-verified — see [`plan/02-phase1-rc-celebrity-scraping.md`](plan/02-phase1-rc-celebrity-scraping.md)
- Phase 2 (Android app MVP): not started — see [`plan/03-phase2-android-app.md`](plan/03-phase2-android-app.md)
