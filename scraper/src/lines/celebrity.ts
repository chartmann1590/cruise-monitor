import type { LineScraper } from "../types.js";
import { resolveShipCode, resolveVoyage, buildPackageCode, fetchRoomPricing } from "./rccl-shared.js";

/**
 * Fully implemented and verified live against a real future sailing
 * (Celebrity Eclipse, "Ultimate Southern Caribbean", 2027-04-11, package
 * "EC12D054") on 2026-09-07. Same pipeline as royalCaribbean.ts — Celebrity
 * is the same corporate family (Royal Caribbean Group) and shares the same
 * ship/voyage metadata API and pricing-page shape. See rccl-shared.ts's
 * module doc comment for the full endpoint detail and verified pricing.
 */
export const celebrity: LineScraper = async (cruise) => {
  const ship = await resolveShipCode(cruise.ship);
  const voyage = await resolveVoyage(ship.shipCode, cruise.sailDate);
  const packageCode = buildPackageCode(ship.shipCode, voyage.voyageCode);

  const pricing = await fetchRoomPricing({
    domain: "www.celebritycruises.com",
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
    source: cruise.isGuarantee ? "celebrity.typeAndSubtype.guarantee" : "celebrity.typeAndSubtype",
  };
};
