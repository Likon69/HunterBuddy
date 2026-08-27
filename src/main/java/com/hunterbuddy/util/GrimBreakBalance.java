package com.hunterbuddy.util;

import java.util.function.ToDoubleFunction;

import net.minecraft.util.math.BlockPos;

/**
 * A copy of Grim's FastBreak, kept in step with the digging packets this client sends.
 *
 * <p>The check runs two balances and flags at a thousand on either one. The delay balance is
 * charged at every start: three hundred milliseconds minus the time since the last finished
 * break, so a start that follows the previous break too closely costs the difference, while one
 * that waits two hundred and seventy-five milliseconds or more repays a tenth of the standing
 * balance instead. The break balance is charged at every finish: the time the block was
 * predicted to need minus the time it actually took, with the same tenth repaid whenever that
 * difference falls under twenty-five milliseconds.
 *
 * <p>Mirroring the first of those is what buys anything. The fixed door it replaces — hold every
 * start until two hundred and eighty milliseconds have passed — pays the worst case on every
 * block, but the balance is a buffer: after a pause it sits at zero, and two or three blocks can
 * go back to back before a millisecond is owed. That is the shape of stash hunting, where blocks
 * come in ones and twos with flying in between, and it is precisely what a fixed door throws
 * away. Over a long run of blocks the two agree, because the decay only pays out a tenth and
 * only from two hundred and seventy-five milliseconds up: the sustained rate is one block per
 * three hundred milliseconds either way, and no mirror can change that.
 *
 * <p>The second balance is a readout, not a door, and the difference matters. Waiting does not
 * repay it — only another finish does — so there is no delay this class could ask for that would
 * bring it down. Left alone it settles at nine times the predicted time of whatever is being
 * mined, which is under the flag for a block that breaks in a tick or two and over it for
 * anything harder. That number is worth watching, and {@link #breakBalance()} is what to watch.
 *
 * <p>Times are read from the packets leaving this client; Grim reads them as they arrive. The
 * two differ by jitter, not by ping, which is why the budget passed to {@link #canStart} should
 * sit well under the thousand Grim actually flags at.
 */
public final class GrimBreakBalance {
    /** Where Grim flags, on either balance. */
    public static final double FLAG = 1000.0;

    /** Time since the last finish, from which a start repays instead of costing. */
    private static final double DECAY_DELAY = 275.0;

    /** The delay a start is measured against: it costs what it falls short of this. */
    private static final double FULL_DELAY = 300.0;

    /** How close a finish has to come to its prediction to repay instead of cost. */
    private static final double CLOSE_ENOUGH = 25.0;

    /**
     * Grim clamps both balances to plus or minus the greater of a thousand and the player's
     * transaction ping. A thousand is the floor, and taking the floor keeps the mirror on the
     * cautious side of the real thing.
     */
    private static final double CLAMP = 1000.0;

    /**
     * Damage per tick the server would compute for a block, as this client's own mining speed
     * calculation gives it. Grim reads the same numbers off its own copy of the world.
     */
    private final ToDoubleFunction<BlockPos> damageAt;

    /** The block Grim believes is being broken: the position of the last start. */
    private BlockPos target;

    /** Position of the last finish, which Grim has already turned to air in its own world. */
    private BlockPos lastFinished;

    /** Whether a finish has landed on {@link #target} since the start that set it. */
    private boolean targetBroken;

    private double maximumBlockDamage;
    private long startBreak;
    private long lastFinishBreak;
    private double breakBalance;
    private double delayBalance;

    /** What the last finish was measured at, kept for the readout. */
    private double lastPredicted;
    private double lastReal;
    private double lastDiff;

    public GrimBreakBalance(ToDoubleFunction<BlockPos> damageAt) {
        this.damageAt = damageAt;
    }

    /** Everything Grim would forget when the player is no longer the same session. */
    public void reset() {
        target = null;
        lastFinished = null;
        targetBroken = false;
        maximumBlockDamage = 0.0;
        startBreak = 0L;
        lastFinishBreak = 0L;
        breakBalance = 0.0;
        delayBalance = 0.0;
        lastPredicted = 0.0;
        lastReal = 0.0;
        lastDiff = 0.0;
    }

    public double delayBalance() {
        return delayBalance;
    }

    /**
     * The balance no delay can pay off.
     *
     * <p>It rises by the whole predicted time of a block whenever a finish is sent before that
     * block could possibly have broken, and falls by a tenth on every finish that arrives when
     * Grim already holds the position as air. Over the flag it stays over the flag, because the
     * clamp pins it at a thousand and every further finish is measured from there.
     */
    public double breakBalance() {
        return breakBalance;
    }

    /** Time the last finished block was predicted to need, in milliseconds. */
    public double lastPredicted() {
        return lastPredicted;
    }

