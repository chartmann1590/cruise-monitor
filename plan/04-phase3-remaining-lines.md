# Phase 3 — Remaining 3 lines (Carnival, Princess, NCL)

Goal: extend scraping coverage to all 5 lines promised in the plan.

## Steps

For each of Carnival, Princess, and NCL:

1. Use browser DevTools network-tab inspection on the cruise line's own booking flow to look for a JSON pricing endpoint, following the same reverse-engineering method demonstrated by [jdeath/CheckRoyalCaribbeanPrice](https://github.com/jdeath/CheckRoyalCaribbeanPrice).
2. If a JSON endpoint is found: implement `scraper/lines/<line>.ts` the same way as Phase 1's Royal Caribbean module.
3. If no JSON endpoint is found within reasonable effort: fall back to Playwright DOM scraping, following the pattern in [fzheng/cruise-price-tracker](https://github.com/fzheng/cruise-price-tracker) (bot-evasion headers, configurable user agent, throttled crawl interval — expect more maintenance burden and bot-detection risk than the JSON-endpoint path).
4. Wire the new module into the same GitHub Actions snapshot/diff/alert pipeline from Phase 1 — no changes needed to the Android app or Firestore schema, since each line just becomes another `scraper/lines/<line>.ts` module.

## Status

- [ ] Not started — depends on Phase 1 pipeline existing to plug new line modules into
