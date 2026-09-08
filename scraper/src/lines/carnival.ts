import type { LineScraper } from "../types.js";

/**
 * TODO (Phase 3): no known JSON-endpoint prior art for Carnival. First use
 * browser DevTools network-tab inspection on Carnival's booking flow to look
 * for a JSON pricing endpoint (same method as the Royal Caribbean project).
 * If none is found, fall back to Playwright DOM scraping following the
 * pattern in https://github.com/fzheng/cruise-price-tracker (bot-evasion
 * headers, configurable user agent, throttled crawl interval).
 */
export const carnival: LineScraper = async (cruise) => {
  throw new Error(
    `carnival scraper not implemented yet (cruise ${cruise.id}: ${cruise.ship} ${cruise.sailDate})`
  );
};
