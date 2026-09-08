# Phase 0 — Skeleton & data model

Goal: lay down the repo structure and reference data that every later phase depends on. No scraping, no app UI yet.

## Steps

1. Repo structure:
   - `/app` — Android Kotlin project (created in Phase 2)
   - `/scraper` — Node or Python + Playwright, run by GitHub Actions
   - `/docs` — shared Firestore schema, cruise-line policy reference data
2. Define Firestore collections (document in `docs/firestore-schema.md`):
   - `users` — auth profile
   - `trackedCruises` — `line`, `ship`, `sailDate`, `cabinCategory`, `farePaid`, `finalPaymentDate`, `bookingChannel`, owning `userId`
   - `priceSnapshots` — per tracked cruise, `{ cruiseId, timestamp, fare }`
   - `alerts` — `{ cruiseId, userId, dropAmount, detectedAt, policyId, claimed: bool }`
3. Encode each cruise line's policy rules as static reference data: policy name, window (e.g. RC's Best Price Guarantee pre-final-payment + 24hr post-payment upgrade window; Carnival's Early Saver vs 110% guarantee; Princess's Better Than Best Price 120%-capped-$2000 OBC; Celebrity's repricing vs 48hr OBC; NCL's region-dependent rules), eligible outcome, exclusions, claim link/phone.
   - Done: `docs/cruise-line-policies.json` (source of truth, consumed by both the scraper's alert-eligibility logic and the Android Claims screen).
4. This phase is pure reference data — no scraping needed — and unblocks the Claims screen (Phase 2) immediately, independent of scraper progress.

## Status

- [x] Repo directories created
- [x] `docs/cruise-line-policies.json` written
- [ ] `docs/firestore-schema.md` written
- [ ] `/scraper` Node project scaffolded (package.json, tsconfig, GitHub Actions workflow stub)
