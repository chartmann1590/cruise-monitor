import { getMessaging } from "firebase-admin/messaging";
import { db } from "./firestore.js";

/**
 * Sends a push notification to every device token registered for a user
 * (see docs/firestore-schema.md's `users/{userId}.fcmTokens`). A stale/
 * invalid token is logged and skipped rather than failing the whole send —
 * tokens go stale when a user uninstalls the app or clears data, which is
 * routine, not an error worth failing a price-check run over.
 */
export async function sendPriceDropPush(args: {
  userId: string;
  line: string;
  dropAmount: number;
  currentFare: number;
  policyName: string;
  cruiseId: string;
}): Promise<void> {
  const userDoc = await db().collection("users").doc(args.userId).get();
  const tokens = (userDoc.data()?.fcmTokens as string[] | undefined) ?? [];
  if (tokens.length === 0) {
    return;
  }

  const response = await getMessaging().sendEachForMulticast({
    tokens,
    notification: {
      title: "Price drop found!",
      body: `Your ${args.line.replace(/_/g, " ")} cruise dropped $${args.dropAmount.toFixed(2)} — "${args.policyName}" applies.`,
    },
    data: {
      cruiseId: args.cruiseId,
      currentFare: String(args.currentFare),
      dropAmount: String(args.dropAmount),
    },
  });

  const staleTokens = response.responses
    .map((r, i) => (r.success ? null : tokens[i]))
    .filter((t): t is string => t !== null);

  if (staleTokens.length > 0) {
    console.warn(`[${args.userId}] ${staleTokens.length} stale FCM token(s), pruning`);
    await db()
      .collection("users")
      .doc(args.userId)
      .update({
        fcmTokens: tokens.filter((t) => !staleTokens.includes(t)),
      });
  }
}
