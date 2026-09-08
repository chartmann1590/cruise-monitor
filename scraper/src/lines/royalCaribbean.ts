import type { LineScraper } from "../types.js";
import { resolveShipCode, resolveVoyage, buildPackageCode, fetchRoomPricing } from "./rccl-shared.js";

/**
 * Fully implemented and verified live against a real future sailing (Allure
 * of the Seas, 2026-10-03) on 2026-09-07. See rccl-shared.ts's module doc
 * comment for the full endpoint detail — TL;DR: ship/voyage metadata comes
 * from RCCL's unprotected public API, and the actual room pricing comes
 * from the Akamai-protected pricing page via a Python/curl_cffi subprocess
 * (Node can't replicate the TLS fingerprint needed to get past Akamai).
 */
export const royalCaribbean: LineScraper = async (cruise) => {
  const ship = await resolveShipCode(cruise.ship);
  const voyage = await resolveVoyage(ship.shipCode, cruise.sailDate);
  const packageCode = buildPackageCode(ship.shipCode, voyage.voyageCode);

  const pricing = await fetchRoomPricing({
    domain: "www.royalcaribbean.com",
    packageCode,
    sailDateIso: cruise.sailDate,
    shipCode: ship.shipCode,
    cabinCategory: cruise.cabinCategory,
    currency: cruise.currency,
    isGuarantee: cruise.isGuarantee,
  });

  return {
    fare: pricing.fare,
    currency: pricing.currency,
    source: cruise.isGuarantee ? "royalCaribbean.typeAndSubtype.guarantee" : "royalCaribbean.typeAndSubtype",
  };
};
