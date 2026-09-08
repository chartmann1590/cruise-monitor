# Phase 4 — Play Store readiness

Goal: ship the app publicly.

## Steps

1. Google Play developer account — $25 one-time fee (the one non-free cost in this whole project).
2. Privacy policy page — required by the Play listing; must disclose that the app tracks third-party cruise line pricing on the user's behalf and what account data is stored.
3. Firestore security rules — enforce multi-user isolation so each user can only read/write their own `trackedCruises`, `priceSnapshots`, and `alerts`.
4. Closed testing track first (internal testing with a handful of real accounts), then promote to production once stable.

## Status

- [ ] Not started — depends on Phase 2 app being feature-complete and Phase 1/3 scrapers being stable enough to demo
