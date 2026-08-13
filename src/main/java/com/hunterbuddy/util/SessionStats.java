package com.hunterbuddy.util;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.ContainerSlotUpdateEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * What this session has cost and turned up, counted once for everyone who wants to show it.
 *
 * <p>One subscriber rather than one per HUD: the counters have to agree, and two elements each
 * watching the same events would drift the moment either one's conditions were tweaked.
 *
 * <p>Everything resets when you join a server. A tally that survives across sessions answers a
 * question nobody asks — what matters is what this trip has used.
 */
public final class SessionStats {
    private static final SessionStats INSTANCE = new SessionStats();

    /** A portal is six to ten blocks; anything within this of the last counted one is the same one. */
    private static final double PORTAL_CLUSTER_RADIUS = 8.0;

    /** How long an ignition of yours stays able to claim a portal that appears. */
    private static final int IGNITION_WINDOW_TICKS = 20;

    /** How far from the block you struck a portal may appear and still be yours. */
    private static final double IGNITION_RADIUS = 6.0;

    /** Beyond this many remembered portals the oldest are forgotten, to bound the scan. */
    private static final int MAX_TRACKED_PORTALS = 256;

    private int rocketsUsed;
    private int xpBottlesUsed;
    private int portalsBuilt;
    private int enderChestsOpened;
    private int shulkerChests;
    private int shulkersSeen;
    private int blocksMined;
    private int stashesFound;
    private double distance;

    /** Ticks spent gliding, so the total is game time rather than wall time. */
    private long flightTicks;

    private int jumps;
    private boolean wasOnGround;

    private long startedAt = System.currentTimeMillis();

    private final List<BlockPos> countedPortals = new ArrayList<>();

    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;

    private String lastWorld = "";

    /** Where and when you last put fire to a block, so an appearing portal can be traced to it. */
    private BlockPos ignitionPos;
    private int ignitionAge = Integer.MAX_VALUE;

    /**
     * Set when a container opens, cleared once its contents have actually been looked at.
     *
     * <p>The items are not there at opening time — the server sends them over the following
     * ticks — so scanning on the open event finds an empty box every time and counts nothing.
     */
    private boolean containerPending;
    private boolean containerDirty;

    /**
     * Most shulkers seen in the container currently open.
     *
     * <p>A running maximum rather than a single reading. The contents arrive over several
     * packets, and scanning once on the first update could catch a half-filled box, count that,
     * mark the container done and never look again — a chest with shulkers in it coming out at
     * zero. Watching until the screen closes and keeping the best count cannot undercount.
     */
    private int containerBest;

    /** Block whose contents are waiting to be looked at. */
    private BlockPos containerPos;

    /**
     * Containers already tallied this session.
     *
     * <p>A chest holds the same shulkers however many times you open it. Counting on every open
     * turned the tally into a count of visits rather than of finds — go back to a stash twice and
     * it claimed you had found it twice.
     */
    private final java.util.Set<BlockPos> countedContainers = new java.util.HashSet<>();

    private SessionStats() {
    }

    public static SessionStats get() {
        return INSTANCE;
    }

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    public int rocketsUsed() {
        return rocketsUsed;
    }

    public int xpBottlesUsed() {
        return xpBottlesUsed;
    }

    public int portalsBuilt() {
        return portalsBuilt;
    }

    public int enderChestsOpened() {
        return enderChestsOpened;
    }

    public int shulkerChests() {
        return shulkerChests;
    }

    public int shulkersSeen() {
        return shulkersSeen;
    }

    public int blocksMined() {
        return blocksMined;
    }

    public int stashesFound() {
        return stashesFound;
    }

    public double distance() {
        return distance;
    }

    public long elapsedSeconds() {
        return (System.currentTimeMillis() - startedAt) / 1000L;
    }

    /**
     * Time spent gliding this session.
     *
     * <p>Counted in ticks rather than off the wall clock, so it measures time the game actually
     * ran: a freeze while chunks load is not flight, and neither is a session left paused.
     */
    public long flightSeconds() {
        return flightTicks / 20L;
    }

    public int jumps() {
        return jumps;
    }

    /** Called by MlepMine when the server confirms a block it was mining is gone. */
    public void onBlockMined() {
        blocksMined++;
    }

