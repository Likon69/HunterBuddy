package com.hunterbuddy.bephax;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.world.RaycastContext;


import com.hunterbuddy.util.GlideClearHolder;

import net.minecraft.util.hit.HitResult;

import net.minecraft.util.math.Vec3d;

public class BepRocketFly extends Module {
    public static enum RefireMode {
        TIMED,
        CONTINUOUS
    }

    public static enum HoverMode {
        OFF,
        FREEZE
    }

    private long lastRocketUse = 0L;
    private long lastRocketTick = -1L;
    private int lastRocketDuration = 1;
    private boolean launched = false;
    private double yTarget = -1.0;
    private float targetPitch = 0.0F;
    private boolean boostToggledByUs = false;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<RefireMode> refire = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<RefireMode>()
                .name("refire")
                .description(
                    "When the next rocket goes out. Timed waits a wall clock delay, which leaves the boost window shut a third to a half of the time and drops the ride back to plain gliding while it waits. Continuous relights just before the one on the wing can die, so the window never shuts."
                )
                .defaultValue(RefireMode.CONTINUOUS)
                .build()
        );
    private final Setting<Double> safetyMargin = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("safety-margin")
                .description("How many seconds before a rocket can die the next one goes out.")
                .defaultValue(0.2)
                .min(0.0)
                .sliderRange(0.0, 1.0)
                .visible(() -> this.refire.get() == RefireMode.CONTINUOUS)
                .build()
        );
    private final Setting<Integer> fireworkDelay = this.sgGeneral
        .add(
            new Builder()
                .name("timed-delay")
                .description("The delay between firework usages in milliseconds.")
                .defaultValue(4000)
                .sliderRange(0, 10000)
                .visible(() -> this.refire.get() == RefireMode.TIMED)
                .build()
        );
    private final Setting<Boolean> useManualY = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("use-manual-y-level")
                .description("Use a manually set Y level instead of the Y level when activated.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> manualYLevel = this.sgGeneral
        .add(
            new Builder()
                .name("manual-y-level")
                .description("The Y level to maintain when using manual Y level.")
                .defaultValue(256)
                .sliderRange(-64, 320)
                .visible(this.useManualY::get)
                .onChanged(val -> this.yTarget = val.intValue())
                .build()
        );
    private final SettingGroup sgBoost = this.settings.createGroup("Firework Boost");
    private final Setting<Boolean> useBoost = this.sgBoost
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("firework-boost")
                .description(
                    "Ride the firework window while holding the Y level. The rockets stay BepRocketFly's own on timed-delay - the boost only rides whatever is lit. Level flight pays out on diagonal headings only: straight down an axis the window is worth no more than the firework itself and the boost stands aside, so leave boost-porpoise on."
                )
                .defaultValue(false)
                .onChanged(val -> {
                    if (this.isActive()) {
                        if (val) {
                            this.enableBoost();
                        } else {
                            this.disableBoost();
                        }
                    }
                })
                .build()
        );
    private final Setting<Boolean> boostPorpoise = this.sgBoost
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("boost-porpoise")
                .description(
                    "Alternate climb and dive legs instead of holding level flight. This is what makes the window pay on ANY heading. Level flight only settles each axis at the box edge (~1.68 b/t), so it needs a diagonal to split speed across two axes - but a dive makes the elytra's own fall-to-forward term grow the predicted movement the box is measured against, and that term depends on |vy| * cos^2(pitch), not on how the horizontal splits. Measured 92.15 b/s straight down an axis against 92.42 b/s on a diagonal at a 24-block leg: heading stops mattering. Net altitude stays inside porpoise-height; jump and sneak bias the legs up or down instead of steering pitch directly."
                )
                .defaultValue(true)
                .visible(this.useBoost::get)
                .build()
        );
    private final Setting<Integer> porpoiseHeight = this.sgBoost
        .add(
            new Builder()
                .name("porpoise-height")
                .description(
                    "Vertical blocks a climb or dive leg covers before flipping, centred on the Y lock. Longer legs lose fewer ticks to the turn-around but swing the altitude further around the locked height."
                )
                .defaultValue(24)
                .min(4)
                .max(128)
                .sliderRange(8, 64)
                .visible(() -> this.useBoost.get() && this.boostPorpoise.get())
                .build()
        );
    private final Setting<Boolean> diagonalSnap = this.sgBoost
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("diagonal-snap")
                .description(
                    "Veer travel onto the nearest 45-degree diagonal. Off by default: it is only a real win with boost-porpoise OFF, where level flight settles each axis at the box edge and a diagonal splits speed across two of them - 47.8 b/s against 33.8 straight down an axis. With the porpoise running the speed comes from the dive conversion instead, which does not care how the horizontal splits, so a diagonal is worth 0.3% (92.4 against 92.2) and costs you your heading. Leave it off unless you actually want to travel diagonally."
                )
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> asymmetricLegs = this.sgBoost
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("asymmetric-legs")
                .description(
                    "Solve the climb and the dive together instead of flying the same angle in both directions. They are not the same problem: the dive turns fall speed into forward speed while the climb spends it, so one angle chosen for the dive is the wrong one going up. Solved as a pair, the whole hop stays inside the boost window instead of only the half the single angle was picked for."
                )
                .defaultValue(true)
                .visible(() -> this.useBoost.get() && this.boostPorpoise.get())
                .build()
        );
    private final Setting<Boolean> pumpLegs = this.sgBoost
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pump-legs")
                .description(
                    "Fly each leg in two parts instead of one angle all the way. A look angle does two incompatible jobs: steep charges the vertical, because the window hands back a fall to convert and a steep climb turns horizontal into vertical; flat spends it, because the conversion pays by the square of the cosine and a climb near level coasts on the vertical already bought instead of paying the climb term twice. One angle has to compromise between the two. Two do not: steep for the first stretch of the leg, flat for the rest."
                )
                .defaultValue(true)
                .visible(() -> this.useBoost.get() && this.boostPorpoise.get())
                .build()
        );
    private final SettingGroup sgHover = this.settings.createGroup("Hover");
    private final Setting<HoverMode> hoverMode = this.sgHover
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<HoverMode>()
                .name("hover-mode")
                .description(
                    "What happens when you let go of the movement keys in flight. Off keeps flying. Freeze parks you where you are without claiming anything the server can dispute: the client simply stops sending positions, and the one reminder vanilla still sends every second carries the single movement the server accepts from a resting glider. Costs about a block of altitude a minute, against the steady sink a rubberbanded hover gives."
                )
                .defaultValue(HoverMode.FREEZE)
                .build()
        );
    private final Setting<Double> upAngle = this.sgHover
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("up-angle")
                .description("Pitch flown while the jump key is held, in degrees above level.")
                .defaultValue(45.0)
                .min(0.0)
                .sliderRange(0.0, 89.0)
                .build()
        );
    private final Setting<Double> downAngle = this.sgHover
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("down-angle")
                .description("Pitch flown while the sneak key is held, in degrees below level.")
                .defaultValue(20.0)
                .min(0.0)
                .sliderRange(0.0, 89.0)
                .build()
        );
    private final SettingGroup sgChestSwap = this.settings.createGroup("Chest Swap");
    private final Setting<Boolean> chestSwap = this.sgChestSwap
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("chest-swap")
                .description(
                    "Fly on rockets without spending elytra durability: a chest piece worn between bursts, the elytra swapped in for one server tick every swap-interval, and the rockets going out on the burst ticks. Keep a chest piece in a hand with the elytra worn."
                )
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> swapInterval = this.sgChestSwap
        .add(
            new Builder()
                .name("swap-interval")
                .description("Fewest ticks between two bursts. A floor, not an appointment: a burst only departs once the server's glide clear has arrived.")
                .defaultValue(8)
                .min(2)
                .sliderRange(2, 40)
                .visible(this.chestSwap::get)
                .build()
        );
    private final Setting<Integer> minAirBelow = this.sgChestSwap
        .add(
            new Builder()
                .name("min-air-below")
                .description(
                    "Fewest blocks of clear air under all four corners of the feet before a burst may run. Between bursts the server falls a body of its own at terminal speed and refuses the next start the moment that fall touches ground, so this is the margin that keeps it in the air. Two blocks of headroom are required as well, since the server carries a standing hitbox while the client glides."
                )
                .defaultValue(6)
                .min(3)
                .sliderRange(3, 32)
                .visible(this.chestSwap::get)
                .build()
        );
    private static final double PORPOISE_CLEARANCE = 32.0;
    private boolean frozen = false;
    private net.minecraft.util.Hand chestSwapBurstHand = null;
    private volatile long chestSwapNextBurst = 0L;
    private String chestSwapBlockedReason = null;
    private boolean chestSwapJumpHeld = false;
    private boolean rocketPending = false;
    private boolean porpoising = false;
    private boolean porpoiseDescending = false;
    private boolean porpoiseRotating = false;
    private int porpoiseFlipTicks = 0;
    private int boostWindowGrace = 0;

    public BepRocketFly() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "BepRocketFly", "Maintains a level Y-flight with fireworks and smooth pitch control.");
    }

    @Override
    public void onActivate() {
        this.launched = false;
        this.yTarget = -1.0;
        this.porpoising = false;
        this.porpoiseRotating = false;
        this.porpoiseFlipTicks = 0;
        this.boostWindowGrace = 0;
        if (this.useBoost.get()) {
            this.enableBoost();
        }

        if (this.mc.player == null || !this.mc.player.isGliding()) {
            this.info("You must be flying before enabling BepRocketFly.");
        }
    }

    @Override
    public void onDeactivate() {
        this.endPorpoise();
        this.disableBoost();
    }

    private void enableBoost() {
        BepBoost boost = Modules.get().get(BepBoost.class);
        if (boost != null && !boost.isActive()) {
            boost.toggle();
            this.boostToggledByUs = true;
        }
    }

    private void disableBoost() {
        if (this.boostToggledByUs) {
            this.boostToggledByUs = false;
            BepBoost boost = Modules.get().get(BepBoost.class);
            if (boost != null && boost.isActive()) {
                boost.toggle();
            }
        }
    }

    private BepBoost activeBoost() {
        if (!this.useBoost.get()) {
            return null;
        }

        BepBoost boost = Modules.get().get(BepBoost.class);
        return boost != null && boost.isActive() ? boost : null;
    }

    private void driveBoost() {
        if (this.mc.player != null) {
            BepBoost windowBoost = this.activeBoost();
            if (windowBoost != null && windowBoost.hasWindow()) {
                this.boostWindowGrace = 5;
            } else if (this.boostWindowGrace > 0) {
                this.boostWindowGrace--;
            }

            BepBoost boost = Modules.get().get(BepBoost.class);
            if (boost != null && boost.isActive() && this.mc.player.isGliding()) {
                boost.declareTravelling();
            }
        }
    }

    public void tickFlyLogic() {
        if (this.mc.player != null) {
            double currentY = this.mc.player.getY();
            if (this.mc.player.isGliding()) {
                if (this.yTarget == -1.0 || !this.launched) {
                    if (this.useManualY.get()) {
                        this.yTarget = this.manualYLevel.get().intValue();
                    } else {
                        this.yTarget = currentY;
                    }

                    this.launched = true;
                }

                if (!this.porpoiseTravel(currentY)) {
                    this.endPorpoise();
                    if (!this.useManualY.get()) {
                        double yDiffFromLock = currentY - this.yTarget;
                        if (Math.abs(yDiffFromLock) > 10.0) {
                            this.yTarget = currentY;
                            this.info("Y-lock reset due to altitude deviation.");
                        }
                    }

                    double yDiff = currentY - this.yTarget;
                    if (Math.abs(yDiff) > 10.0) {
                        this.targetPitch = (float)(Math.atan2(yDiff, 100.0) * (180.0 / Math.PI));
                    } else if (yDiff > 2.0) {
                        this.targetPitch = 10.0F;
                    } else if (yDiff < -2.0) {
                        this.targetPitch = -10.0F;
                    } else {
                        this.targetPitch = 0.0F;
                    }

                    float currentPitch = this.mc.player.getPitch();
                    float pitchDiff = this.targetPitch - currentPitch;
                    this.mc.player.setPitch(currentPitch + pitchDiff * 0.1F);
                }

                if (this.rocketDue(this.fireworkDelay.get().intValue())) {
                    this.tryUseFirework();
                }
            } else {
                this.endPorpoise();
                if (!this.launched) {
                    this.mc.player.jump();
                    this.launched = true;
                } else if (System.currentTimeMillis() - this.lastRocketUse > 1000L) {
                    this.tryUseFirework();
                }

                this.yTarget = -1.0;
            }
        }
    }

    private boolean porpoiseTravel(double y) {
        if (this.useBoost.get() && this.boostPorpoise.get() && this.boostWindowGrace != 0) {
            double half = this.porpoiseHeight.get().intValue() / 2.0;
            if (!this.porpoising) {
                this.porpoising = true;
                this.porpoiseDescending = y > this.yTarget;
                this.porpoiseFlipTicks = 0;
            }

            if (this.porpoiseFlipTicks > 0) {
                this.porpoiseFlipTicks--;
            }

            boolean manualUp = this.mc.options.jumpKey.isPressed();
            boolean manualDown = this.mc.options.sneakKey.isPressed();
            if (manualUp || manualDown) {
                this.porpoiseDescending = manualDown;
                if (!this.useManualY.get()) {
                    this.yTarget = y;
                }
            } else if (this.porpoiseFlipTicks == 0) {
                if (this.porpoiseDescending && y <= this.yTarget - half) {
                    this.porpoiseDescending = false;
                } else if (!this.porpoiseDescending && y >= this.yTarget + half) {
                    this.porpoiseDescending = true;
                }
            }

            float yaw = this.mc.player.getYaw();
            if (this.diagonalSnap.get()) {
                yaw = snapToDiagonal(yaw);
            }

            float[] legs = this.porpoiseLegs(yaw);
            float pitch = this.legPitchNow(legs, y, half);
            if (manualUp) {
                pitch = -this.upAngle.get().floatValue();
            } else if (manualDown) {
                pitch = this.downAngle.get().floatValue();
            }

            if (!manualUp && !manualDown && this.porpoiseFlipTicks == 0 && this.legObstructed(yaw, pitch)) {
                this.porpoiseDescending = !this.porpoiseDescending;
                pitch = this.legPitchNow(legs, y, half);
                this.porpoiseFlipTicks = 10;
            }

            if (BepRotations.getInstance().setRotationFullInstant(yaw, pitch)) {
                this.porpoiseRotating = true;
            }

            BepBoost porpoiseBoost = this.activeBoost();
            if (porpoiseBoost != null) {
                porpoiseBoost.declarePorpoising();
            }

            return true;
        } else {
            return false;
        }
    }

    private float[] porpoiseLegs(float yaw) {
        BepBoost boost = this.activeBoost();
        double alignment = boost == null ? 0.0 : boost.alignmentDegrees();
        double threshold = boost == null ? 1.7 : boost.windowThreshold();
        return BepSolver.solveLegs(
            this.porpoiseHeight.get().intValue(), yaw, alignment, threshold, this.asymmetricLegs.get(), this.pumpLegs.get()
        );
    }

    /**
     * The angle the plan calls for at this point of the leg. A pumped leg is
     * steep for its first stretch and flat for the rest, so where the swing
     * already is decides the angle, not just which way it is going.
     */
    private float legPitchNow(float[] legs, double y, double half) {
        double height = half * 2.0;
        double progress = this.porpoiseDescending ? (this.yTarget + half - y) / height : (y - (this.yTarget - half)) / height;
        progress = Math.max(0.0, Math.min(1.0, progress));
        float magnitude = BepSolver.legPitch(legs, this.porpoiseDescending, progress);
        return this.porpoiseDescending ? magnitude : -magnitude;
    }

    private static float snapToDiagonal(float yaw) {
        return (float)(Math.floor(yaw / 90.0F) * 90.0 + 45.0);
    }

    private boolean legObstructed(float yaw, float pitch) {
        if (this.mc.world == null) {
            return false;
        }

        Vec3d look = Vec3d.fromPolar(pitch, yaw);
        BepBoost boost = this.activeBoost();
        // The slope the leg actually flies, not the one it looks down: with the
        // window ridden at its corner the two are tens of degrees apart.
        Vec3d travel = boost == null ? look : boost.rideDirection(look);
        Vec3d eye = this.mc.player.getEyePos();
        HitResult hit = this.mc
            .world
            .raycast(new RaycastContext(eye, eye.add(travel.multiply(32.0)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player));
        return hit.getType() != HitResult.Type.MISS;
    }

    private void endPorpoise() {
        this.porpoising = false;
        this.porpoiseFlipTicks = 0;
        if (this.porpoiseRotating) {
            this.porpoiseRotating = false;
            BepRotations.getInstance().clearRotations();
        }
    }

    public void resetYLock() {
        this.yTarget = -1.0;
        this.launched = false;
    }

    @EventHandler
    private void onTick(Pre event) {
        this.frozen = this.wantsFreeze();
        this.driveBoost();
        this.serviceChestSwap();
        if (!this.frozen) {
            this.tickFlyLogic();
        }
    }

    /**
     * A resting glider: hovering asked for, nothing held down, and airborne.
     * The module claims nothing in that state - it simply stops talking, which
     * is the one thing the server cannot argue with.
     */
    private boolean wantsFreeze() {
        if (this.hoverMode.get() != HoverMode.FREEZE || this.mc.player == null || !this.mc.player.isGliding()) {
            return false;
        }

        return !this.mc.options.forwardKey.isPressed()
            && !this.mc.options.backKey.isPressed()
            && !this.mc.options.leftKey.isPressed()
            && !this.mc.options.rightKey.isPressed()
            && !this.mc.options.jumpKey.isPressed()
            && !this.mc.options.sneakKey.isPressed();
    }

    @EventHandler
    private void onSendPacket(meteordevelopment.meteorclient.events.packets.PacketEvent.Send event) {
        if (this.frozen
            && this.mc.player != null
            && event.packet instanceof net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
            && this.mc.player.age % 20L != 0L) {
            event.cancel();
        }
    }

    @EventHandler
    private void onReceivePacket(meteordevelopment.meteorclient.events.packets.PacketEvent.Receive event) {
        net.minecraft.client.network.ClientPlayerEntity player = this.mc.player;
        if (this.chestSwap.get() && player != null) {
            if (event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
                || event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
                || event.packet instanceof net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
                || event.packet instanceof net.minecraft.network.packet.s2c.play.ExplosionS2CPacket explosion && explosion.playerKnockback().isPresent()
                || event.packet instanceof net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket velocity
                    && velocity.getEntityId() == player.getId()) {
                GlideClearHolder.flushAheadOfTeleport();
                this.chestSwapBurstHand = null;
                this.chestSwapNextBurst = player.age + this.swapInterval.get().intValue();
            } else {
                GlideClearHolder.intake(this, event);
            }
        }
    }

    @EventHandler
    private void onSendMovementPackets(meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent.Pre event) {
        if (this.chestSwapBurstHand != null && this.mc.player != null) {
            net.minecraft.util.Hand hand = this.chestSwapBurstHand;
            this.chestSwapBurstHand = null;
            com.hunterbuddy.util.ChestSwapBurst.swapSilently(hand);
        }
    }

    private void serviceChestSwap() {
        this.releaseChestSwapJump();
        if (!this.chestSwap.get() || this.mc.player == null) {
            GlideClearHolder.release(this);
            this.chestSwapBurstHand = null;
            this.chestSwapBlockedReason = null;
            return;
        }

        boolean gliderWorn = com.hunterbuddy.util.ChestSwapBurst.isGlider(this.mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
        if (!this.mc.player.isGliding()) {
            this.handBackGlide();
            GlideClearHolder.release(this);
            return;
        }

        String problem = com.hunterbuddy.util.ChestSwapBurst.pairProblem(this.mc.player);
        if (problem != null && !gliderWorn) {
            this.reportChestSwapBlocked(problem);
            if (this.handBackGlide()) {
                GlideClearHolder.release(this);
            }

            return;
        }

        if (!this.clearanceOk()) {
            if (this.handBackGlide()) {
                GlideClearHolder.release(this);
            }

            return;
        }

        this.chestSwapBlockedReason = null;
        if (!GlideClearHolder.claim(this)) {
            return;
        }

        long age = this.mc.player.age;
        int interval = this.swapInterval.get().intValue();
        if (gliderWorn) {
            net.minecraft.util.Hand chestHand = com.hunterbuddy.util.ChestSwapBurst.chestPieceHand(this.mc.player);
            if (chestHand != null && age >= this.chestSwapNextBurst) {
                com.hunterbuddy.util.ChestSwapBurst.swapSilently(chestHand);
                this.chestSwapNextBurst = age + 2L;
            }

            return;
        }

        if (!GlideClearHolder.holding()) {
            GlideClearHolder.answerPings();
            return;
        }

        if (age < this.chestSwapNextBurst && age - GlideClearHolder.heldSince() <= 2L * interval) {
            return;
        }

        net.minecraft.util.Hand gliderHand = com.hunterbuddy.util.ChestSwapBurst.gliderHand(this.mc.player);
        if (gliderHand != null) {
            this.chestSwapBurst(gliderHand, true);
        }
    }

    private void chestSwapBurst(net.minecraft.util.Hand gliderHand, boolean swapBack) {
        GlideClearHolder.flush();
        if (gliderHand != null) {
            com.hunterbuddy.util.ChestSwapBurst.swapSilently(gliderHand);
        } else {
            com.hunterbuddy.util.ChestSwapBurst.wearGlider(this.mc.player);
        }

        this.mc
            .getNetworkHandler()
            .sendPacket(
                new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(
                    this.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING
                )
            );
        this.mc.player.startGliding();
        this.mc.options.jumpKey.setPressed(true);
        this.chestSwapJumpHeld = true;
        this.chestSwapBurstHand = swapBack ? gliderHand : null;
        this.chestSwapNextBurst = this.mc.player.age + this.swapInterval.get().intValue();
        if (this.rocketPending) {
            this.rocketPending = false;
            this.fireRocketNow();
        }
    }

    private void releaseChestSwapJump() {
        if (this.chestSwapJumpHeld) {
            this.chestSwapJumpHeld = false;
            this.mc.options.jumpKey.setPressed(false);
        }
    }

    private boolean handBackGlide() {
        if (this.mc.player == null) {
            return true;
        }

        this.chestSwapBurstHand = null;
        if (com.hunterbuddy.util.ChestSwapBurst.isGlider(this.mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST))) {
            return true;
        }

        net.minecraft.util.Hand gliderHand = com.hunterbuddy.util.ChestSwapBurst.gliderHand(this.mc.player);
        if (!this.mc.player.isGliding()) {
            com.hunterbuddy.util.ChestSwapBurst.wearGlider(this.mc.player);
            return true;
        }

        if (gliderHand == null && com.hunterbuddy.util.ChestSwapBurst.gliderSlot(this.mc.player) == -1) {
            return true;
        }

        if (!GlideClearHolder.owns(this) || !GlideClearHolder.holding()) {
            return false;
        }

        this.chestSwapBurst(gliderHand, false);
        return true;
    }

    private void reportChestSwapBlocked(String reason) {
        if (!reason.equals(this.chestSwapBlockedReason)) {
            this.chestSwapBlockedReason = reason;
            this.info(reason);
        }
    }

    private boolean clearanceOk() {
        double x = this.mc.player.getX();
        double y = this.mc.player.getY();
        double z = this.mc.player.getZ();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return false;
        }

        int feet = net.minecraft.util.math.MathHelper.floor(y);
        net.minecraft.util.math.BlockPos.Mutable pos = new net.minecraft.util.math.BlockPos.Mutable();

        for (int above = 1; above <= 2; above++) {
            pos.set(net.minecraft.util.math.MathHelper.floor(x), feet + above, net.minecraft.util.math.MathHelper.floor(z));
            if (!this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos).isEmpty()) {
                return false;
            }
        }

        int depth = this.minAirBelow.get().intValue();

        for (int cornerX = -1; cornerX <= 1; cornerX += 2) {
            for (int cornerZ = -1; cornerZ <= 1; cornerZ += 2) {
                if (!this.columnClear(x + cornerX * 0.3, z + cornerZ * 0.3, feet, depth)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean columnClear(double x, double z, int feet, int depth) {
        net.minecraft.util.math.BlockPos.Mutable pos = new net.minecraft.util.math.BlockPos.Mutable();
        int blockX = net.minecraft.util.math.MathHelper.floor(x);
        int blockZ = net.minecraft.util.math.MathHelper.floor(z);

        for (int dy = 1; dy <= depth; dy++) {
            pos.set(blockX, feet - dy, blockZ);
            if (!this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private void tryUseFirework() {
        // The server only spawns a rocket for a player it believes is gliding,
        // and between bursts it believes nothing of the sort: the rocket would
        // be eaten in silence, no entity, no boost, the item kept. So it waits
        // for the burst and goes out behind the glide start.
        if (this.chestSwap.get()) {
            this.rocketPending = true;
            return;
        }

        this.fireRocketNow();
    }

    private void fireRocketNow() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!hotbar.found()) {
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (!inv.found()) {
                this.info("No fireworks found in hotbar or inventory.");
                return;
            }

            int hotbarSlot = this.findEmptyHotbarSlot();
            if (hotbarSlot == -1) {
                this.info("No empty hotbar slot available to move fireworks.");
                return;
            }

            InvUtils.move().from(inv.slot()).to(hotbarSlot);
        }

        this.lastRocketDuration = this.rocketFlightDuration();
        BepUtils.firework(this.mc);
        this.lastRocketUse = System.currentTimeMillis();
        this.lastRocketTick = this.mc.player.age;
    }

    /** Flight duration of the rocket about to go out; 1 when there is nothing to read. */
    private int rocketFlightDuration() {
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
                net.minecraft.component.type.FireworksComponent fireworks = stack.get(net.minecraft.component.DataComponentTypes.FIREWORKS);
                if (fireworks != null) {
                    return fireworks.flightDuration();
                }
            }
        }

        return 1;
    }

    /**
     * Whether the next rocket is due. TIMED is the old wall-clock delay, which
     * leaves the Grim window shut between 30 and 55 percent of the time and
     * drops the ride back to plain gliding while it waits. CONTINUOUS relights
     * a few ticks before the one on the wing can die: a firework lives
     * 10 x (flight duration + 1) ticks at the earliest, and safety-margin is
     * how far ahead of that the next one goes out, so the window never shuts.
     */
    private boolean rocketDue(long groundedDelayMillis) {
        if (this.refire.get() != RefireMode.CONTINUOUS) {
            return System.currentTimeMillis() - this.lastRocketUse > groundedDelayMillis;
        }

        if (this.lastRocketTick < 0L || this.mc.player == null) {
            return true;
        }

        int needed = 10 * (this.lastRocketDuration + 1) - (int)Math.ceil((Double)this.safetyMargin.get() * 20.0);
        return this.mc.player.age - this.lastRocketTick >= Math.max(2, needed);
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }
}
