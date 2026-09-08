import { db } from "./firestore.js";
import { getLineScraper } from "./lines/index.js";
import { getLinePolicy } from "./policies.js";
import { isWithinPolicyWindow } from "./policyWindow.js";
import { sendPriceDropPush } from "./fcm.js";
import type { TrackedCruise } from "./types.js";

async function loadActiveCruises(): Promise<TrackedCruise[]> {
  const snap = await db().collection("trackedCruises").where("active", "==", true).get();
  return snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<TrackedCruise, "id">) }));
}

async function processCruise(cruise: TrackedCruise): Promise<void> {
  const scraper = getLineScraper(cruise.line);
  const lookup = await scraper(cruise);

  const cruiseRef = db().collection("trackedCruises").doc(cruise.id);
  await cruiseRef.collection("priceSnapshots").add({
    timestamp: new Date(),
    fare: lookup.fare,
    source: lookup.source,
  });

  if (lookup.fare >= cruise.farePaid) {
    return; // no drop, nothing to alert on
  }

  const linePolicy = getLinePolicy(cruise.line);
  const now = new Date();
  const finalPaymentDate = new Date(cruise.finalPaymentDate);

  const applicablePolicy = linePolicy.policies.find((p) =>
    isWithinPolicyWindow(p, now, finalPaymentDate, /* lastPaymentAt */ null)
  );

  if (!applicablePolicy) {
    console.log(
      `[${cruise.id}] price dropped (${lookup.fare} < ${cruise.farePaid}) but no policy window is currently open for ${cruise.line}`
    );
    return;
  }

  await db().collection("alerts").add({
    userId: cruise.userId,
    cruiseId: cruise.id,
    policyId: applicablePolicy.name,
    currentFare: lookup.fare,
    farePaid: cruise.farePaid,
    dropAmount: cruise.farePaid - lookup.fare,
    detectedAt: now,
    claimed: false,
    claimedAt: null,
  });

  console.log(
    `[${cruise.id}] ALERT: ${cruise.line} dropped from ${cruise.farePaid} to ${lookup.fare} — "${applicablePolicy.name}" applies`
  );

  await sendPriceDropPush({
    userId: cruise.userId,
    line: cruise.line,
    dropAmount: cruise.farePaid - lookup.fare,
    currentFare: lookup.fare,
    policyName: applicablePolicy.name,
    cruiseId: cruise.id,
  });
}

async function main(): Promise<void> {
  const cruises = await loadActiveCruises();
  console.log(`Checking ${cruises.length} active tracked cruise(s)...`);

  const results = await Promise.allSettled(cruises.map(processCruise));

  results.forEach((result, i) => {
    if (result.status === "rejected") {
      console.error(`[${cruises[i]!.id}] failed:`, result.reason);
    }
  });

  const failures = results.filter((r) => r.status === "rejected").length;
  if (failures > 0) {
    console.error(`${failures}/${cruises.length} cruise checks failed.`);
  }
}

main().catch((err) => {
  console.error("Fatal error in price-check run:", err);
  process.exitCode = 1;
});
