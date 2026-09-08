import { initializeApp, cert, getApps, type App } from "firebase-admin/app";
import { getFirestore, type Firestore } from "firebase-admin/firestore";

let app: App;

function getApp(): App {
  if (getApps().length === 0) {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!raw) {
      throw new Error(
        "FIREBASE_SERVICE_ACCOUNT env var not set. In GitHub Actions this comes from a repo secret " +
          "holding the Firebase service account JSON (Project Settings > Service Accounts > Generate new private key)."
      );
    }
    app = initializeApp({ credential: cert(JSON.parse(raw)) });
  } else {
    app = getApps()[0]!;
  }
  return app;
}

export function db(): Firestore {
  return getFirestore(getApp());
}
