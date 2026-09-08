# Key risks

## Terms of Service / scraping risk (primary)

Automated scraping of cruise-line sites is likely against those sites' Terms of Service even though CruiseSignal itself does exactly this and the underlying fare data is public. There's no way to fully eliminate this risk in code — it's a business/legal judgment call, not a technical one.

Mitigations:
- Keep polling per-user and infrequent (not a shared high-frequency crawl across all users' tracked cruises).
- Be prepared for a given cruise line to block requests, rate-limit, or change their site structure — treat each `scraper/lines/<line>.ts` module as independently disposable/reworkable.
- Isolate scraping logic entirely in the `/scraper` backend repo so the Android app and Firestore schema never need to change when a scraper breaks or a line is paused.

## Site fragility / maintenance burden

Cruise line booking flows change their DOM/endpoints without notice. The DOM-scraping fallback (Phase 3, for lines without a discoverable JSON endpoint) is more fragile than the JSON-endpoint path (Phase 1) and will need more ongoing maintenance.

## Bot detection

[fzheng/cruise-price-tracker](https://github.com/fzheng/cruise-price-tracker) documents Royal Caribbean "occasionally deploys extra bot detection." Mitigate with bot-evasion headers, realistic user agents, and throttled intervals — but accept this may still eventually require slowing down or pausing a line's scraper.

**Resolved 2026-09-07** for both Royal Caribbean and Celebrity: live testing during Phase 1 found both lines' actual pricing pages return a hard Akamai 403 for plain HTTP requests, and a soft maintenance-page block for vanilla headless Playwright. The fix that worked: [`curl_cffi`](https://github.com/lexiforest/curl_cffi)'s `impersonate="chrome"` mode, which replicates a real Chrome TLS/JA3 fingerprint at the handshake level — verified live and repeatably against multiple endpoints on both domains. There's no equivalent for Node, so this step now runs as a Python subprocess (`scraper/python/fetch_room_pricing.py`) called from the TypeScript scraper. See `plan/02-phase1-rc-celebrity-scraping.md` for full detail. This is still a cat-and-mouse mitigation, not a permanent fix — Akamai/RCCL could change their detection approach at any time, which is exactly the ongoing-maintenance risk described below; the public ship/voyage *metadata* API (`aws-prd.api.rccl.com`) has no such protection and needed no impersonation for either brand. Celebrity's separate, older AEM-based search API (`/prd/*`) turned out to have its own application-layer restriction (authenticated requests came back with `hits: 0` for every filter combination tried) — not used by the working solution, so not a live blocker, but a reminder that "past the Akamai edge" doesn't always mean "past every layer."

**New dependency risk**: TLS fingerprint impersonation is itself a documented cat-and-mouse game — the [general Akamai-bypass research](https://scrapfly.io/blog/posts/how-to-bypass-akamai-anti-scraping) found during this investigation notes bypass techniques can break when Akamai updates its detection, sometimes as often as weekly, and that professionally-maintained bypass tooling is a paid, actively-maintained product for exactly this reason. `curl_cffi` is a free, community-maintained open-source project (not a paid bypass service) — treat a sudden return of 403s/soft-blocks as "the impersonation profile needs updating," not as a sign the whole approach is dead, and keep an eye on the project's releases.

## Play Store policy risk

Google Play may scrutinize an app whose core function is automated third-party data scraping. Flag this during Phase 4 and be ready to explain the app's data source in the store listing / review notes if asked.
