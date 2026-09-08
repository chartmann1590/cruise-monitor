import type { LineScraper } from "../types.js";

/**
 * VERIFIED LIVE on 2026-09-08. Princess's booking platform is the "UBE"
 * (Universal Booking Engine, shared across some Carnival Corp brands) at
 * `gw.api.princess.com/pcl-web/internal`. No Akamai/bot protection
 * encountered — plain Node `fetch()` works throughout, no TLS
 * impersonation needed.
 *
 * Auth: every request needs a `pcl-client-id` header (a public client ID
 * embedded in princess.com's own JS bundle, not a secret — confirmed by
 * reading it out of https://www.princess.com/js/global/ube-search-widget/
 * ube-search-widget.js) plus an `appid` header, a JSON string
 * `{agencyId,cruiseLineCode,sessionId,systemId,gdsCookie}` — the
 * `sessionId` is just a client-generated UUID, not tied to a real session.
 *
 * Pipeline:
 *   1. Ship code resolution: GET .../resdb/p1.0/ships -> `{ships:[{id,name}]}`.
 *   2. Voyage resolution: GET .../resdb/p1.0/products?agencyCountry=US&
 *      cruiseType=C&voyageStatus=A&webDisplay=Y&promoFilter=all&light=true
 *      -> `{products:[{id, cruises:[{id, voyage:{ship:{id}, sailDate}}]}]}`.
 *      Returns EVERY future sailing across the whole fleet in one request
 *      (1006 results in testing) — filter client-side for the matching
 *      ship + sailDate to get the real voyage id (NOT the same as the
 *      product id — e.g. product "AWG070" had a specific sailing with
 *      voyage id "2711").
 *   3. Pricing: POST .../caps/pc/pricing/v1/cruises/{voyageId} with a
 *      booking/filters JSON body -> one response containing EVERY cabin
 *      category's price for that sailing, both "BESTFARE" and
 *      "BESTVALUE" fare types. Category ids are prefixed by tier: S
 *      (Suite), M (Mini-Suite), B (Balcony), O (Oceanview), I (Interior)
 *      — e.g. "IF"/"OZ" are guarantee-type (status "G"), still the
 *      genuine lowest price shown in the UI for that tier (unlike RC,
 *      Princess's own UI does NOT exclude guarantee categories from its
 *      displayed "from" price, so we don't exclude them either).
 *
 * Confirmed end-to-end against a real future sailing (Island Princess,
 * 7-night Alaska ex-Whittier, 2027-05-19, voyage 2711, fareType
 * BESTFARE): Interior $674, Oceanview $714, Balcony $1,481, Mini-Suite
 * $1,812, Suite $3,459 — matches the live princess.com UI exactly.
 */

const API_BASE = "https://gw.api.princess.com/pcl-web/internal";
const PCL_CLIENT_ID = "32e7224ac6cc41302f673c5f5d27b4ba";

function baseHeaders(): Record<string, string> {
  return {
    reqsrc: "W",
    appid: JSON.stringify({
      agencyId: "DIRPB",
      cruiseLineCode: "PCL",
      sessionId: crypto.randomUUID(),
      systemId: "PB",
      gdsCookie: "CO=US",
    }),
    "x-pcl-traceapp": "NA=pcl-ube-ui",
    "pcl-client-id": PCL_CLIENT_ID,
    bookingcompany: "PC",
    productcompany: "PC",
    accept: "application/json, text/plain, */*",
    referer: "https://www.princess.com/",
  };
}

interface PrincessShip {
  id: string;
  name: string;
}

async function resolveShipCode(shipName: string): Promise<string> {
  const res = await fetch(`${API_BASE}/resdb/p1.0/ships`, { headers: baseHeaders() });
  if (!res.ok) {
    throw new Error(`Princess ships lookup failed: HTTP ${res.status}`);
  }
  const data = (await res.json()) as { ships: PrincessShip[] };
  const match = data.ships.find((s) => s.name.toLowerCase() === shipName.toLowerCase());
  if (!match) {
    throw new Error(`No Princess ship found matching "${shipName}"`);
  }
  return match.id;
}

interface PrincessCruise {
  id: string; // voyage id, e.g. "2711"
  voyage: { ship: { id: string }; sailDate: string }; // YYYYMMDD
}

interface PrincessProduct {
  id: string;
  cruises: PrincessCruise[];
}

