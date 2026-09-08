# Phase 3 — Remaining 3 lines (Carnival, Princess, Norwegian)

**Status: DONE. All 3 lines fully implemented and live-verified (2026-09-08).**

Unlike Royal Caribbean/Celebrity (Phase 1), **none of these three needed Python/curl_cffi TLS impersonation** — all work with plain Node `fetch()`. Carnival and Princess have no bot protection at all on the endpoints used; Norwegian has Akamai present site-wide but the specific `www.ncl.com/api/...` endpoints used weren't blocked in testing.

## Carnival (`scraper/src/lines/carnival.ts`)

The simplest of all 5 lines — one clean JSON search API, no session/voyage-id resolution dance needed.

1. **Ship code**: fetch the ship's marketing page (`carnival.com/en/Cruise-Ships/{slug}`, slug = `carnival-` + ship name lowercased/hyphenated) and read the hidden `id="ShipCode"` input's value. Confirmed: "carnival-vista" -> `VS`, "carnival-horizon" -> `HZ`.
2. **Pricing**: `GET carnival.com/cruisesearch/api/search?shipcode={code}&pagesize=50&async=true&currency=USD&locality=1&client=cruisesearch` returns **every future itinerary and sailing for that ship in one request** (18 itineraries / 84 sailings spanning 18 months, in testing). Each sailing has a `rooms` object keyed exactly `interior`/`oceanview`/`balcony`/`suite`, each with a live `price` or `soldOut: true`.

Verified: Carnival Vista, Ocean View, 2026-09-13 sailing -> **$730**, matching the live site exactly.

## Princess (`scraper/src/lines/princess.ts`)

Uses Princess's "UBE" (Universal Booking Engine) API at `gw.api.princess.com`. Needs a `pcl-client-id` header — a **public** client ID (not a secret) read straight out of princess.com's own JS bundle — plus an `appid` header (a small JSON blob with a client-generated session UUID, no real session needed).

1. **Ship code**: `GET .../resdb/p1.0/ships` -> `{ships:[{id,name}]}`.
2. **Voyage id**: `GET .../resdb/p1.0/products?...&light=false` returns every future sailing across the whole fleet in one ~900KB request; filter client-side for the matching ship + sail date to get the sailing's own voyage id (distinct from the itinerary/product id — e.g. product "AWG070"'s 2027-05-19 sailing has voyage id "2711"). **Must use `light=false`** — `light=true` returns a smaller shape with no per-sailing voyage id.
3. **Pricing**: `POST .../caps/pc/pricing/v1/cruises/{voyageId}` with a booking/filters JSON body returns every cabin category for that one sailing in one response — both "BESTFARE" and "BESTVALUE" fare types, categories prefixed by tier (`S`=Suite, `M`=Mini-Suite, `B`=Balcony, `O`=Oceanview, `I`=Interior). Princess's own UI does **not** exclude guarantee-type categories (status `"G"`) from its displayed lowest price, so neither do we — take the plain minimum per tier.

Verified: Island Princess, 2027-05-19 sailing -> Interior **$674**, Oceanview $714, Balcony $1,481, Mini-Suite **$1,812**, Suite $3,459 — matches the live site exactly for both categories tested.

## Norwegian (`scraper/src/lines/norwegian.ts`)

1. **Ship code**: derived from the ship name (strip a "Norwegian " prefix, uppercase, remove spaces) — confirmed for Getaway/Viva/Encore. Not verified for older ships without a "Norwegian "-prefixed name (e.g. "Pride of America").
2. **Itinerary codes for that ship**: `GET .../api/v2/vacations/search?filterConfig=search-filters-configuration&limit=100&offset=0&ship={code}` — a ship can run several different itineraries (37 for Getaway in testing); returns each itinerary's `code` with only a lead-in price, not per-date.
3. **Per-sailing pricing**: `GET .../api/vacation-builder/v2/itinerary/{itineraryCode}/sailings?numberOfGuests=2` returns every future sailing of that one itinerary with `sailing.sailStartDate` and a `stateroomTypesPricing[]` array keyed by cabin type (`HAVEN`/`MINISUITE`/`BALCONY`/`OCEANVIEW`/`INSIDE`/`STUDIO`), each with a `combinedPrice`. Try each of the ship's itinerary codes until one has a sailing matching the target date (worst case ~37 requests for a ship with many itinerary variants — acceptable for a scheduled background job).

Verified: Norwegian Getaway, itinerary "GETAWAY4MIANPINASMIA", 2026-09-14 sailing -> Inside **$199**, Balcony **$224**, Oceanview $229, Mini-Suite $262, Haven (suite) $924 — matches the live site exactly for both categories tested.

## Cabin category mapping across all 5 lines now covered

| Our category | RC/Celebrity | Carnival | Princess | Norwegian |
|---|---|---|---|---|
| Interior | INTERIOR | interior | I* | INSIDE |
| Ocean View | OUTSIDE | oceanview | O* | OCEANVIEW |
| Balcony | BALCONY | balcony | B* | BALCONY |
| Concierge (Celebrity only) | CONCIERGE | — | — | — |
| Aqua (Celebrity only) | AQUA | — | — | — |
| Mini-Suite (Princess/NCL only) | — | — | M* | MINISUITE |
| Suite | DELUXE | suite | S* | HAVEN |

Each line's scraper module has its own `toCabinClassType`/`toRoomKey`/`toTierPrefix`/`toStateroomCode` mapping function — not unified into shared code since the category sets genuinely differ per line (Celebrity's extra tiers, Princess/NCL's Mini-Suite).

## Remaining steps (all 3 lines)

- None of these three have been run through the full Firestore-backed `src/index.ts` pipeline yet either — only the scraper modules themselves are live-tested (same gap as Phase 1's Royal Caribbean/Celebrity).
- No monitoring for "this line's scraper started failing" beyond GitHub Actions log output.
- Norwegian's per-ship "try every itinerary code" approach is the heaviest of the 5 (up to ~37 requests for one price check) — fine for a scheduled job checking a handful of tracked cruises, but would need rethinking if tracking hundreds of NCL cruises at once.
- None of these three were tested against Akamai-style bot protection under sustained/repeated polling — today's single-request tests succeeding doesn't guarantee no rate-limiting kicks in under the real GitHub Actions schedule. Watch for this once Phase 1's remaining step (wiring `src/index.ts` into the actual scheduled workflow) is done.

## Status checklist

- [x] Carnival — implemented, live-verified
- [x] Princess — implemented, live-verified
- [x] Norwegian — implemented, live-verified
- [x] All 5 cruise lines now have real, working `LineScraper` implementations (see `scraper/src/lines/index.ts`)
- [ ] Full Firestore-backed pipeline run for these 3 lines (same gap noted in Phase 1)
