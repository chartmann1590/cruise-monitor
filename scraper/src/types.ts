export interface TrackedCruise {
  id: string;
  userId: string;
  line: string;
  ship: string;
  sailDate: string;
  cabinCategory: string;
  farePaid: number;
  currency: string;
  finalPaymentDate: string;
  bookingChannel: string;
  active: boolean;
}

export interface FareLookup {
  fare: number;
  currency: string;
  source: string;
}

/** One module per cruise line implements this. See src/lines/index.ts for the registry. */
export type LineScraper = (cruise: TrackedCruise) => Promise<FareLookup>;

export interface CruisePolicy {
  name: string;
  window: string;
  outcome: string;
  outcomeDetail?: string;
  notes?: string;
}

export interface CruiseLinePolicy {
  id: string;
  displayName: string;
  monitoring: string;
  policies: CruisePolicy[];
  exclusions: string[];
  eligibility: string;
  claimChannel: string;
}
