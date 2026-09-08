import type { LineScraper } from "../types.js";

/**
 * TODO (Phase 3): no known JSON-endpoint prior art for Norwegian (NCL).
 * Policy windows also vary by region/fare type for this line (see
 * docs/cruise-line-policies.json), so alert-eligibility logic here needs
 * extra care. See lines/carnival.ts for the scraping method to follow.
 */
export const norwegian: LineScraper = async (cruise) => {
  throw new Error(
    `norwegian scraper not implemented yet (cruise ${cruise.id}: ${cruise.ship} ${cruise.sailDate})`
  );
};