    /** Time it was measured to have taken, from the start the check timed it from. */
    public double lastReal() {
        return lastReal;
    }

    /** What that finish cost, or repaid when it came out under twenty-five. */
    public double lastDiff() {
        return lastDiff;
    }

    /** START_DESTROY_BLOCK left the client. */
    public void onStart(BlockPos pos, long now) {
        // Grim credits fifty milliseconds to the very first start of a session and nothing after
        // that, so this only ever fires once.
        startBreak = now - (target == null ? 50L : 0L);

        double breakDelay = now - lastFinishBreak;

        if (breakDelay >= DECAY_DELAY) {
            delayBalance *= 0.9;
        } else {
            delayBalance += FULL_DELAY - breakDelay;
        }

        target = pos.toImmutable();
        targetBroken = false;

        // The damage is read from Grim's world, not ours, and the two disagree on exactly one
        // spot: a position it has already been told broke is air to it while the server has yet
        // to tell us anything. Air has no hardness, so the damage there is unbounded and the
        // block is predicted to take no time at all -- which is the whole point of sending a
        // finish before the start, and the reason that trick is worth anything.
        maximumBlockDamage = pos.equals(lastFinished) ? Double.POSITIVE_INFINITY : damageAt.applyAsDouble(pos);

        clamp();
    }

    /** STOP_DESTROY_BLOCK left the client: a finish, whatever this client calls it. */
    public void onFinish(BlockPos pos, long now) {
        lastFinished = pos.toImmutable();

        // A finish with nothing started before it is not measured at all.
        if (target == null) return;

        double predicted = maximumBlockDamage <= 0.0
            ? Double.POSITIVE_INFINITY
            : Math.ceil(1.0 / maximumBlockDamage) * 50.0;
        double real = now - startBreak;
        double diff = predicted - real;

        lastPredicted = predicted;
        lastReal = real;
        lastDiff = diff;

        clamp();

        if (diff < CLOSE_ENOUGH) {
            breakBalance *= 0.9;
        } else {
            breakBalance += diff;
        }

        if (pos.equals(target)) targetBroken = true;

        // Grim sets both, and the start time matters as much as the other: the next finish on
        // this same position is measured from here, not from the start that opened the block.
        lastFinishBreak = startBreak = now;

        clamp();
    }

    /**
     * A movement or an animation left the client.
     *
     * <p>Grim samples the block being broken on each of these and keeps the best damage it ever
     * saw, which is how picking up a better tool mid-block is credited -- and how a position it
     * holds as air ends up predicted at no time at all.
     */
    public void onFlying() {
        // Nothing left to learn once it is unbounded, and this runs on every movement
        // packet: past the finish that cleared the spot, that is all of them.
        if (target == null || Double.isInfinite(maximumBlockDamage)) return;

        double damage = targetBroken ? Double.POSITIVE_INFINITY : damageAt.applyAsDouble(target);
        if (damage > maximumBlockDamage) maximumBlockDamage = damage;
    }

    /**
     * Whether a start can leave now without pushing the delay balance past {@code budget}.
     *
     * <p>Past the decay delay the answer is always yes: that branch repays and cannot cost.
     */
    public boolean canStart(double budget, long now) {
        double breakDelay = now - lastFinishBreak;
        if (breakDelay >= DECAY_DELAY) return true;

        return delayBalance + (FULL_DELAY - breakDelay) <= budget;
    }

    /**
     * Milliseconds still to wait before {@link #canStart} turns true, zero when it already is.
     *
     * <p>Never more than the decay delay, because waiting that long is the branch that repays.
     */
    public long startWait(double budget, long now) {
        if (canStart(budget, now)) return 0L;

        // The delay the balance can afford, capped at the one that stops costing altogether.
        double affordable = Math.min(DECAY_DELAY, FULL_DELAY - (budget - delayBalance));
        return (long) Math.max(0.0, Math.ceil(affordable - (now - lastFinishBreak)));
    }

    /**
     * What a finish sent right now, on the block started this instant, would cost.
     *
     * <p>This is the burst as the module sends it: a start and a finish in the same breath, so
     * the block is measured as having taken no time whatsoever and the whole of its predicted
     * time falls due.
     */
    public double burstCost(double damage) {
        if (damage <= 0.0) return Double.POSITIVE_INFINITY;
        return Math.ceil(1.0 / damage) * 50.0;
    }

    /** Whether that burst would take the break balance over the flag. */
    public boolean burstWouldFlag(double damage) {
        return breakBalance + burstCost(damage) > FLAG;
    }

    private void clamp() {
        delayBalance = Math.max(-CLAMP, Math.min(CLAMP, delayBalance));
        breakBalance = Math.max(-CLAMP, Math.min(CLAMP, breakBalance));
    }
}
