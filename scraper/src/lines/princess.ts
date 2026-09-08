import type { LineScraper } from "../types.js";

/**
 * TODO (Phase 3): no known JSON-endpoint prior art for Princess. See
 * lines/carnival.ts for the reverse-engineering method and DOM-scraping
 * fallback pattern to follow.
 */
export const princess: LineScraper = async (cruise) => {
  throw new Error(
    `princess scraper not implemented yet (cruise ${cruise.id}: ${cruise.ship} ${cruise.sailDate})`
  );
};
