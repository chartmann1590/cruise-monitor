#!/usr/bin/env python3
"""
Fetches live per-cabin-category pricing for a Royal Caribbean Group sailing
(Royal Caribbean International or Celebrity Cruises).

WHY THIS IS PYTHON, NOT NODE: the pricing pages are behind Akamai Bot
Manager, which fingerprints the TLS/JA3 handshake. Plain HTTP requests
(curl, Node's fetch/undici) get a hard 403. A vanilla headless-Chromium
request (Playwright) gets a *soft* block (served a generic maintenance
page instead of real content). curl_cffi's `impersonate="chrome"` mode
replicates a real Chrome TLS fingerprint and gets through cleanly —
verified live against royalcaribbean.com on 2026-09-07. There's no
equivalent library for Node, so this one step is isolated in Python and
called from the TypeScript scraper via a subprocess
(see scraper/src/lines/rccl-shared.ts).

Endpoint and request shape reverse-engineered from
https://github.com/jdeath/CheckRoyalCaribbeanPrice (MIT license) and
confirmed live: the real `packageCode` a sailing uses is
{shipCode}{voyageCode} (e.g. ship "AL" + voyage "08D140" -> "AL08D140"),
NOT the bare voyageCode from the public ships/voyages metadata API.

Usage: reads a JSON request object from stdin, prints a JSON result to
stdout, exits 0 on success or 1 with {"error": "..."} on failure.

Input shape:
{
  "domain": "www.royalcaribbean.com" | "www.celebritycruises.com",
  "packageCode": "AL08D140",
  "sailDate": "2026-10-03",           # YYYY-MM-DD
  "shipCode": "AL",
  "cabinClassType": "INTERIOR" | "OUTSIDE" | "BALCONY" | "DELUXE",
  "currency": "USD",
  "numAdults": 2,
  "numChildren": 0
}

Output shape (success):
{
  "fare": 1742.98,
  "currency": "USD",
  "cabinClassType": "INTERIOR",
  "subtypeCode": "ZI",
  "categoryCode": "ZI",
  "source": "rccl.typeAndSubtype"
}
"""
import json
import sys

from curl_cffi import requests

APPKEY_WEB = "hyNNqIPHHzaLzVpcICPdAdbFV8yvTsAm"
USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0"

# Guarantee-cabin codes: the passenger doesn't pick an exact room, and pricing/
# eligibility for price-drop policies works differently for these. Excluded
# when computing "the current lowest fare" for a cabin category, matching
# the exclusion list in the reference project.
GTY_CODES = {"GTY", "XB", "YO", "ZI", "WS", "XN", "CB"}


def extract_json_array(text: str, key: str):
    """Bracket-counting extraction of a JSON array embedded in a larger
    text/HTML blob, e.g. `"rooms":[...]`. Mirrors the approach in
    jdeath/CheckRoyalCaribbeanPrice, needed because the RSC response isn't
    valid JSON on its own (it's a React Server Component text stream with
    JSON fragments embedded in it)."""
    needle = f'"{key}":'
    idx = text.find(needle)
    if idx == -1:
        needle = f'\\"{key}\\":'
        idx = text.find(needle)
        if idx == -1:
            return None
    start = text.find("[", idx)
    if start == -1:
        return None
    depth = 0
    for i in range(start, len(text)):
        c = text[i]
        if c == "[":
            depth += 1
        elif c == "]":
            depth -= 1
            if depth == 0:
                candidate = text[start : i + 1]
                # The RSC stream sometimes double-escapes quotes; try both.
                for attempt in (candidate, candidate.replace('\\"', '"')):
                    try:
                        return json.loads(attempt)
                    except json.JSONDecodeError:
                        continue
                return None
    return None


def fetch_room_pricing(req: dict) -> dict:
    domain = req["domain"]
    package_code = req["packageCode"]
    sail_date = req["sailDate"]
    ship_code = req["shipCode"]
    cabin_class_type = req["cabinClassType"]
    currency = req.get("currency", "USD")
    num_adults = req.get("numAdults", 2)
    num_children = req.get("numChildren", 0)

    headers = {
        "user-agent": USER_AGENT_WEB,
        "accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "accept-language": "en-US,en;q=0.9",
        "Accept": "text/x-component",
        "RSC": "1",
        "AppKey": APPKEY_WEB,
    }
    params = {
        "packageCode": package_code,
        "sailDate": sail_date,
        "country": "USA",
        "selectedCurrencyCode": currency,
        "shipCode": ship_code,
        "cabinClassType": cabin_class_type,
        "roomIndex": "0",
        "r0a": str(num_adults),
        "r0c": str(num_children),
        "r0b": "n",
        "r0r": "n",
        "r0s": "n",
        "r0q": "n",
        "r0t": "n",
        "r0d": cabin_class_type,
        "r0D": "y",
        "rgVisited": "true",
        "r0C": "y",
    }

    url = f"https://{domain}/room-selection/type-and-subtype"
    resp = requests.get(url, params=params, headers=headers, impersonate="chrome", timeout=30)
    if resp.status_code != 200:
        raise RuntimeError(f"HTTP {resp.status_code} from {url}")

    rooms = extract_json_array(resp.text, "rooms")
    if not rooms:
        raise RuntimeError(
            "Could not find a 'rooms' JSON array in the response — the page layout may have "
            "changed, or this sailing/packageCode is no longer valid."
        )

    stateroom_types = rooms[0].get("options", {}).get("stateroomTypes", [])
    matching_type = next(
        (t for t in stateroom_types if t.get("code") == cabin_class_type), None
    )
    if matching_type is None:
        available = [t.get("code") for t in stateroom_types]
        raise RuntimeError(
            f"No stateroom type '{cabin_class_type}' found for this sailing. Available: {available}"
        )

    best = None  # (price, subtypeCode, categoryCode)
    for subtype in matching_type.get("stateroomSubtypes", []):
        code = subtype.get("code")
        category_code = subtype.get("categoryCode")
        if code in GTY_CODES or (code and code.endswith("GTY")):
            continue
        price = subtype.get("pricing", {}).get("invoice", {}).get("total")
        if price is None:
            continue
        if best is None or price < best[0]:
            best = (price, code, category_code)

    if best is None:
        raise RuntimeError(
            f"No priced, non-guarantee subtype found under stateroom type '{cabin_class_type}'."
        )

    fare, subtype_code, category_code = best
    return {
        "fare": fare,
        "currency": currency,
        "cabinClassType": cabin_class_type,
        "subtypeCode": subtype_code,
        "categoryCode": category_code,
        "source": "rccl.typeAndSubtype",
    }


def main() -> int:
    try:
        req = json.loads(sys.stdin.read())
        result = fetch_room_pricing(req)
        print(json.dumps(result))
        return 0
    except Exception as e:  # noqa: BLE001 - report every failure as structured JSON
        print(json.dumps({"error": str(e)}))
        return 1


if __name__ == "__main__":
    sys.exit(main())
