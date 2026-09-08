import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import type { CruiseLinePolicy } from "./types.js";

// docs/cruise-line-policies.json lives at the repo root, two levels above
// this file once compiled to scraper/dist/policies.js.
const here = dirname(fileURLToPath(import.meta.url));
const policiesPath = resolve(here, "../../docs/cruise-line-policies.json");

interface PoliciesFile {
  lines: CruiseLinePolicy[];
}

let cache: PoliciesFile | null = null;

function load(): PoliciesFile {
  if (!cache) {
    cache = JSON.parse(readFileSync(policiesPath, "utf-8")) as PoliciesFile;
  }
  return cache;
}

export function getLinePolicy(lineId: string): CruiseLinePolicy {
  const policy = load().lines.find((l) => l.id === lineId);
  if (!policy) {
    throw new Error(`No policy data for cruise line "${lineId}" in docs/cruise-line-policies.json`);
  }
  return policy;
}

export function allLinePolicies(): CruiseLinePolicy[] {
  return load().lines;
}
