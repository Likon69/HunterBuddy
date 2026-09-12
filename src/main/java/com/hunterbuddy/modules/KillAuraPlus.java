package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * An aura that picks its weapon, its moment and its target on purpose.
 *
 * <p>Meteor's own kill aura is good at deciding <em>who</em> is a target and poor
 * at everything after that: it swings with the first acceptable item in the
 * hotbar rather than the one that hits hardest, it never stops sprinting, and it
 * draws nothing at all. The first costs damage on every swing, the second costs
 * every critical hit -- {@code Player.canCriticalAttack} in 1.21.11 lists
 * {@code isSprinting()} among the things that forbid a crit, so an aura that
 * sprints is an aura that never crits -- and the third means you never know what
 * it is about to hit.
 *
 * <p>Three things here are worth naming:
 *
 * <ul>
 *   <li>The weapon is chosen by {@link DamageUtils#getAttackDamage} against
 *       <em>this</em> target, so Smite goes to the skeleton and the axe to the
 *       unarmoured player, enchantments and armour included.
 *   <li>The sprint is dropped by packet for the length of one attack and put
 *       back, which is the W-tap a player does by hand.
 *   <li>One target per cooldown, never several. Attacking three entities in the
 *       same tick spends the cooldown on the first and gives the other two a tap
 *       worth almost nothing.
 * </ul>
 *
 * <p>The defaults are what a server measures as legal: three blocks of reach,
 * the vanilla cooldown, no swinging through walls. The sliders go further for
 * anyone who wants them to.
 */
public class KillAuraPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargets = settings.createGroup("Targets");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far a hitbox may be from your eyes. Vanilla attack reach is three blocks and a server that checks reach measures exactly this distance, so anything above three is a hit the server can refuse and a check it can fail.")
        .defaultValue(3.0)
        .min(0.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("How far you may hit something you cannot see. Zero means never: a swing that lands through a wall is the one thing a watching player notices without any tooling.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 6.0)
        .build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Only consider what is within this many degrees of where you are looking. A hundred and eighty is everything, including what is behind you.")
        .defaultValue(180.0)
        .min(10.0)
        .sliderRange(30.0, 180.0)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Hold the hotbar slot that does the most damage to the current target. The damage is computed against that entity, so Smite wins against a skeleton and loses against a spider, and armour is taken into account.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fetchFromInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("fetch-from-inventory")
        .description("When no hotbar slot holds a weapon but the inventory does (a regear moved it, say), bring the best one back: into an empty hotbar slot, else the slot you are holding, whose item takes the weapon's old place.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Return to the slot you were holding once there is nothing left to attack.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Integer> handsOff = sgGeneral.add(new IntSetting.Builder()
        .name("hands-off")
        .description("Milliseconds to stay out of your hotbar after you change slot yourself or hold right click. Without it the switch takes the slot back on the same tick you leave it, and eating or placing a block with a mob nearby becomes impossible.")
        .defaultValue(600)
        .min(0)
        .sliderRange(0, 2000)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> requireWeapon = sgGeneral.add(new BoolSetting.Builder()
        .name("require-weapon")
        .description("Never attack while holding something that is not in the weapon list. Off, the aura will happily punch with a shulker box in hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<net.minecraft.item.Item>> weapons = sgGeneral.add(new ItemListSetting.Builder()
        .name("weapons")
        .description("Which families of item count as a weapon, for both the switch and the check above. Pick the diamond one and every material of that family counts. Empty means anything in your hand will do.")
        .defaultValue(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT, Items.MACE)
        .filter(FILTER::contains)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only swing while you hold left click.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseEating = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-using")
        .description("Stop while you are eating, drawing a bow or holding up a shield. Attacking cancels all three.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseMining = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-mining")
        .description("Stop while you are breaking a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minHealth = sgGeneral.add(new IntSetting.Builder()
        .name("min-health")
        .description("Stop swinging at or below this many points of health, so you can eat or leave without the module pulling you back into the fight. Zero turns it off.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Stop while the server has not sent a tick for a second. Swinging into a freeze lands everything at once when it comes back.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseGliding = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-gliding")
        .description("Stop while you are gliding on an elytra. Whatever put you in the air -- Baritone, WaypointFollower, or your own hand -- a swing turn takes the rotation away from the heading and a hit costs the speed.")
        .defaultValue(true)
        .build()
    );

    // Targets

    private final Setting<Set<EntityType<?>>> entities = sgTargets.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("What to attack. Nothing outside this list is ever touched, whatever the other settings say.")
        .onlyAttackable()
        .defaultValue(
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED,
            EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON,
            EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.CREEPER, EntityType.ENDERMAN,
            EntityType.WITCH, EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.BLAZE,
            EntityType.PHANTOM, EntityType.SILVERFISH, EntityType.PILLAGER, EntityType.VINDICATOR,
            EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN
        )
        .build()
    );

    private final Setting<Priority> priority = sgTargets.add(new EnumSetting.Builder<Priority>()
        .name("priority")
        .description("""
            Which one to hit when several qualify.
            - Smart:      anything a single swing would kill first, then the closest. Finishes what is nearly dead instead of spreading damage over a crowd.
            - Closest:    shortest distance.
            - Angle:      whatever is nearest the middle of your screen.
            - Weakest:    lowest health.
            - Toughest:   highest health.
            """)
        .defaultValue(Priority.Smart)
        .build()
    );

    private final Setting<Boolean> cycle = sgTargets.add(new BoolSetting.Builder()
        .name("cycle-targets")
        .description("Spread the swings around the crowd instead of finishing one at a time. Slower to kill anything, but it keeps several mobs knocked back at once.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minAge = sgTargets.add(new IntSetting.Builder()
        .name("min-age")
        .description("Ignore anything that has existed for fewer than this many ticks. Something that appeared next to you a moment ago is far more often a spawn you should not be swinging at than a target.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargets.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Never attack anyone on Meteor's friend list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargets.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Never attack a mob wearing a name tag. Someone went to the trouble.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargets.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Never attack an animal you tamed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreBaby = sgTargets.add(new BoolSetting.Builder()
        .name("ignore-baby")
        .description("Leave the babies alone.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargets.add(new BoolSetting.Builder()
        .name("only-angry-neutrals")
        .description("Endermen, piglins, zombified piglins and wolves are only attacked once they have turned on you. Off, the aura starts every one of those fights for you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreInvisible = sgTargets.add(new BoolSetting.Builder()
        .name("ignore-invisible")
        .description("Skip invisible entities.")
        .defaultValue(false)
        .build()
    );

    // Timing

    private final Setting<Timing> timing = sgTiming.add(new EnumSetting.Builder<Timing>()
        .name("timing")
        .description("Cooldown waits for the vanilla attack bar to refill, which is the only way a swing does its full damage. Clicks is the old fixed rate: more swings, each one weaker, and only worth it against something with no armour that dies to knockback.")
        .defaultValue(Timing.Cooldown)
        .build()
    );

    private final Setting<Double> minCps = sgTiming.add(new DoubleSetting.Builder()
        .name("min-cps")
        .description("Slowest click rate.")
        .defaultValue(6.0)
        .min(0.5)
        .sliderRange(1.0, 20.0)
        .visible(() -> timing.get() == Timing.Clicks)
        .build()
    );

    private final Setting<Double> maxCps = sgTiming.add(new DoubleSetting.Builder()
        .name("max-cps")
        .description("Fastest click rate. The delay between two swings is drawn between the two, so the rhythm is never exactly regular.")
        .defaultValue(9.0)
        .min(0.5)
        .sliderRange(1.0, 20.0)
        .visible(() -> timing.get() == Timing.Clicks)
        .build()
    );

    private final Setting<Integer> jitter = sgTiming.add(new IntSetting.Builder()
        .name("jitter")
        .description("Milliseconds of random extra wait after the cooldown fills. A swing that lands on exactly the same millisecond of the bar every single time is a rhythm no hand produces.")
        .defaultValue(60)
        .min(0)
        .sliderRange(0, 400)
        .visible(() -> timing.get() == Timing.Cooldown)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("tps-sync")
        .description("Stretch the cooldown when the server runs slow. The bar refills in server ticks, not in seconds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> noDelay = sgTiming.add(new BoolSetting.Builder()
        .name("no-delay")
        .description("Hit at the fixed rate below, ignoring the attack cooldown and the charge entirely -- never waiting for the bar to refill. This is what lets the aura bat away a ghast fireball the instant it is in range. Overrides the timing above.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> noDelayCps = sgTiming.add(new DoubleSetting.Builder()
        .name("no-delay-cps")
        .description("How many hits per second while no-delay is on. High (up to 20) bats fireballs away fastest but each hit is barely charged, so it mostly knocks back -- which is why the sword felt like a fist. Low (around 2-3) lets the bar charge between hits, so each swing lands real sword damage; in between trades one for the other.")
        .defaultValue(10.0).min(2.0).max(20.0).sliderRange(2.0, 20.0)
        .visible(noDelay::get)
        .build()
    );

    private final Setting<Boolean> sprintReset = sgTiming.add(new BoolSetting.Builder()
        .name("sprint-reset")
        .description("Drop sprint by packet for the length of the swing and take it straight back. Sprinting is one of the conditions that forbids a critical hit, so this is what turns every landed swing into a crit without you letting go of the key.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyCrits = sgTiming.add(new BoolSetting.Builder()
        .name("only-crits")
        .description("Hold the swing until you are falling, so it always crits. Off the ground this costs nothing; standing on flat ground it means the aura never swings at all, which is why it is off. Useless on the clicks timing, where nothing reaches the nine tenths of charge a critical needs.")
        .defaultValue(false)
        .visible(() -> timing.get() == Timing.Cooldown)
        .build()
    );

    private final Setting<Integer> switchDelay = sgTiming.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("Ticks to wait after changing hotbar slot before swinging. A swing in the same tick as the switch is a swing the server may still credit to the old item.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    // Rotation

    private final Setting<RotationMode> rotate = sgRotation.add(new EnumSetting.Builder<RotationMode>()
        .name("rotate")
        .description("On hit turns for the swing and lets go. Always keeps you aimed at the target the whole time, which is what a yaw step needs to have something to walk from. Off swings wherever you happen to be looking, which a server that checks the hitbox against your own rotation reads as a miss on every swing that is not already on target. While a rotation is being sent -- the swing and the few ticks after it -- forward pushes toward the target rather than toward the camera. That is not a side effect but the fix for one: the server works the movement out from the yaw it was sent, so walking along the camera during those ticks is what gets you set back.")
        .defaultValue(RotationMode.OnHit)
        .build()
    );

    private final Setting<Aim> aimAt = sgRotation.add(new EnumSetting.Builder<Aim>()
        .name("aim-at")
        .description("Which point of the target to aim at. Closest is the point of the hitbox nearest your eyes, which is also the point a reach check measures, so it is the one that keeps a distant swing inside the limit.")
        .defaultValue(Aim.Closest)
        .visible(() -> rotate.get() != RotationMode.Off)
        .build()
    );

    private final Setting<Double> yawStep = sgRotation.add(new DoubleSetting.Builder()
        .name("yaw-step")
        .description("Most degrees of yaw per tick. Zero snaps straight onto the target. Anything else takes several ticks to come round, and the swing waits until the aim has arrived.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 180.0)
        .visible(() -> rotate.get() == RotationMode.Always)
        .build()
    );

    private final Setting<Double> pitchJitter = sgRotation.add(new DoubleSetting.Builder()
        .name("pitch-jitter")
        .description("Degrees of noise added to the pitch. A pitch that is exact to the seventh decimal on every swing is not a pitch a mouse produces.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 6.0)
        .visible(() -> rotate.get() != RotationMode.Off)
        .build()
    );

    // Render

    private final Setting<RenderMode> render = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render")
        .description("Sweep draws the animation chosen below. Box is the hitbox. Both draws the two together.")
        .defaultValue(RenderMode.Sweep)
        .build()
    );

    private final Setting<Animation> animation = sgRender.add(new EnumSetting.Builder<Animation>()
        .name("animation")
        .description("""
            Which animation marks the target.
            - Sweep:     a ring that travels up and down the body with a soft skirt behind it.
            - Orbit:     two rings of arcs turning against each other, like a gyroscope, that jolt on every hit.
            - Heartbeat: a ring on the ground that fills with the attack cooldown, and a wave that leaves on each hit.
            """)
        .defaultValue(Animation.Sweep)
        .visible(() -> render.get() == RenderMode.Sweep || render.get() == RenderMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill colour.")
        .defaultValue(new SettingColor(190, 60, 60, 60))
        .visible(() -> render.get() != RenderMode.Off)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Edge colour.")
        .defaultValue(new SettingColor(255, 90, 90, 255))
        .visible(() -> render.get() != RenderMode.Off)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Whether the box is drawn filled, outlined or both.")
        .defaultValue(ShapeMode.Both)
        .visible(() -> render.get() == RenderMode.Box || render.get() == RenderMode.Both)
        .build()
    );

    private final Setting<BoxStyle> boxStyle = sgRender.add(new EnumSetting.Builder<BoxStyle>()
        .name("box-style")
        .description("Fade dims the box between swings, so the brightness is the beat of the cooldown. Shrink pulls it in after each hit. Pulse breathes on its own clock. Solid does not move.")
        .defaultValue(BoxStyle.Fade)
        .visible(() -> render.get() == RenderMode.Box || render.get() == RenderMode.Both)
        .build()
    );

    private final Setting<Integer> sweepPeriod = sgRender.add(new IntSetting.Builder()
        .name("sweep-period")
        .description("Milliseconds for one full travel of the ring, up and back down.")
        .defaultValue(1500)
        .min(200)
        .sliderRange(400, 4000)
        .visible(() -> render.get() == RenderMode.Sweep || render.get() == RenderMode.Both)
        .build()
    );

    private final Setting<Integer> sweepSegments = sgRender.add(new IntSetting.Builder()
        .name("sweep-segments")
        .description("Sides of the ring. Sixteen is visibly a polygon, forty-eight is a circle at any size a target ever has on screen.")
        .defaultValue(32)
        .min(6)
        .sliderRange(8, 64)
        .visible(() -> render.get() == RenderMode.Sweep || render.get() == RenderMode.Both)
        .build()
    );

    private final Setting<Double> sweepScale = sgRender.add(new DoubleSetting.Builder()
        .name("sweep-scale")
        .description("Radius of the ring as a multiple of the target's width. Above one it stands off the body, which reads better on something thin.")
        .defaultValue(1.1)
        .min(0.3)
        .sliderRange(0.5, 2.5)
        .visible(() -> render.get() == RenderMode.Sweep || render.get() == RenderMode.Both)
        .build()
    );

    private final Setting<Boolean> hurry = sgRender.add(new BoolSetting.Builder()
        .name("faster-when-dying")
        .description("Speed the animation up as the target's health falls, down to a third of the period at the last heart. The health bar you would otherwise be reading, as a rhythm you catch out of the corner of your eye. Heartbeat ignores it: there it is the cooldown that beats, and two clocks on the same drawing cancel each other out.")
        .defaultValue(true)
        .visible(() -> render.get() == RenderMode.Sweep || render.get() == RenderMode.Both)
        .build()
    );

    private static final List<net.minecraft.item.Item> FILTER = List.of(
        Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL,
        Items.DIAMOND_HOE, Items.MACE, Items.TRIDENT);

    private final List<Entity> targets = new ArrayList<>();
    private final Set<Entity> lethal = new HashSet<>();
    private final Set<Entity> hitThisRound = new HashSet<>();
    private final Random random = new Random();

    private Entity current;
    private int switchTimer;
    private int ticksSinceHit;
    private net.minecraft.item.Item lastHeld;
    private int previousSlot = -1;
    private int ourSlot = -1;
    private long handsOffUntil;
    private boolean swapped;
    private long nextFetchMs;
    private long nextHitMs;
    private long lastHitMs;
    private double orbitAngle;
    private long orbitLastMs;

    // The last yaw handed to Rotations, and how long ago. Read from the input
    // mixin, which has to turn the walk by the same amount.
    private float lastAimYaw = Float.NaN;
    private int ticksSinceAim;

    public KillAuraPlus() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "kill-aura-plus",
            "Attacks what you choose, with the weapon that hurts it most, on the beat that does full damage.");
    }

    @Override
    public void onActivate() {
        targets.clear();
        lethal.clear();
        hitThisRound.clear();
        current = null;
        switchTimer = 0;
        ticksSinceHit = 0;
        lastHeld = null;
        previousSlot = -1;
        ourSlot = -1;
        handsOffUntil = 0;
        swapped = false;
        nextHitMs = 0;
        lastHitMs = 0;
        orbitAngle = 0.0;
        orbitLastMs = 0;
        lastAimYaw = Float.NaN;
        ticksSinceAim = 0;
    }

    @Override
    public void onDeactivate() {
        release();
        targets.clear();
        current = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (switchTimer > 0) switchTimer--;
        if (ticksSinceAim < 1000) ticksSinceAim++;

        // Vanilla resets the attack timer whenever the held item changes, so the
        // count of server ticks has to reset with it or it would credit a charge
        // the server never gave.
        ticksSinceHit++;
        net.minecraft.item.Item held = mc.player.getMainHandStack().getItem();
        if (held != lastHeld) {
            lastHeld = held;
            ticksSinceHit = 0;
        }

        if (!running()) {
            release();
            return;
        }

        gather();

        if (targets.isEmpty()) {
            release();
            return;
        }

        Entity target = choose();
        if (target == null) {
            release();
            return;
        }

        current = target;

        long now = System.currentTimeMillis();
        int selected = mc.player.getInventory().getSelectedSlot();

        // Two ways of saying "leave my hands alone": moving the slot off the one
        // this module put you on, and holding right click. Without this the switch
        // reclaims the slot on the very next tick, so with a mob nearby you cannot
        // eat, cannot place, cannot scroll -- the module wins every exchange and
        // the game stops answering you.
        if (ourSlot != -1 && selected != ourSlot) {
            ourSlot = -1;
            handsOffUntil = now + handsOff.get();

            // And the swap is forgotten with it. Kept, previousSlot would still
            // hold whatever you were on before the module last took over, and the
            // next release would send you back there instead of leaving you on the
            // slot you had just chosen by hand.
            swapped = false;
            previousSlot = -1;
        }

        if (mc.options.useKey.isPressed()) handsOffUntil = now + handsOff.get();

        // Only at full charge, and this is not an optimisation. The damage figure
        // scales the base by 0.2 + charge^2 * 0.8 and the enchantments by charge
        // flat, so at low charge an unenchanted axe outscores a Sharpness sword;
        // and changing item resets the cooldown, which keeps the charge low. Weighed
        // every tick the two answers chase each other and the module switches for
        // ever without ever landing a charged hit.
        if (autoSwitch.get() && now >= handsOffUntil && charged()) {
            int slot = bestSlot(target);

            if (slot == -1 && fetchFromInventory.get()) {
                fetchWeapon(target, selected, now);
            }

            if (slot != -1 && slot != selected) {
                if (!swapped) {
                    previousSlot = selected;
                    swapped = true;
                }

                InvUtils.swap(slot, false);
                ourSlot = slot;
                switchTimer = switchDelay.get();
            }
            else if (slot == selected) {
                ourSlot = slot;
            }
        }

        if (requireWeapon.get() && !isWeapon(mc.player.getMainHandStack())) return;

        Vec3d point = aimPoint(target);
        double goalYaw = Rotations.getYaw(point);
        double goalPitch = Rotations.getPitch(point);
        double yaw = goalYaw;
        boolean aimed = true;

        // Walking the yaw round instead of snapping it, starting from what the
        // server was last told. But serverYaw only moves when a module posts a
        // rotation: once the hold has run out it is your own camera that goes in
        // the packets while serverYaw keeps the last figure some module wrote,
        // possibly minutes ago. Stepping from that would step from nowhere.
        if (rotate.get() == RotationMode.Always && yawStep.get() > 0.0) {
            double from = Rotations.rotationTimer > Config.get().rotationHoldTicks.get()
                ? mc.player.getYaw()
                : Rotations.serverYaw;

            double diff = MathHelper.wrapDegrees(goalYaw - from);

            if (Math.abs(diff) > yawStep.get()) {
                yaw = from + Math.copySign(yawStep.get(), diff);
                aimed = false;
            }
        }

        double pitch = goalPitch;
        if (pitchJitter.get() > 0.0) pitch += (random.nextDouble() - 0.5) * pitchJitter.get();
        pitch = MathHelper.clamp(pitch, -90.0, 90.0);

        boolean hit = aimed && switchTimer <= 0 && ready() && (!onlyCrits.get() || crits());

        if (rotate.get() == RotationMode.Off) {
            if (hit) attack(target);
            return;
        }

        if (rotate.get() == RotationMode.OnHit && !hit) return;

        // No callback. Meteor runs a rotation callback in SendMovementPacketsEvent
        // .Post, which is after this tick's flying has already gone out -- so the
        // server would pair the swing with the position of tick N while the reach
        // and the aim were worked out at N-1, up to a quarter of a block apart at
        // sprint speed. That is a reach check failed on arithmetic alone. Vanilla
        // sends the attack during the tick and the flying at the end of it, so the
        // swing goes now and the rotation rides the packet that follows it.
        // Never client side: Meteor writes the rotation onto the movement packet
        // and takes it back off immediately after, so a single rotation in a tick
        // leaves the camera alone either way. The setting that used to sit here
        // chose between two identical outcomes.
        Rotations.rotate(yaw, pitch, 50, false, null);
        lastAimYaw = (float) yaw;
        ticksSinceAim = 0;

        if (hit) attack(target);
    }

    // Deciding

    private boolean running() {
        if (!mc.player.isAlive() || mc.player.isSpectator()) return false;
        if (minHealth.get() > 0 && mc.player.getHealth() <= minHealth.get()) return false;
        if (mc.currentScreen != null) return false;
        if (pauseGliding.get() && mc.player.isGliding()) return false;
        if (onlyOnClick.get() && !mc.options.attackKey.isPressed()) return false;
        if (pauseMining.get() && mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) return false;
        if (pauseEating.get() && mc.player.isUsingItem()) return false;
        return !pauseOnLag.get() || TickRate.INSTANCE.getTimeSinceLastTick() < 1.0f;
    }

    private void gather() {
        targets.clear();

        for (Entity entity : mc.world.getEntities()) {
            if (valid(entity)) targets.add(entity);
        }
    }

    private boolean valid(Entity entity) {
        if (entity == mc.player || entity == mc.getCameraEntity()) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof LivingEntity living && living.isDead()) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (entity.age < minAge.get()) return false;
        if (ignoreInvisible.get() && entity.isInvisible()) return false;
        if (ignoreNamed.get() && entity.hasCustomName() && !(entity instanceof PlayerEntity)) return false;

        double reach = reachTo(entity);
        if (reach > range.get()) return false;

        // Line of sight is asked for last of the cheap tests because it raycasts.
        if (!PlayerUtils.canSeeEntity(entity) && reach > wallsRange.get()) return false;
        if (fov.get() < 180.0 && angleTo(entity) > fov.get() / 2.0) return false;

        if (ignoreTamed.get() && entity instanceof Tameable tameable
            && tameable.getOwner() != null && tameable.getOwner().equals(mc.player)) return false;

        if (ignorePassive.get()) {
            if (entity instanceof EndermanEntity enderman && !enderman.isAngry()) return false;
            if ((entity instanceof PiglinEntity || entity instanceof ZombifiedPiglinEntity
                || entity instanceof WolfEntity) && !((MobEntity) entity).isAttacking()) return false;
        }

        if (ignoreBaby.get() && entity instanceof LivingEntity living && living.isBaby()) return false;

        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
            if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
        }

        return true;
    }

    /**
     * The distance a reach check measures: from your eyes to the nearest point of
     * the hitbox, not to the entity's feet and not centre to centre.
     */
    private double reachTo(Entity entity) {
        Vec3d eye = mc.player.getEyePos();
        Box box = entity.getBoundingBox();

        double dx = Math.max(box.minX - eye.x, Math.max(0.0, eye.x - box.maxX));
        double dy = Math.max(box.minY - eye.y, Math.max(0.0, eye.y - box.maxY));
        double dz = Math.max(box.minZ - eye.z, Math.max(0.0, eye.z - box.maxZ));

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * The angle between where you are looking and the entity, in three dimensions.
     *
     * <p>Measured on yaw alone, something directly under your feet sits at zero
     * degrees and passes any cone you set, which is the opposite of what the
     * setting promises.
     */
    private double angleTo(Entity entity) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d to = nearestPoint(entity).subtract(eye);
        double length = to.length();
        if (length < 1.0E-4) return 0.0;

        double cos = mc.player.getRotationVec(1.0f).dotProduct(to) / length;
        return Math.toDegrees(Math.acos(MathHelper.clamp(cos, -1.0, 1.0)));
    }

    /** The point of the hitbox nearest your eyes, which is also what reach measures. */
    private Vec3d nearestPoint(Entity entity) {
        Vec3d eye = mc.player.getEyePos();
        Box box = entity.getBoundingBox();

        return new Vec3d(
            MathHelper.clamp(eye.x, box.minX, box.maxX),
            MathHelper.clamp(eye.y, box.minY, box.maxY),
            MathHelper.clamp(eye.z, box.minZ, box.maxZ));
    }

    private Entity choose() {
        if (targets.size() == 1) return targets.getFirst();

        // Worked out once per tick rather than inside the comparator: a sort asks
        // for the key of the same entity many times over, and this key runs a full
        // damage calculation through that entity's armour every time it is asked.
        // Same reason as the switch: below full charge the figure says almost
        // nothing would die, so the ranking is only meaningful on the tick the
        // swing can actually happen. Between two swings the last answer stands.
        if (priority.get() == Priority.Smart && charged()) {
            lethal.clear();
            ItemStack weapon = mc.player.getMainHandStack();

            for (Entity entity : targets) {
                if (entity instanceof LivingEntity living
                    && damageTo(entity, weapon) >= living.getHealth()) lethal.add(entity);
            }
        }

        targets.sort(comparator());

        if (!cycle.get()) return targets.getFirst();

        // Whoever has not taken a swing yet this round. An index into the list
        // would be an index into ranks, not into entities: the list is re-sorted
        // every tick, so rank two is a different mob each time and the same one
        // can be hit twice while its neighbour is never touched.
        for (Entity entity : targets) {
            if (!hitThisRound.contains(entity)) return entity;
        }

        hitThisRound.clear();
        return targets.getFirst();
    }

    private Comparator<Entity> comparator() {
        return switch (priority.get()) {
            case Closest -> Comparator.comparingDouble(this::reachTo);
            case Angle -> Comparator.comparingDouble(this::angleTo);
            case Weakest -> Comparator.comparingDouble(KillAuraPlus::healthOf)
                .thenComparingDouble(this::reachTo);
            case Toughest -> Comparator.comparingDouble(KillAuraPlus::healthOf).reversed()
                .thenComparingDouble(this::reachTo);
            // Anything one swing would finish, first, and among those the closest.
            // Spreading damage over a crowd leaves a crowd; taking the ones that
            // are already nearly dead takes them off the board.
            case Smart -> Comparator.comparing((Entity e) -> !lethal.contains(e))
                .thenComparingDouble(this::reachTo);
        };
    }

    private static double healthOf(Entity entity) {
        return entity instanceof LivingEntity living ? living.getHealth() : 0.0;
    }

    // Weapon

    private int bestSlot(Entity target) {
        int best = -1;
        float bestDamage = 0.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (requireWeapon.get() && !isWeapon(stack)) continue;

            float damage = damageTo(target, stack);
            if (damage > bestDamage) {
                bestDamage = damage;
                best = i;
            }
        }

        return best;
    }

    /**
     * Brings the best weapon in the main inventory into the hotbar when the hotbar has none.
     * Throttled to one attempt every two seconds: the move is three inventory clicks the server
     * has to answer before {@link #bestSlot} can see the weapon. Nothing is done with an item on
     * the cursor, or with an empty weapon list, where anything at all would count as a weapon.
     */
    private void fetchWeapon(Entity target, int selected, long now) {
        if (now < nextFetchMs || weapons.get().isEmpty()) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;

        int bestInv = -1;
        float bestDamage = 0.0f;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !isWeapon(stack)) continue;
            float damage = damageTo(target, stack);
            if (damage > bestDamage) {
                bestDamage = damage;
                bestInv = i;
            }
        }
        if (bestInv == -1) return;

        int dest = selected;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                dest = i;
                break;
            }
        }
        InvUtils.move().from(bestInv).toHotbar(dest);
        nextFetchMs = now + 2000;
        info("Weapon brought back from inventory slot %d to hotbar slot %d.", bestInv, dest);
    }

    /**
     * What this weapon would actually take off this entity.
     *
     * <p>Not the attack damage on the tooltip: Meteor's calculation runs the
     * enchantments against the target's type and then the target's own armour and
     * resistance over it. That is the whole reason the switch is worth having --
     * the sharpest sword in the bar is not the best answer to a skeleton if
     * another slot holds Smite.
     */
    private float damageTo(Entity target, ItemStack weapon) {
        if (weapon.isEmpty()) return 0.0f;

        try {
            return DamageUtils.getAttackDamage(mc.player, target, weapon);
        }
        catch (Exception e) {
            HunterBuddyAddon.LOG.warn("KillAuraPlus: could not weigh {} against {}",
                weapon.getItem(), EntityUtils.getName(target), e);
            return 0.0f;
        }
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (weapons.get().isEmpty()) return true;

        if (weapons.get().contains(Items.DIAMOND_SWORD) && stack.isIn(ItemTags.SWORDS)) return true;
        if (weapons.get().contains(Items.DIAMOND_AXE) && stack.getItem() instanceof AxeItem) return true;
        if (weapons.get().contains(Items.DIAMOND_PICKAXE) && stack.isIn(ItemTags.PICKAXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_SHOVEL) && stack.isIn(ItemTags.SHOVELS)) return true;
        if (weapons.get().contains(Items.DIAMOND_HOE) && stack.isIn(ItemTags.HOES)) return true;
        if (weapons.get().contains(Items.MACE) && stack.getItem() instanceof MaceItem) return true;

        return weapons.get().contains(Items.TRIDENT) && stack.getItem() instanceof TridentItem;
    }

    // Timing

    private boolean ready() {
        long now = System.currentTimeMillis();

        // No-delay: hit at the no-delay-cps rate, ignoring the attack cooldown and the charge
        // entirely -- no waiting for the bar to refill. High rate bats away a ghast fireball the
        // instant it is in range (low damage per hit); low rate lets the bar charge for real sword
        // damage. nextHitMs is set from that rate in attack().
        if (noDelay.get()) return now >= nextHitMs;

        if (now < nextHitMs) return false;

        if (timing.get() == Timing.Clicks) return true;
        if (!charged()) return false;
        if (!tpsSync.get()) return true;

        float tps = TickRate.INSTANCE.getTickRate();
        if (tps <= 0.0f || tps >= 20.0f) return true;

        // The bar on your screen fills in client ticks; the damage is worked out
        // on the server, in server ticks. At seventeen the server sees 0.875 of a
        // charge where the client shows a full one -- under the 0.9 a critical
        // needs, so every crit is silently lost. Meteor divides the base time
        // instead, which makes it swing sooner when the server is slower.
        return ticksSinceHit * (tps / 20.0f) + 0.5f >= mc.player.getAttackCooldownProgressPerTick();
    }

    private boolean charged() {
        return mc.player.getAttackCooldownProgress(0.5f) >= 1.0f;
    }

    /**
     * The conditions {@code Player.canCriticalAttack} checks, minus the ones this
     * module is about to arrange itself.
     *
     * <p>Sprint is left out when sprint-reset is on, because the packet that drops
     * it goes out a moment later and the server will see the swing land without
     * it. Blindness is left out because the client cannot read it reliably here;
     * a crit missed under blindness is a crit missed, not a swing lost.
     */
    private boolean crits() {
        if (mc.player.fallDistance <= 0.0) return false;
        if (mc.player.isOnGround() || mc.player.isClimbing()) return false;
        if (mc.player.isTouchingWater() || mc.player.hasVehicle()) return false;

        return sprintReset.get() || !mc.player.isSprinting();
    }

    private void attack(Entity target) {
        if (mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        boolean sprinting = mc.player.isSprinting();

        // A swing under nine tenths of charge cannot crit whatever the sprint says,
        // so dropping it there buys nothing and costs two packets. In the cooldown
        // timing the gate is already full charge, so this changes only the clicks
        // timing, where it was pure noise.
        boolean reset = sprintReset.get() && sprinting
            && mc.player.getAttackCooldownProgress(0.5f) > 0.9f;

        // Stop first, so the server works out the swing on a player who is not
        // sprinting and therefore may crit. Start again after, on both sides, so
        // the client, its lastSprinting and the server all leave this tick
        // agreeing -- which is the half the original tap of the key gets for free.
        if (reset) sprint(false);

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Both sides, unconditionally. Hitting a player makes the client drop its
        // own sprint (a mob does not, the client damage path returns false there),
        // and if the packet is skipped on that ground the next tick starts
        // sprinting again without sending anything -- lastSprinting never moved.
        // Server walking, client sprinting: a movement mismatch that grows every
        // tick until something notices.
        if (reset) {
            sprint(true);
            mc.player.setSprinting(true);
        }

        long now = System.currentTimeMillis();
        lastHitMs = now;
        ticksSinceHit = 0;

        if (noDelay.get()) {
            nextHitMs = now + (long) (1000.0 / Math.max(0.5, noDelayCps.get()));
        }
        else if (timing.get() == Timing.Clicks) {
            double low = Math.min(minCps.get(), maxCps.get());
            double high = Math.max(minCps.get(), maxCps.get());
            double cps = low + random.nextDouble() * (high - low);
            nextHitMs = now + (long) (1000.0 / Math.max(0.5, cps));
        }
        else {
            nextHitMs = now + (jitter.get() > 0 ? random.nextInt(jitter.get() + 1) : 0);
        }

        if (cycle.get()) hitThisRound.add(target);
    }

    private void sprint(boolean on) {
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
            on ? ClientCommandC2SPacket.Mode.START_SPRINTING : ClientCommandC2SPacket.Mode.STOP_SPRINTING));
    }

    private void release() {
        current = null;
        targets.clear();
        lethal.clear();
        hitThisRound.clear();

        // Only if the slot is still the one this module put you on. Otherwise
        // scrolling to food and right clicking makes running() go false, which
        // lands here, which drags you back to the sword and cancels the meal --
        // the exact thing hands-off exists to prevent, undone on the way out.
        boolean ours = mc.player != null && ourSlot != -1
            && mc.player.getInventory().getSelectedSlot() == ourSlot;

        if (swapped && swapBack.get() && previousSlot != -1 && ours) {
            InvUtils.swap(previousSlot, false);
        }

        swapped = false;
        previousSlot = -1;
        ourSlot = -1;
    }

    private Vec3d aimPoint(Entity target) {
        Vec3d feet = target.getEntityPos();

        return switch (aimAt.get()) {
            case Feet -> feet;
            case Torso -> feet.add(0.0, target.getHeight() / 2.0, 0.0);
            case Eyes -> target.getEyePos();
            // Inward, toward the middle of the box, and not along the ray. The
            // closest point sits exactly on the surface, so at a corner the ray
            // only grazes it and the float rounding of the yaw and pitch that go
            // in the packet decides at random whether it lands inside -- a missed
            // hitbox on arithmetic. Pushing the point further along the same ray,
            // which is the obvious fix, changes nothing at all: yaw comes from the
            // horizontal direction and pitch from the eye, and a point moved along
            // eye-to-point keeps both. It has to move sideways to move the ray.
            case Closest -> {
                Vec3d point = nearestPoint(target);
                Vec3d inward = target.getBoundingBox().getCenter().subtract(point);
                double length = inward.length();

                yield length < 1.0E-4
                    ? point
                    : point.add(inward.multiply(Math.min(0.05, length * 0.5) / length));
            }
        };
    }

    // Render

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (render.get() == RenderMode.Off) return;
        if (current == null || !current.isAlive() || mc.player == null) return;

        double x = MathHelper.lerp(event.tickDelta, current.lastX, current.getX());
        double y = MathHelper.lerp(event.tickDelta, current.lastY, current.getY());
        double z = MathHelper.lerp(event.tickDelta, current.lastZ, current.getZ());

        if (render.get() == RenderMode.Box || render.get() == RenderMode.Both) {
            box(event, x, y, z);
        }

        if (render.get() == RenderMode.Sweep || render.get() == RenderMode.Both) {
            switch (animation.get()) {
                case Sweep -> sweep(event, x, y, z);
                case Orbit -> orbit(event, x, y, z);
                case Heartbeat -> heartbeat(event, x, y, z);
            }
        }
    }

    private void box(Render3DEvent event, double x, double y, double z) {
        Box shape = current.getBoundingBox()
            .offset(-current.getX(), -current.getY(), -current.getZ())
            .offset(x, y, z);

        Color side = sideColor.get();
        Color line = lineColor.get();

        switch (boxStyle.get()) {
            case Fade -> {
                double t = MathHelper.clamp((System.currentTimeMillis() - lastHitMs) / 900.0, 0.0, 1.0);
                double f = 1.0 - t;
                side = scaled(sideColor.get(), 0.15 + 0.85 * f);
                line = scaled(lineColor.get(), 0.25 + 0.75 * f);
            }
            case Pulse -> {
                double f = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0);
                side = scaled(sideColor.get(), 0.25 + 0.75 * f);
                line = scaled(lineColor.get(), 0.35 + 0.65 * f);
            }
            case Shrink -> {
                double t = MathHelper.clamp((System.currentTimeMillis() - lastHitMs) / 400.0, 0.0, 1.0);
                shape = shape.expand(0.14 * (1.0 - t));
            }
            case Solid -> {
            }
        }

        event.renderer.box(shape, side, line, shapeMode.get(), 0);
    }

    /**
     * The ring that travels the height of the target.
     *
     * <p>A box says where something is; a moving band says the module is holding
     * it, and which one of a crowd it is holding, without any text. The skirt is
     * drawn on the trailing side and swells at mid-travel, so the ring reads as
     * having a direction rather than blinking between two heights.
     */
    private void sweep(Render3DEvent event, double x, double y, double z) {
        double height = Math.max(0.4, current.getHeight());
        double radius = Math.max(0.15, current.getWidth() * sweepScale.get());

        double period = period();
        double phase = (System.currentTimeMillis() % (long) period) / period;
        boolean rising = phase < 0.5;
        double travel = rising ? phase * 2.0 : 2.0 - phase * 2.0;
        double eased = travel < 0.5 ? 2.0 * travel * travel : 1.0 - Math.pow(-2.0 * travel + 2.0, 2.0) / 2.0;

        double ringY = y + height * eased;

        // Widest halfway up, nothing at either end, and on the side the ring came
        // from. Without the sign flip the skirt leads the ring on the way down and
        // the whole thing looks like it is falling apart.
        double lip = height / 3.0 * (eased > 0.5 ? 1.0 - eased : eased) * (rising ? -1.0 : 1.0);

        Color solid = solid();
        Color clear = clear();
        Color edge = lineColor.get();

        int segments = sweepSegments.get();
        double step = Math.PI * 2.0 / segments;

        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;

            double x1 = x + Math.cos(a1) * radius;
            double z1 = z + Math.sin(a1) * radius;
            double x2 = x + Math.cos(a2) * radius;
            double z2 = z + Math.sin(a2) * radius;

            // Renderer3D.quad hands its four colours to the vertices in the order
            // bottomLeft, topLeft, topRight, bottomRight -- not the order they are
            // written. Passed the obvious way the fade ran along each segment and
            // the ring came out as thirty-two saw teeth.
            event.renderer.quad(
                x1, ringY + lip, z1,
                x2, ringY + lip, z2,
                x2, ringY, z2,
                x1, ringY, z1,
                clear, solid, solid, clear);

            event.renderer.line(x1, ringY, z1, x2, ringY, z2, edge);
        }
    }

    /**
     * Two rings of arcs turning against each other.
     *
     * <p>A gyroscope reads as a machine holding something, which is what the
     * module is doing. The two directions matter: one ring alone reads as
     * decoration, two turning against each other read as a mechanism. Every hit
     * swells both rings and reverses them, so the swing is visible in the drawing
     * without a number and without a flash.
     */
    private void orbit(Render3DEvent event, double x, double y, double z) {
        double height = Math.max(0.4, current.getHeight());
        double radius = Math.max(0.15, current.getWidth() * sweepScale.get());
        double period = period();

        long now = System.currentTimeMillis();

        // The jolt: a quarter wider and running the other way for three tenths of
        // a second. Reversing rather than pausing is what makes it read as a knock
        // rather than as a stutter in the animation.
        double since = MathHelper.clamp((now - lastHitMs) / 300.0, 0.0, 1.0);
        boolean knocked = since < 1.0;
        double swell = 1.0 + 0.25 * (1.0 - since);
        double way = knocked ? -1.0 : 1.0;

        // The angle is carried, not recomputed from the clock. Read off now %
        // period it is a position, so flipping the sign mirrors it and the rings
        // jump to the other side of the body on the very frame of the hit. Carried,
        // the sign only decides which way the next frame advances: the rings are
        // wherever they were and simply start going back.
        if (orbitLastMs == 0) orbitLastMs = now;
        orbitAngle += way * (now - orbitLastMs) / period * Math.PI * 2.0;
        orbitLastMs = now;

        // Kept inside one turn. Nothing on screen changes -- it is the same angle --
        // but the figure stops growing for as long as the module is on.
        orbitAngle %= Math.PI * 2.0;

        Color solid = solid();
        Color clear = clear();
        int perArc = Math.max(4, sweepSegments.get() / 6);
        double arc = Math.toRadians(50.0);
        double band = height * 0.10;

        for (int i = 0; i < 3; i++) {
            double spaced = i * Math.PI * 2.0 / 3.0;

            // Rising angle is clockwise seen from above, so the top ring turns
            // with the sign and the bottom against it. The leading end carries the
            // sign too: it is the end in the direction of travel, which is what
            // makes the fade behind it read as a trail rather than as a smear --
            // including while the knock has both rings running backwards.
            double top = orbitAngle + spaced;
            arc(event, x, z, radius * swell, y + height * 0.62, band,
                top, top + arc * way, perArc, solid, clear);

            double bottom = -orbitAngle + spaced;
            arc(event, x, z, radius * 0.75 * swell, y + height * 0.28, band,
                bottom, bottom - arc * way, perArc, solid, clear);
        }
    }

    /**
     * One arc of an orbit ring.
     *
     * @param from the trailing end, drawn clear; {@code to} is the leading end, drawn solid
     */
    private void arc(Render3DEvent event, double cx, double cz, double radius, double centreY,
                     double band, double from, double to, int segments, Color solid, Color clear) {
        double half = band / 2.0;
        double bottom = centreY - half;
        double top = centreY + half;
        double step = (to - from) / segments;

        for (int i = 0; i < segments; i++) {
            double a = from + step * i;
            double b = a + step;

            // Alpha rises along the arc, so the trailing end fades out rather than
            // stopping on a hard edge.
            Color ca = blend(clear, solid, (double) i / segments);
            Color cb = blend(clear, solid, (double) (i + 1) / segments);

            double ax = cx + Math.cos(a) * radius;
            double az = cz + Math.sin(a) * radius;
            double bx = cx + Math.cos(b) * radius;
            double bz = cz + Math.sin(b) * radius;

            event.renderer.quad(
                ax, bottom, az,
                ax, top, az,
                bx, top, bz,
                bx, bottom, bz,
                ca, cb, cb, ca);
        }

        // The leading edge, as a bar. It is the only hard line in the shape and it
        // is what the eye follows round.
        double lx = cx + Math.cos(to) * radius;
        double lz = cz + Math.sin(to) * radius;
        event.renderer.line(lx, bottom, lz, lx, top, lz, lineColor.get());
    }

    /**
     * A ring on the ground that fills with the cooldown, and a wave on every hit.
     *
     * <p>The other two say "this one". This one says when. The ring's brightness
     * is the attack bar, so the moment the next swing does full damage is readable
     * without taking your eyes off the fight, and the wave leaving the target is
     * the swing that has just gone.
     */
    private void heartbeat(Render3DEvent event, double x, double y, double z) {
        double radius = Math.max(0.15, current.getWidth() * sweepScale.get());
        int segments = sweepSegments.get();
        double step = Math.PI * 2.0 / segments;
        double ground = y + 0.02;

        SettingColor line = lineColor.get();
        float charge = mc.player == null ? 1.0f : mc.player.getAttackCooldownProgress(0.5f);
        Color ring = new Color(line.r, line.g, line.b,
            (int) MathHelper.clamp(line.a * charge, 0.0f, 255.0f));

        for (int i = 0; i < segments; i++) {
            double a = i * step;
            double b = a + step;

            event.renderer.line(
                x + Math.cos(a) * radius, ground, z + Math.sin(a) * radius,
                x + Math.cos(b) * radius, ground, z + Math.sin(b) * radius,
                ring);
        }

        double since = (System.currentTimeMillis() - lastHitMs) / 450.0;
        if (lastHitMs <= 0 || since >= 1.0) return;

        // One wave, always the last one: a second swing overtakes the first rather
        // than stacking with it, which is also what it feels like to land one.
        double wave = radius * (1.0 + 1.2 * since);
        double width = radius * 0.15;
        double inner = wave - width / 2.0;
        double outer = wave + width / 2.0;
        double lift = y + 0.03;

        SettingColor base = sideColor.get();
        Color full = new Color(base.r, base.g, base.b,
            (int) MathHelper.clamp(Math.min(255, base.a * 3) * (1.0 - since), 0.0, 255.0));
        Color gone = new Color(base.r, base.g, base.b, 0);

        for (int i = 0; i < segments; i++) {
            double a = i * step;
            double b = a + step;

            event.renderer.quad(
                x + Math.cos(a) * inner, lift, z + Math.sin(a) * inner,
                x + Math.cos(a) * outer, lift, z + Math.sin(a) * outer,
                x + Math.cos(b) * outer, lift, z + Math.sin(b) * outer,
                x + Math.cos(b) * inner, lift, z + Math.sin(b) * inner,
                gone, gone, full, full);
        }
    }

    /** The animation period, shortened by the target's health when asked for. */
    private double period() {
        double period = sweepPeriod.get();

        if (hurry.get() && current instanceof LivingEntity living && living.getMaxHealth() > 0.0f) {
            double left = MathHelper.clamp(living.getHealth() / living.getMaxHealth(), 0.0f, 1.0f);
            period *= 0.35 + 0.65 * left;
        }

        return period;
    }

    private Color solid() {
        SettingColor base = sideColor.get();
        return new Color(base.r, base.g, base.b, Math.min(255, base.a * 3));
    }

    private Color clear() {
        SettingColor base = sideColor.get();
        return new Color(base.r, base.g, base.b, 0);
    }

    private static Color blend(Color from, Color to, double t) {
        return new Color(to.r, to.g, to.b,
            (int) MathHelper.clamp(from.a + (to.a - from.a) * t, 0.0, 255.0));
    }

    private static Color scaled(SettingColor base, double factor) {
        return new Color(base.r, base.g, base.b,
            (int) MathHelper.clamp(base.a * factor, 0.0, 255.0));
    }

    @Override
    public String getInfoString() {
        return current == null ? null : EntityUtils.getName(current);
    }

    public Entity target() {
        return current;
    }

    public boolean hasActiveTarget() {
        return isActive() && current != null;
    }

    public boolean isAboutToAttack() {
        return isActive() && current != null && switchTimer <= 0 && charged();
    }

    /**
     * The yaw the walk has to be turned by, or {@code NaN} while there is nothing
     * to correct.
     *
     * <p>The window is the tick the rotation was posted plus the hold ticks after
     * it, because that is exactly how long Meteor keeps re-sending the same
     * rotation once nothing new is posted -- and every one of those packets is a
     * packet whose yaw the server predicts the movement from.
     *
     * <p>It reports this module's own aim. If another module posts a rotation at a
     * higher priority in the same tick, that one is what goes out and this
     * correction turns the walk by the wrong angle; nothing in the addon does that
     * today, and the aura sits at fifty.
     */
    public static float moveFixYaw() {
        KillAuraPlus aura = Modules.get().get(KillAuraPlus.class);

        if (aura == null || !aura.isActive()) return Float.NaN;
        if (Float.isNaN(aura.lastAimYaw)) return Float.NaN;
        if (aura.ticksSinceAim > Config.get().rotationHoldTicks.get()) return Float.NaN;

        return aura.lastAimYaw;
    }

    public enum Priority {
        Smart,
        Closest,
        Angle,
        Weakest,
        Toughest
    }

    public enum Timing {
        Cooldown,
        Clicks
    }

    public enum RotationMode {
        Off,
        OnHit,
        Always
    }

    public enum Aim {
        Feet,
        Torso,
        Eyes,
        Closest
    }

    public enum Animation {
        Sweep,
        Orbit,
        Heartbeat
    }

    public enum RenderMode {
        Off,
        Box,
        Sweep,
        Both
    }

    public enum BoxStyle {
        Solid,
        Fade,
        Pulse,
        Shrink
    }
}
