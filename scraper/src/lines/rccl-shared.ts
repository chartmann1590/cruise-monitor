import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

/**
 * Shared helpers for Royal Caribbean and Celebrity Cruises — same corporate
 * family (Royal Caribbean Group), same backend API family, per
 * https://github.com/jdeath/CheckRoyalCaribbeanPrice.
 *
 * VERIFIED LIVE on 2026-09-07 against BOTH Royal Caribbean and Celebrity:
 *   - `aws-prd.api.rccl.com`'s ship/voyage metadata endpoints cover the
 *     whole RCCL Group fleet, not just Royal Caribbean — despite the
 *     "royal" path segment, `/en/royal/web/v2/ships` returns ships with
 *     `"brand":"C"` (Celebrity) alongside `"brand":"R"` (Royal Caribbean),
 *     and `/en/royal/web/v3/ships/{shipCode}/voyages` works unchanged for
 *     a Celebrity ship code (confirmed with "EC" / Celebrity Eclipse). No
 *     auth, no bot protection, works with a plain server-side HTTP
 *     request for both brands.
 *   - The real "packageCode" a sailing uses on the pricing pages is
 *     `{shipCode}{voyageCode}` (e.g. "AL" + "08D140" -> "AL08D140" for
 *     Royal Caribbean; "EC" + "12D054" -> "EC12D054" for Celebrity), NOT
 *     the bare voyageCode from the metadata API. Confirmed for both
 *     brands by finding a real itinerary page (via web search) and
 *     reading its embedded packageCode, then confirming the same code
 *     works against the pricing endpoints below.
 *   - The pricing pages ARE behind Akamai Bot Manager on both
 *     royalcaribbean.com and celebritycruises.com: a plain HTTP request
 *     gets a hard 403, and a vanilla headless-Chromium request gets a
 *     soft block (served a generic maintenance page). curl_cffi's
 *     `impersonate="chrome"` (TLS/JA3 fingerprint spoofing) gets through
 *     cleanly on both. There's no equivalent for Node, so that one step
 *     is isolated in `scraper/python/fetch_room_pricing.py` and invoked
 *     here via subprocess. See that file's docstring for the full
 *     endpoint detail and a worked example.
 *   - Confirmed end-to-end against real future sailings:
 *       Royal Caribbean (Allure of the Seas, 2026-10-03, "AL08D140"):
 *       Interior from $1,742.98, Ocean View from $2,323.98, Balcony from
 *       $2,637.98, Suite from $15,470.44.
 *       Celebrity (Celebrity Eclipse, 2027-04-11, "EC12D054"): Inside
 *       from $2,904.88, Ocean View from $3,522.88, Veranda from
 *       $4,876.88, Concierge Class from $5,091.88, AquaClass from
 *       $7,676.88, The Retreat from $16,546.88.
 *     (Both exclude "GTY" guarantee-cabin codes, which have different
 *     pricing/eligibility mechanics.)
 *
 * Note: celebritycruises.com also exposes a separate, older AEM-based
 * search API (`/prd/cruises`, discovered while investigating this) with
 * its own anonymous-JWT auth flow (`POST /prd/token`, then
 * `Authorization: token <jwt>`). It was NOT needed for the working
 * solution above — the Next.js itinerary pages + `room-selection/
 * type-and-subtype` pattern (identical to Royal Caribbean's) covers our
 * use case — but it returned `hits:0` for every filter combination tried
 * and was abandoned in favor of the approach that worked. Not used by any
 * code here; noted in case Phase 3 ever needs a broad "search all
 * sailings" capability this repo doesn't currently have.
 */

const APPKEY_WEB = "hyNNqIPHHzaLzVpcICPdAdbFV8yvTsAm";
const USER_AGENT_WEB =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0";

function metadataHeaders(): Record<string, string> {
  return {
    AppKey: APPKEY_WEB,
    Accept: "application/json",
    "User-Agent": USER_AGENT_WEB,
  };
}

export interface RcclShip {
  shipCode: string;
  name: string;
}

export interface RcclVoyage {
  voyageCode: string;
  sailDate: string; // YYYYMMDD
  sailEndDate: string;
  duration: number;
  voyageDescription: string;
}

/** Looks up a ship's shipCode by (case-insensitive, exact) name. */
export async function resolveShipCode(shipName: string): Promise<RcclShip> {
  const res = await fetch("https://aws-prd.api.rccl.com/en/royal/web/v2/ships?sort=name", {
    headers: metadataHeaders(),
  });
  if (!res.ok) {
    throw new Error(`RCCL ships lookup failed: HTTP ${res.status}`);
  }
  const body = (await res.json()) as { payload?: { ships?: Array<{ shipCode: string; name: string }> } };
  const ships = body.payload?.ships ?? [];
  const match = ships.find((s) => s.name.toLowerCase() === shipName.toLowerCase());
  if (!match) {
    throw new Error(`No RCCL ship found matching "${shipName}"`);
  }
  return { shipCode: match.shipCode, name: match.name };
}

