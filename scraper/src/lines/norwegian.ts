import type { LineScraper } from "../types.js";

/**
 * VERIFIED LIVE on 2026-09-08. Despite Akamai being present on ncl.com
 * (an `/akam/...` sensor script is loaded on every page), the actual
 * `www.ncl.com/api/...` endpoints used here were NOT blocked in testing —
 * plain Node `fetch()` worked throughout, no TLS impersonation needed.
 *
 * Pipeline:
 *   1. Ship code: derived from the ship name (strip a "Norwegian " prefix,
 *      uppercase, remove spaces) — confirmed against live search results
 *      for "Norwegian Getaway" -> "GETAWAY", "Norwegian Viva" -> "VIVA",
 *      "Norwegian Encore" -> "ENCORE". Not verified for older ships with
 *      non-"Norwegian "-prefixed names (e.g. "Pride of America").
 *   2. Itinerary codes for that ship: `GET /api/v2/vacations/search
 *      ?filterConfig=search-filters-configuration&limit=100&offset=0&ship={code}`
 *      — a ship can run several different itineraries (37 for Getaway in
 *      testing); this returns each itinerary's `code` (e.g.
 *      "GETAWAY4MIANPINASMIA") with only a lead-in price, not per-date.
 *   3. Per-sailing pricing: `GET /api/vacation-builder/v2/itinerary/
 *      {itineraryCode}/sailings?numberOfGuests=2` returns EVERY future
 *      sailing of that itinerary with `sailing.sailStartDate` and a
 *      `stateroomTypesPricing[]` array keyed by cabin type code (HAVEN,
 *      MINISUITE, BALCONY, OCEANVIEW, INSIDE, STUDIO), each with a
 *      `combinedPrice`. Try each of the ship's itinerary codes until one
 *      has a sailing matching the target date.
 *
 * Confirmed end-to-end: Norwegian Getaway, itinerary
 * "GETAWAY4MIANPINASMIA", sailing 2026-09-14: Inside $199, Oceanview
 * $229, Balcony $224, Mini-Suite $262, Haven (suite) $924 — matches the
 * live site's search result price for the Inside category exactly.
 */

const SEARCH_API = "https://www.ncl.com/api/v2/vacations/search";
const SAILINGS_API = "https://www.ncl.com/api/vacation-builder/v2/itinerary";

function toShipCode(shipName: string): string {
  return shipName.replace(/^norwegian\s+/i, "").trim().toUpperCase().replace(/\s+/g, "");
}

function toStateroomCode(cabinCategory: string): string {
  const normalized = cabinCategory.trim().toLowerCase();
  if (["interior", "inside"].includes(normalized)) return "INSIDE";
  if (["ocean view", "oceanview", "outside", "ocean-view"].includes(normalized)) return "OCEANVIEW";
  if (["balcony", "veranda"].includes(normalized)) return "BALCONY";
  if (["mini-suite", "mini suite", "minisuite"].includes(normalized)) return "MINISUITE";
  if (["suite", "haven", "deluxe"].includes(normalized)) return "HAVEN";
  throw new Error(
    `Unrecognized cabin category "${cabinCategory}" for Norwegian — expected Interior, Ocean View, Balcony, Mini-Suite, or Suite`
  );
}

interface NclItinerary {
  code: string;
  ship: { code: string };
}

async function listItineraryCodes(shipCode: string): Promise<string[]> {
  const params = new URLSearchParams({
    filterConfig: "search-filters-configuration",
    limit: "100",
    offset: "0",
    ship: shipCode,
  });
  const res = await fetch(`${SEARCH_API}?${params.toString()}`);
  if (!res.ok) {
    throw new Error(`Norwegian itinerary search failed for ship ${shipCode}: HTTP ${res.status}`);
  }
  const data = (await res.json()) as { itineraries: NclItinerary[] };
  return [...new Set(data.itineraries.map((i) => i.code))];
}

interface NclStateroomPricing {
  code: string;
  combinedPrice: number | null;
}

interface NclSailingResult {
  sailing: { sailStartDate: string }; // ISO datetime
  stateroomTypesPricing: NclStateroomPricing[];
}

async function findSailingPrice(
  itineraryCode: string,
  targetSailDate: string,
  stateroomCode: string
): Promise<number | null> {
  const res = await fetch(`${SAILINGS_API}/${itineraryCode}/sailings?numberOfGuests=2`);
  if (!res.ok) {
    throw new Error(`Norwegian sailings lookup failed for itinerary ${itineraryCode}: HTTP ${res.status}`);
  }
  const data = (await res.json()) as { results: NclSailingResult[] };

  for (const result of data.results) {
    if (result.sailing.sailStartDate.slice(0, 10) !== targetSailDate) continue;
    const stateroom = result.stateroomTypesPricing.find((s) => s.code === stateroomCode);
    return stateroom?.combinedPrice ?? null;
  }
  return null; // this itinerary doesn't sail on the target date
}

export const norwegian: LineScraper = async (cruise) => {
  const shipCode = toShipCode(cruise.ship);
  const stateroomCode = toStateroomCode(cruise.cabinCategory);
  const itineraryCodes = await listItineraryCodes(shipCode);

  if (itineraryCodes.length === 0) {
    throw new Error(`No Norwegian itineraries found for ship "${cruise.ship}" (code ${shipCode})`);
  }

  for (const itineraryCode of itineraryCodes) {
    const price = await findSailingPrice(itineraryCode, cruise.sailDate, stateroomCode);
    if (price !== null) {
      return { fare: price, currency: cruise.currency, source: "norwegian.vacationBuilder" };
    }
  }

  throw new Error(
    `No Norwegian sailing found for ship ${cruise.ship} (${shipCode}) departing ${cruise.sailDate} ` +
      `across ${itineraryCodes.length} known itineraries for this ship.`
  );
};