async function resolveVoyageId(shipCode: string, sailDateIso: string): Promise<string> {
  // `light=false` is required here: `light=true` returns a smaller shape
  // (`ships[].sailDates[]`) with no per-sailing voyage id, which the
  // pricing endpoint needs. `light=false` returns `cruises[].id` per
  // sailing at the cost of a much bigger payload (~900KB vs ~150KB).
  const params = new URLSearchParams({
    agencyCountry: "US",
    cruiseType: "C",
    voyageStatus: "A",
    webDisplay: "Y",
    light: "false",
  });
  const res = await fetch(`${API_BASE}/resdb/p1.0/products?${params.toString()}`, { headers: baseHeaders() });
  if (!res.ok) {
    throw new Error(`Princess products lookup failed: HTTP ${res.status}`);
  }
  const data = (await res.json()) as { products: PrincessProduct[] };
  const targetSailDate = sailDateIso.replaceAll("-", "");

  for (const product of data.products) {
    for (const cruise of product.cruises ?? []) {
      if (cruise.voyage.ship.id === shipCode && cruise.voyage.sailDate === targetSailDate) {
        return cruise.id;
      }
    }
  }
  throw new Error(`No Princess voyage found for ship ${shipCode} sailing on ${sailDateIso}`);
}

function toTierPrefix(cabinCategory: string): string {
  const normalized = cabinCategory.trim().toLowerCase();
  if (["interior", "inside"].includes(normalized)) return "I";
  if (["ocean view", "oceanview", "outside", "ocean-view"].includes(normalized)) return "O";
  if (["balcony", "veranda"].includes(normalized)) return "B";
  if (["mini-suite", "mini suite", "minisuite"].includes(normalized)) return "M";
  if (["suite", "deluxe"].includes(normalized)) return "S";
  throw new Error(
    `Unrecognized cabin category "${cabinCategory}" for Princess — expected Interior, Ocean View, Balcony, Mini-Suite, or Suite`
  );
}

interface PrincessCategory {
  id: string;
  averageGuestPrice: number;
}

interface PrincessFare {
  fareType: string;
  categories: PrincessCategory[];
}

interface PrincessPricingResponse {
  products: Array<{
    cruises: Array<{
      id: string;
      pricing: { fareCurrency: string; fares: PrincessFare[] };
    }>;
  }>;
}

async function fetchPricing(voyageId: string, currency: string): Promise<PrincessPricingResponse> {
  const body = {
    booking: {
      currencyCode: currency,
      guests: [
        { country: "US", homeCity: "LAX" },
        { country: "US", homeCity: "LAX" },
      ],
      promos: [],
      couponCodes: [],
      bookingAgency: { id: "DIRPB", bookingCompany: "PC", currency, country: "US" },
    },
    filters: {
      availabilities: ["Y", "G", "B"],
      cruises: [],
      cruiseType: "C",
      meta: "I",
      itinPorts: [],
      subTrades: [],
    },
    leadInBy: "itins",
    retrieveFlags: {
      additionalGuestFare: true,
      averageFare: true,
      brochureFare: true,
      averageBrochureFare: true,
      includeMisc: true,
      fareType: "BESTFARE",
      roundUpFare: true,
      includeTfpe: false,
    },
  };

  const res = await fetch(`${API_BASE}/caps/pc/pricing/v1/cruises/${voyageId}`, {
    method: "POST",
    headers: { ...baseHeaders(), "content-type": "application/json;charset=UTF-8" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(`Princess pricing lookup failed for voyage ${voyageId}: HTTP ${res.status}`);
  }
  return (await res.json()) as PrincessPricingResponse;
}

export const princess: LineScraper = async (cruise) => {
  const shipCode = await resolveShipCode(cruise.ship);
  const voyageId = await resolveVoyageId(shipCode, cruise.sailDate);
  const pricing = await fetchPricing(voyageId, cruise.currency);

  const tierPrefix = toTierPrefix(cruise.cabinCategory);
  const cruiseEntry = pricing.products.flatMap((p) => p.cruises).find((c) => c.id === voyageId);
  if (!cruiseEntry) {
    throw new Error(`Pricing response didn't include voyage ${voyageId}`);
  }
  const bestFare = cruiseEntry.pricing.fares.find((f) => f.fareType === "BESTFARE");
  if (!bestFare) {
    throw new Error(`No BESTFARE pricing found for voyage ${voyageId}`);
  }

  const matching = bestFare.categories.filter((c) => c.id.startsWith(tierPrefix));
  if (matching.length === 0) {
    throw new Error(`No "${cruise.cabinCategory}" (prefix ${tierPrefix}) categories found for voyage ${voyageId}`);
  }
  const lowest = Math.min(...matching.map((c) => c.averageGuestPrice));

  return { fare: lowest, currency: cruiseEntry.pricing.fareCurrency, source: "princess.ubePricing" };
};
