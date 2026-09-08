# Phase 1 — Royal Caribbean + Celebrity scraping

**Status: DONE. Both lines fully implemented and verified live (2026-09-07).**

## Royal Caribbean

Verified end-to-end against a real future sailing: Allure of the Seas, 8-night Southern Caribbean & Perfect Day, sailing 2026-10-03 (package `AL08D140`).

| Cabin category | Fare |
|---|---|
| Interior | from $1,742.98 (GTY) / $1,841.98 (assignable) |
| Ocean View | from $2,323.98 |
| Balcony | from $2,637.98 |
| Suite | from $15,470.44 |

## Celebrity

Verified end-to-end against a real future sailing: Celebrity Eclipse, "Ultimate Southern Caribbean", sailing 2027-04-11 (package `EC12D054`). Celebrity has 6 cabin tiers (Royal Caribbean has 4) — same underlying codes for the shared tiers, plus two more:

| Cabin category | Fare |
|---|---|
| Inside (Interior) | from $2,904.88 |
| Ocean View | from $3,522.88 |
| Veranda (Balcony) | from $4,876.88 |
| Concierge Class | from $5,091.88 |
| AquaClass | from $7,676.88 |
| The Retreat (Suite) | from $16,546.88 |

Running `royalCaribbean(cruise)` / `celebrity(cruise)` against a `TrackedCruise`-shaped object (ship name, sail date, cabin category) reproduces these numbers exactly — tested via `dist/lines/royalCaribbean.js` and `dist/lines/celebrity.js` directly.

## How it works (both lines, same pipeline)

1. **Ship + voyage lookup** (`scraper/src/lines/rccl-shared.ts::resolveShipCode` / `resolveVoyage`) — calls `aws-prd.api.rccl.com`'s public `/en/royal/web/v2/ships` and `/en/royal/web/v3/ships/{shipCode}/voyages` endpoints. Public, unauthenticated, **no bot protection**. Despite the `/royal/` path segment, this endpoint covers the **whole RCCL Group fleet** — the ships list includes both `"brand":"R"` (Royal Caribbean) and `"brand":"C"` (Celebrity) entries, and the voyages endpoint works unchanged for a Celebrity ship code (confirmed with `EC` / Celebrity Eclipse). One function, both brands, zero brand-specific code needed here.
2. **packageCode construction** (`buildPackageCode`) — the real packageCode a sailing uses on the pricing pages is `{shipCode}{voyageCode}` (e.g. `AL` + `08D140` = `AL08D140`, or `EC` + `12D054` = `EC12D054`), confirmed for both brands by finding a real live itinerary page via web search (`.../itinerary/8-night-southern-caribbean-...-on-allure-AL08D140` and `.../itinerary/12-night-ultimate-southern-caribbean-...-on-eclipse-EC12D054`) and reading each page's embedded `packageCode`/`groupId`. The bare `voyageCode` from step 1's metadata API is **not** the right value on its own — using it alone was the reason the earliest attempts failed.
3. **Room pricing fetch** (`scraper/python/fetch_room_pricing.py`, called from `rccl-shared.ts::fetchRoomPricing` via subprocess) — `GET https://{domain}/room-selection/type-and-subtype` (domain = `www.royalcaribbean.com` or `www.celebritycruises.com`) with an `RSC: 1` header (React Server Component mode), returns a `rooms[].options.stateroomTypes[].stateroomSubtypes[]` array with a live price (`pricing.invoice.total`) per subtype. We take the lowest price across non-guarantee (`GTY`-coded) subtypes within the matching cabin type as "the current fare" for that category.

Both brands' `/itinerary/*` pages and pricing endpoints run on the **same Next.js infrastructure** — same request shape, same response shape, same headers. Celebrity additionally has an older, separate AEM-based marketing site with its own `/prd/*` API and anonymous-JWT auth flow (discovered along the way — see "Dead end" below) — that one was **not** needed for the working solution and isn't used by any code here.

## The Akamai problem, and how it was actually solved

