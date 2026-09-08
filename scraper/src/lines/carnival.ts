import type { LineScraper } from "../types.js";

/**
 * VERIFIED LIVE on 2026-09-08. Unlike Royal Caribbean/Celebrity, Carnival's
 * pricing API is NOT behind Akamai (or any) bot protection — plain Node
 * `fetch()` works with no TLS impersonation, no special headers, no
 * Python subprocess needed. Much simpler pipeline than rccl-shared.ts.
 *
 * Ship code resolution: fetch the ship's marketing page
 * (carnival.com/en/Cruise-Ships/{slug}) and read the hidden
 * `id="ShipCode"` input's value. Confirmed for "carnival-horizon" -> "HZ"
 * and "carnival-vista" -> "VS". The slug is just "carnival-" + the ship
 * name (minus any "Carnival " prefix) lowercased and hyphenated, matching
 * every entry in carnival.com/sitemap-cruise-ships_en.xml.
 *
 * Pricing: `GET https://www.carnival.com/cruisesearch/api/search
 * ?shipcode={code}&pagesize=50&async=true&currency=USD&locality=1&client=cruisesearch`
 * returns EVERY future itinerary/sailing for that ship in one request (in
 * testing: 18 itineraries, 84 sailings, spanning 18 months) — no
 * pagination or date-range filtering needed for our use case. Each
 * sailing has a `rooms` object keyed exactly `interior`/`oceanview`/
 * `balcony`/`suite`, each with a live `price` (or `soldOut: true`).
 */

const SHIP_PAGE_BASE = "https://www.carnival.com/en/Cruise-Ships/";
const SEARCH_API = "https://www.carnival.com/cruisesearch/api/search";

interface CarnivalRoom {
  price: number;
  soldOut: boolean;
}

interface CarnivalSailing {
  departureDate: string; // ISO, e.g. "2026-09-13T00:00:00.000Z"
  rooms: {
    interior?: CarnivalRoom;
    oceanview?: CarnivalRoom;
    balcony?: CarnivalRoom;
    suite?: CarnivalRoom;
  };
}

interface CarnivalItinerary {
  code: string;
  shipCode: string;
  sailings: CarnivalSailing[];
}

interface CarnivalSearchResponse {
  results: {
    itineraries: CarnivalItinerary[];
    totalResults: number;
    lastPage: number;
  };
}

function toShipSlug(shipName: string): string {
  const withoutBrand = shipName.replace(/^carnival\s+/i, "").trim();
  return `carnival-${withoutBrand.toLowerCase().replace(/\s+/g, "-")}`;
}

async function resolveShipCode(shipName: string): Promise<string> {
  const url = `${SHIP_PAGE_BASE}${toShipSlug(shipName)}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Carnival ship page lookup failed for "${shipName}" (${url}): HTTP ${res.status}`);
  }
  const html = await res.text();
  const match = html.match(/id="ShipCode"[^>]*value="([A-Z0-9]+)"/);
  if (!match) {
    throw new Error(`Could not find ShipCode on Carnival ship page for "${shipName}" (${url})`);
  }
  return match[1]!;
}

function toRoomKey(cabinCategory: string): keyof CarnivalSailing["rooms"] {
  const normalized = cabinCategory.trim().toLowerCase();
  if (["interior", "inside"].includes(normalized)) return "interior";
  if (["ocean view", "oceanview", "outside", "ocean-view"].includes(normalized)) return "oceanview";
  if (["balcony", "veranda"].includes(normalized)) return "balcony";
  if (["suite", "deluxe"].includes(normalized)) return "suite";
  throw new Error(
    `Unrecognized cabin category "${cabinCategory}" for Carnival — expected Interior, Ocean View, Balcony, or Suite`
  );
}

async function fetchAllSailings(shipCode: string, currency: string): Promise<CarnivalItinerary[]> {
  const params = new URLSearchParams({
    pageNumber: "1",
    numadults: "2",
    shipcode: shipCode,
    pagesize: "50",
    sort: "fromprice",
    async: "true",
    currency,
    locality: "1",
    client: "cruisesearch",
  });
  const res = await fetch(`${SEARCH_API}?${params.toString()}`);
  if (!res.ok) {
    throw new Error(`Carnival search API failed for ship ${shipCode}: HTTP ${res.status}`);
  }
  const data = (await res.json()) as CarnivalSearchResponse;
  return data.results.itineraries;
}

export const carnival: LineScraper = async (cruise) => {
  const shipCode = await resolveShipCode(cruise.ship);
  const itineraries = await fetchAllSailings(shipCode, cruise.currency);

  const roomKey = toRoomKey(cruise.cabinCategory);
  const targetDate = cruise.sailDate; // "YYYY-MM-DD"

  for (const itinerary of itineraries) {
    for (const sailing of itinerary.sailings) {
      if (sailing.departureDate.slice(0, 10) !== targetDate) continue;
      const room = sailing.rooms[roomKey];
      if (!room) {
        throw new Error(`No "${roomKey}" room data for Carnival sailing on ${targetDate}`);
      }
      if (room.soldOut) {
        throw new Error(`"${roomKey}" is sold out for this Carnival sailing (${targetDate})`);
      }
      return { fare: room.price, currency: cruise.currency, source: "carnival.cruisesearchApi" };
    }
  }

  throw new Error(
    `No Carnival sailing found for ship ${cruise.ship} (${shipCode}) departing ${targetDate} — ` +
      `it may be more than ~18 months out, sold out entirely, or the itinerary no longer runs.`
  );
};
