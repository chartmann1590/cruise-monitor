# Firestore schema

Free Spark tier. All collections are scoped per-user via a `userId` field and enforced by security rules (see Phase 4). `line` values match the `id` field in `docs/cruise-line-policies.json` (`royal_caribbean`, `carnival`, `princess`, `celebrity`, `norwegian`).

## `users/{userId}`

Mirrors Firebase Auth; holds app-specific profile data.

| Field | Type | Notes |
|---|---|---|
| `email` | string | copied from Auth at sign-up |
| `createdAt` | timestamp | |
| `fcmTokens` | array\<string\> | device push tokens; a user may have more than one device |

## `trackedCruises/{cruiseId}`

One doc per cruise a user is watching.

| Field | Type | Notes |
|---|---|---|
| `userId` | string | owner, indexed |
| `line` | string | one of the 5 line ids in `cruise-line-policies.json` |
| `ship` | string | ship name, as it appears on the line's booking flow |
| `sailDate` | string (ISO date) | |
| `cabinCategory` | string | e.g. "Interior", "Balcony", "Suite" — must match the line's category naming for scraper lookups to work |
| `isGuarantee` | boolean | true if this is a Guarantee (GTY) stateroom (no exact room picked) — pricing/eligibility works differently for these. Currently only affects Royal Caribbean/Celebrity's scraper (`scraper/src/lines/rccl-shared.ts`), which tracks the GTY-coded rate specifically when true, instead of excluding it. Carnival/Princess/Norwegian don't yet distinguish guarantee rates — the flag is accepted but has no effect for those lines. Defaults to `false`. |
| `farePaid` | number | cruise fare only, no taxes/fees (matches CruiseSignal's "fare only" comparison approach) |
| `currency` | string | ISO 4217, e.g. "USD" |
| `finalPaymentDate` | string (ISO date) | used to compute which policy window (pre/post final payment) currently applies |
| `bookingChannel` | string | e.g. "direct", "travel_agent" — some lines' policies exclude travel-agent bookings |
| `active` | boolean | scraper skips inactive/archived cruises |
| `createdAt` | timestamp | |

## `trackedCruises/{cruiseId}/priceSnapshots/{snapshotId}` (subcollection)

Append-only price history for one tracked cruise.

| Field | Type | Notes |
|---|---|---|
| `timestamp` | timestamp | when the scraper captured this fare |
| `fare` | number | fare only, same currency as the parent `trackedCruises` doc |
| `source` | string | which scraper module produced this (e.g. `"royalCaribbean.jsonEndpoint"`, `"celebrity.playwrightDom"`) — useful for debugging when a line's scraping method changes |

## `alerts/{alertId}`

Written when a price drop is detected inside an applicable policy window.

| Field | Type | Notes |
|---|---|---|
| `userId` | string | denormalized from the tracked cruise, for security-rule scoping and direct querying |
| `line` | string | denormalized from the tracked cruise, so the app can show that line's claim instructions (phone number, script) without a join |
| `cruiseId` | string | reference to `trackedCruises/{cruiseId}` |
| `policyId` | string | which policy from `cruise-line-policies.json` applies (matches a `policies[].name` under the cruise's `line`) |
| `currentFare` | number | fare observed at detection time |
| `farePaid` | number | denormalized at alert-creation time, so historical alerts stay accurate even if the user edits `trackedCruises.farePaid` later |
| `dropAmount` | number | `farePaid - currentFare` |
| `detectedAt` | timestamp | |
| `claimed` | boolean | user marks true once they've filed the claim; default false |
| `claimedAt` | timestamp \| null | |

## Indexes

- `trackedCruises`: composite index on `(userId, active)` for the scraper's "give me all active cruises for line X" query — actually scraper queries by `(line, active)` across all users, so index `(line, active)`.
- `alerts`: composite index on `(userId, claimed)` for the app's "unclaimed alerts" view.

## Security rules (implemented in Phase 4)

- `trackedCruises`, `alerts`: read/write allowed only where `request.auth.uid == resource.data.userId`.
- `priceSnapshots` subcollection: read allowed only if the parent `trackedCruises` doc's `userId` matches; writes come only from the scraper's service account (Admin SDK bypasses rules), never from the client.
- `users/{userId}`: read/write allowed only where `request.auth.uid == userId`.