    /** Called by StashFinder when a chunk clears its thresholds for the first time. */
    public void onStashFound() {
        stashesFound++;
    }

    public void reset() {
        rocketsUsed = 0;
        xpBottlesUsed = 0;
        portalsBuilt = 0;
        enderChestsOpened = 0;
        shulkerChests = 0;
        shulkersSeen = 0;
        blocksMined = 0;
        stashesFound = 0;
        distance = 0.0;
        flightTicks = 0L;
        jumps = 0;
        wasOnGround = false;
        startedAt = System.currentTimeMillis();
        countedPortals.clear();
        countedContainers.clear();
        containerPos = null;
        containerBest = 0;
        ignitionPos = null;
        ignitionAge = Integer.MAX_VALUE;
        containerPending = false;
        containerDirty = false;
        lastX = Double.NaN;
        lastZ = Double.NaN;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        reset();
    }

    /**
     * Counts a firework or a bottle at the moment you use it.
     *
     * <p>From the outgoing use packet rather than from the spawned entity. The entity route
     * needs to prove the rocket is yours, and on the client the owner of a projectile is not
     * reliably filled in — the fallbacks are proximity guesses that miscount in a crowd. The
     * packet is unambiguous: it is your own client saying it used the thing in your hand.
     */
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractItemC2SPacket packet)) return;
        if (MeteorClient.mc.player == null) return;

        ItemStack stack = MeteorClient.mc.player.getStackInHand(
            packet.getHand() == Hand.OFF_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);

        if (stack.isOf(Items.FIREWORK_ROCKET)) rocketsUsed++;
        else if (stack.isOf(Items.EXPERIENCE_BOTTLE)) xpBottlesUsed++;
    }

    /**
     * Remembers you putting fire to a block, which is what makes a portal yours.
     *
     * <p>The tally is meant to be a record of what you did, and a portal appearing nearby is not
     * that: on a server somebody else lighting one at spawn, or a highway hub where they light
     * all day, would run the counter up for you. Striking the flint is the act; the portal is
     * only its consequence.
     */
    @EventHandler
    private void onInteractBlock(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet)) return;
        if (MeteorClient.mc.player == null) return;

        ItemStack stack = MeteorClient.mc.player.getStackInHand(
            packet.getHand() == Hand.OFF_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);

        if (stack.isOf(Items.FLINT_AND_STEEL) || stack.isOf(Items.FIRE_CHARGE)) {
            onPortalLit(packet.getBlockHitResult().getBlockPos());
        }
    }

    /**
     * Declares that you have just lit something at this position.
     *
     * <p>Public so a module that builds portals without ever sending a click can say so itself,
     * the way MlepMine reports a broken block. Anything going through
     * {@code interactionManager.interactBlock} is picked up on its own and needs no call.
     */
    public void onPortalLit(BlockPos pos) {
        ignitionPos = pos.toImmutable();
        ignitionAge = 0;
    }

    /**
     * Counts a portal once, however many blocks it is made of.
     *
     * <p>Lighting one turns six to ten blocks into portal at once, each of them its own update.
     * Grouping by position is what turns that burst into a single portal; without it the tally
     * reads eight portals for one frame lit.
     */
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (event.newState.getBlock() != Blocks.NETHER_PORTAL) return;
        if (event.oldState.getBlock() == Blocks.NETHER_PORTAL) return;

        // Only a portal traceable to your own strike counts. This replaces the arrival-point
        // exclusion that used to sit here: the portal the server carves at the far end of a
        // crossing has no ignition behind it either, so one rule now covers both that and every
        // portal lit by somebody else.
        if (ignitionAge > IGNITION_WINDOW_TICKS || ignitionPos == null) return;
        if (!ignitionPos.isWithinDistance(event.pos, IGNITION_RADIUS)) return;

        for (BlockPos counted : countedPortals) {
            if (counted.isWithinDistance(event.pos, PORTAL_CLUSTER_RADIUS)) return;
        }

        // Bounded rather than unbounded: the list is walked on every portal block update, and a
        // long session lighting its way across the Nether would otherwise grow it without end.
        if (countedPortals.size() >= MAX_TRACKED_PORTALS) countedPortals.remove(0);

        countedPortals.add(event.pos.toImmutable());
        portalsBuilt++;
        HuntFeed.get().publish(HuntFeed.Type.PORTAL, "Portal lit", event.pos);
    }

    /**
     * Notices an ender chest being opened, and arms the scan of any other container.
     *
     * <p>Ender chests are counted on opening rather than on being seen or broken: what the tally
     * is about is how many times you have gone to the stash, not how many exist.
     */
    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof HandledScreen<?>)) return;
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;

        if (!(MeteorClient.mc.crosshairTarget instanceof BlockHitResult hit)
            || MeteorClient.mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            // No block under the crosshair means this is your own inventory or a screen opened by
            // something else; there is nothing out in the world to attribute a find to.
            return;
        }

        BlockPos pos = hit.getBlockPos().toImmutable();

        if (MeteorClient.mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
            enderChestsOpened++;

            // And nothing more. An ender chest is your own storage: the shulkers inside are the
            // ones you are carrying around, not ones you have found, and every visit would add
            // them again.
            return;
        }

        if (countedContainers.contains(pos)) return;

        containerPending = true;
        containerDirty = false;
        containerPos = pos;
        containerBest = 0;
    }

    @EventHandler
    private void onSlotUpdate(ContainerSlotUpdateEvent event) {
        if (containerPending) containerDirty = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;

        String world = MeteorClient.mc.world.getRegistryKey().getValue().toString();
        if (!world.equals(lastWorld)) {
            lastWorld = world;
            // The jump in coordinates is not distance travelled, and a strike left over from the
            // other side must not claim the portal waiting on this one.
            lastX = Double.NaN;
            ignitionPos = null;
            ignitionAge = Integer.MAX_VALUE;
        }

        if (ignitionAge < Integer.MAX_VALUE) ignitionAge++;

        // Every kind of gliding, whether a module is flying or you are. Firework boosts and the
        // gaps between them are all flight; what this excludes is walking, and standing still.
        if (MeteorClient.mc.player.isGliding()) flightTicks++;

        // Leaving the ground upwards. A jump starts at about 0.42 and a fall is negative, so the
        // threshold separates the two without counting a walk off a ledge.
        boolean onGround = MeteorClient.mc.player.isOnGround();
        if (wasOnGround && !onGround && MeteorClient.mc.player.getVelocity().y > 0.3) jumps++;
        wasOnGround = onGround;

        double x = MeteorClient.mc.player.getX();
        double z = MeteorClient.mc.player.getZ();

        if (!Double.isNaN(lastX)) {
            double step = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ));
            // Same teleport guard the other odometers use: a hundred-block step in one tick is
            // a portal or a setback, not distance travelled.
            if (step < 100.0) distance += step;
        }

        lastX = x;
        lastZ = z;

        if (containerPending) {
            if (containerDirty) scanOpenContainer();

            // The screen closing is the signal that nothing more is coming.
            if (!(MeteorClient.mc.currentScreen instanceof HandledScreen<?>)) finishContainer();
        }
    }

    /**
     * Re-reads the open container and remembers the most shulkers it has shown.
     *
     * <p>Called on every slot update for as long as the screen is up, so a late packet can only
     * raise the count. Nothing is tallied here — that waits for the screen to close, when the
     * contents are certainly all in.
     */
    private void scanOpenContainer() {
        containerDirty = false;

        if (!(MeteorClient.mc.currentScreen instanceof HandledScreen<?> screen)) return;

        int found = 0;

        for (Slot slot : screen.getScreenHandler().slots) {
            // The player's own inventory is stitched onto the bottom of every container screen;
            // counting those slots would tally the shulkers you are carrying as ones you found.
            if (slot.inventory == MeteorClient.mc.player.getInventory()) continue;

            ItemStack stack = slot.getStack();
            if (stack.getItem() instanceof BlockItem item
                && item.getBlock().getTranslationKey().contains("shulker_box")) {
                found += stack.getCount();
            }
        }

        if (found > containerBest) containerBest = found;
    }

    /** Closes the books on a container once its screen is gone. */
    private void finishContainer() {
        containerPending = false;
        containerDirty = false;

        if (containerPos != null) countedContainers.add(containerPos);
        containerPos = null;

        if (containerBest > 0) {
            shulkerChests++;
            shulkersSeen += containerBest;
        }

        containerBest = 0;
    }
}
