import type { CruisePolicy } from "./types.js";

const MS_PER_HOUR = 60 * 60 * 1000;

/**
 * Decides whether `policy.window` currently applies for a tracked cruise.
 * Handles the window shapes used in docs/cruise-line-policies.json. Windows
 * phrased as "varies" / region-dependent (e.g. Norwegian) always return
 * false here — those need a human to check manually, so cruises on lines
 * with only "varies" policies won't auto-generate alerts until this is
 * extended with real per-region rules.
 */
export function isWithinPolicyWindow(
  policy: CruisePolicy,
  now: Date,
  finalPaymentDate: Date,
  lastPaymentAt: Date | null
): boolean {
  const window = policy.window;

  if (window === "before_final_payment" || window === "before_or_at_final_payment") {
    return now.getTime() <= finalPaymentDate.getTime();
  }

  if (window.startsWith("until:")) {
    // e.g. "until:2_business_days_before_sailing" — not yet modeled precisely;
    // treat conservatively as "before final payment" until sail-date-relative
    // logic is added.
    return now.getTime() <= finalPaymentDate.getTime();
  }

  const hoursAfterPaymentMatch = window.match(/^hours_after_payment:(\d+)$/);
  if (hoursAfterPaymentMatch) {
    if (!lastPaymentAt) return false;
    const hours = Number(hoursAfterPaymentMatch[1]);
    const deadline = lastPaymentAt.getTime() + hours * MS_PER_HOUR;
    return now.getTime() >= lastPaymentAt.getTime() && now.getTime() <= deadline;
  }

  const hoursAfterBookingMatch = window.match(/^hours_after_booking:(\d+)$/);
  if (hoursAfterBookingMatch) {
    // Booking timestamp isn't tracked separately from final payment yet;
    // treat as not-applicable until trackedCruises gains a bookedAt field.
    return false;
  }

  // "varies", "varies_by_region_and_fare_type", or anything unrecognized.
  return false;
}