/** Looks up the voyage (and its voyageCode) for a ship sailing on a given ISO date. */
export async function resolveVoyage(shipCode: string, sailDateIso: string): Promise<RcclVoyage> {
  const res = await fetch(`https://aws-prd.api.rccl.com/en/royal/web/v3/ships/${shipCode}/voyages`, {
    headers: metadataHeaders(),
  });
  if (!res.ok) {
    throw new Error(`RCCL voyages lookup failed for ship ${shipCode}: HTTP ${res.status}`);
  }
  const body = (await res.json()) as { payload?: { voyages?: RcclVoyage[] } };
  const voyages = body.payload?.voyages ?? [];
  const targetSailDate = sailDateIso.replaceAll("-", ""); // "2026-09-05" -> "20260905"
  const match = voyages.find((v) => v.sailDate === targetSailDate);
  if (!match) {
    throw new Error(`No voyage found for ship ${shipCode} sailing on ${sailDateIso}`);
  }
  return match;
}

/** The real packageCode used by the pricing pages: shipCode + voyageCode. */
export function buildPackageCode(shipCode: string, voyageCode: string): string {
  return `${shipCode}${voyageCode}`;
}

/**
 * Maps our app's free-form cabin category names to RCCL's internal
 * cabinClassType codes. Royal Caribbean has 4 tiers (Interior/Outside/
 * Balcony/Suite -> DELUXE); Celebrity has 6, confirmed live against a real
 * Celebrity Eclipse sailing on 2026-09-07: Inside(INTERIOR), Ocean View
 * (OUTSIDE), Veranda(BALCONY), Concierge Class(CONCIERGE), AquaClass(AQUA),
 * The Retreat(DELUXE). Both brands use the same codes for the tiers they
 * share, so one mapping works for both.
 */
export function toCabinClassType(cabinCategory: string): string {
  const normalized = cabinCategory.trim().toLowerCase();
  if (["interior", "inside"].includes(normalized)) return "INTERIOR";
  if (["ocean view", "oceanview", "outside", "ocean-view"].includes(normalized)) return "OUTSIDE";
  if (["balcony", "veranda"].includes(normalized)) return "BALCONY";
  if (["concierge", "concierge class"].includes(normalized)) return "CONCIERGE";
  if (["aqua", "aquaclass", "aqua class"].includes(normalized)) return "AQUA";
  if (["suite", "deluxe", "the retreat", "retreat"].includes(normalized)) return "DELUXE";
  throw new Error(
    `Unrecognized cabin category "${cabinCategory}" — expected one of Interior, Ocean View, Balcony, ` +
      `Concierge, Aqua, Suite`
  );
}

export interface RoomPricingResult {
  fare: number;
  currency: string;
  subtypeCode: string;
  categoryCode: string;
  source: string;
}

const here = dirname(fileURLToPath(import.meta.url));
// scraper/dist/lines/rccl-shared.js -> ../../../python/fetch_room_pricing.py
const pythonScript = resolve(here, "../../python/fetch_room_pricing.py");
const PYTHON_BIN = process.env.PYTHON_BIN ?? (process.platform === "win32" ? "py" : "python3");

/**
 * Fetches the current lowest fare for a cabin category on a specific
 * sailing. Shells out to a Python helper because the pricing endpoint is
 * behind Akamai Bot Manager and only a real Chrome TLS fingerprint
 * (curl_cffi's `impersonate="chrome"`) gets past it — see the module doc
 * comment above and scraper/python/fetch_room_pricing.py.
 */
export async function fetchRoomPricing(args: {
  domain: "www.royalcaribbean.com" | "www.celebritycruises.com";
  packageCode: string;
  sailDateIso: string;
  shipCode: string;
  cabinCategory: string;
  currency: string;
  isGuarantee?: boolean;
}): Promise<RoomPricingResult> {
  const request = {
    domain: args.domain,
    packageCode: args.packageCode,
    sailDate: args.sailDateIso,
    shipCode: args.shipCode,
    cabinClassType: toCabinClassType(args.cabinCategory),
    currency: args.currency,
    numAdults: 2,
    numChildren: 0,
    isGuarantee: args.isGuarantee ?? false,
  };

  const result = await new Promise<string>((resolvePromise, reject) => {
    const proc = spawn(PYTHON_BIN, process.platform === "win32" ? ["-3", pythonScript] : [pythonScript]);
    let stdout = "";
    let stderr = "";
    proc.stdout.on("data", (chunk) => (stdout += chunk));
    proc.stderr.on("data", (chunk) => (stderr += chunk));
    proc.on("error", reject);
    proc.on("close", (code) => {
      if (code === 0) {
        resolvePromise(stdout);
      } else {
        reject(new Error(`fetch_room_pricing.py exited ${code}: ${stdout.trim() || stderr.trim()}`));
      }
    });
    proc.stdin.write(JSON.stringify(request));
    proc.stdin.end();
  });

  const parsed = JSON.parse(result) as RoomPricingResult | { error: string };
  if ("error" in parsed) {
    throw new Error(`fetch_room_pricing.py: ${parsed.error}`);
  }
  return parsed;
}