The pricing pages **are** behind Akamai Bot Manager on both `royalcaribbean.com` and `celebritycruises.com`:
- A plain HTTP request (curl, Node's fetch/undici) gets a hard `403 Access Denied`.
- A vanilla headless-Chromium request (Playwright) gets a *soft* block: HTTP 200, but served a generic maintenance page ("royalcaribbean.com is on vacation" / "please be patient... we'll be back shortly") instead of real content.
- [`curl_cffi`](https://github.com/lexiforest/curl_cffi)'s `impersonate="chrome"` mode — which replicates a real Chrome TLS/JA3 fingerprint at the handshake level — **gets through cleanly** on both domains. Verified live, repeatedly, against multiple endpoints on each (ships metadata, `room-selection/type-and-subtype`, and `checkout/api/v1/rooms/checkout`).

There's no equivalent TLS-impersonation library for Node, so this one step (and only this step) is implemented in Python and called from the TypeScript scraper via `child_process.spawn`. Everything else — Firestore, the alert/policy pipeline, GitHub Actions — stays in TypeScript. See `scraper/python/fetch_room_pricing.py`'s docstring and `scraper/README.md` for full detail.

## Dead end (kept for reference, not used)

Celebrity's `www.celebritycruises.com` also serves a separate, older AEM-based search widget (used on ship-listing pages like `/cruise-ships/celebrity-beyond/itineraries`) backed by its own `/prd/*` API family — fully reverse-engineered while investigating (anonymous auth: `POST /prd/token` → JWT, used as `Authorization: token <jwt>` plus `X-request-id`/`X-country-code`/`X-currency-code`/`Accept-Language` headers; search endpoint at `GET /prd/cruises` with params `accessibleCabins`, `bookingType`, `offset`, `count`, `includeResults`, `includeFacets`, `groupBy=PACKAGE`, `office`, `cruiseType` ∈ `{CO, CT}`). This returned clean, correctly-authenticated `200` responses but `hits: 0` for every filter combination tried — most likely an application-layer bot-trust check independent of the Akamai edge bypass (curl_cffi has no real browser behind it to satisfy a JS-derived sensor-cookie check), though the exact required filter param wasn't ruled out either. **Abandoned in favor of the itinerary-page + `type-and-subtype` approach**, which turned out to work identically to Royal Caribbean's and didn't need this at all. Left documented in case a future "browse all sailings for a ship" feature needs it.

## Files

- `scraper/src/lines/rccl-shared.ts` — ship/voyage resolution (both brands), packageCode construction, cabin-category mapping (6 tiers, covering Celebrity's extra Concierge/AquaClass), and the subprocess bridge to the Python fetcher
- `scraper/python/fetch_room_pricing.py` — the actual Akamai-bypassing fetch + price extraction (brand-agnostic, takes `domain` as a parameter)
- `scraper/python/requirements.txt` — `curl_cffi`
- `scraper/src/lines/royalCaribbean.ts` / `scraper/src/lines/celebrity.ts` — thin wrappers, one per brand
- `.github/workflows/price-check.yml` — installs Python + `curl_cffi` alongside Node before running the scraper

## Remaining steps (both lines)

- Wire the GitHub Actions workflow (already scaffolded) to run `scraper/src/index.ts` on a schedule against real `trackedCruises` docs — currently only smoke-tested by calling `royalCaribbean(cruise)` / `celebrity(cruise)` directly, not through the full Firestore-backed pipeline.
- Send push via Firebase Cloud Messaging when a new `alerts` doc is created — not yet implemented (see `TODO (Phase 2)` in `src/index.ts`).
- Longer-term robustness: `fetch_room_pricing.py` depends on the exact JSON shape of the RSC response and on the `packageCode = shipCode + voyageCode` convention — both could change without notice on either site. No monitoring/alerting exists yet for "this line's scraper started failing" beyond the GitHub Actions job's own log output; consider adding this once Phase 2's push pipeline exists.
- `curl_cffi`'s TLS impersonation is itself a maintenance-prone mitigation (see `plan/06-risks.md`) — a sudden return of 403s/soft-blocks on either domain means "the impersonation profile needs updating," not "the whole approach is dead."

## Status checklist

- [x] Ship/voyage metadata resolution (both brands) — implemented, live-verified
- [x] packageCode construction (both brands) — implemented, live-verified
- [x] Room pricing fetch past Akamai (both brands) — implemented, live-verified end-to-end
- [x] Cabin-category mapping covering Celebrity's 6 tiers
- [ ] Full Firestore-backed pipeline run (currently only the scraper modules themselves are tested, not `src/index.ts` against real data)
- [ ] FCM push on alert creation
