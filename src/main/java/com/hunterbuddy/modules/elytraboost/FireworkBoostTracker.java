package com.hunterbuddy.modules.elytraboost;

/**
 * Tracks whether one of our own fireworks is currently boosting the player.
 *
 * <p>Marked from {@code FireworkRocketEntityMixin#tick}, which already resolves the
 * rocket's shooter and compares it against the local player. A rocket ticks every
 * tick while it is alive, so a short freshness window is enough to tell whether a
 * boost is currently being applied.
 */
public final class FireworkBoostTracker {
   private static volatile long lastBoostMs = -1L;

   private FireworkBoostTracker() {
   }

   /** Called once per tick per rocket that is boosting the local player. */
   public static void mark() {
      lastBoostMs = System.currentTimeMillis();
   }

   public static boolean isBoosting(int windowMs) {
      long last = lastBoostMs;
      return last != -1L && System.currentTimeMillis() - last <= windowMs;
   }

   public static void reset() {
      lastBoostMs = -1L;
   }
}
