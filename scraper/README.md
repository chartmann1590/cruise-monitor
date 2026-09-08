# scraper

Scheduled job (run via GitHub Actions, see `.github/workflows/price-check.yml`) that checks each active tracked cruise's current public fare and writes results to Firestore.

## Structure

- `src/types.ts` — shared types (`TrackedCruise`, `FareLookup`, `LineScraper`)
- `src/lines/<line>.ts` — one module per cruise line implementing `LineScraper`; currently all stubs (see `plan/02-phase1-rc-celebrity-scraping.md` and `plan/04-phase3-remaining-lines.md`)
- `src/lines/index.ts` — registry mapping `docs/cruise-line-policies.json` line ids to their scraper module
- `src/policies.ts` — loads `docs/cruise-line-policies.json`
- `src/policyWindow.ts` — decides whether a policy's claim window is currently open for a cruise
- `src/firestore.ts` — Firebase Admin SDK init
- `src/index.ts` — entrypoint: load active cruises -> scrape -> snapshot -> diff -> alert

## Local setup

```bash
cd scraper
npm install
pip install -r python/requirements.txt   # curl_cffi — see below
npx playwright install chromium          # only needed once a line uses DOM scraping (Phase 3)
```

### Why there's a Python dependency in a Node project

Royal Caribbean / Celebrity's pricing pages are behind Akamai Bot Manager,
which fingerprints the TLS handshake. Plain HTTP (curl, Node's
fetch/undici) gets a hard 403; a vanilla headless-Chromium request
(Playwright) gets a *soft* block (a generic maintenance page instead of
real content). [`curl_cffi`](https://github.com/lexiforest/curl_cffi)'s
`impersonate="chrome"` mode replicates a real Chrome TLS fingerprint and
gets through — verified live on 2026-09-07. There's no equivalent library
for Node, so `scraper/python/fetch_room_pricing.py` does that one step and
`scraper/src/lines/rccl-shared.ts` calls it via subprocess. Everything else
stays in TypeScript. If `python3` isn't on PATH (or on Windows, the `py`
launcher), set `PYTHON_BIN` to the right interpreter.

Create a Firebase project (free Spark tier), generate a service account key (Project Settings > Service Accounts > Generate new private key), and set it as an env var for local runs:

```bash
export FIREBASE_SERVICE_ACCOUNT="$(cat path/to/service-account.json)"
npm run dev
```

In GitHub Actions, store the same JSON as the `FIREBASE_SERVICE_ACCOUNT` repository secret — never commit it (see `.gitignore`).

## Adding a cruise line

1. Implement `LineScraper` in `src/lines/<line>.ts` (see the JSON-endpoint pattern in Phase 1's Royal Caribbean module, or the Playwright DOM pattern for lines without a discoverable endpoint).
2. Register it in `src/lines/index.ts` under the matching id from `docs/cruise-line-policies.json`.
3. No changes needed to `src/index.ts` or the Firestore schema — the pipeline is line-agnostic.
