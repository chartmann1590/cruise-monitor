import type { LineScraper } from "../types.js";
import { royalCaribbean } from "./royalCaribbean.js";
import { celebrity } from "./celebrity.js";
import { carnival } from "./carnival.js";
import { princess } from "./princess.js";
import { norwegian } from "./norwegian.js";

/** Keys must match the `id` field in docs/cruise-line-policies.json. */
export const lineScrapers: Record<string, LineScraper> = {
  royal_caribbean: royalCaribbean,
  celebrity,
  carnival,
  princess,
  norwegian,
};

export function getLineScraper(lineId: string): LineScraper {
  const scraper = lineScrapers[lineId];
  if (!scraper) {
    throw new Error(`No scraper registered for cruise line "${lineId}"`);
  }
  return scraper;
}
