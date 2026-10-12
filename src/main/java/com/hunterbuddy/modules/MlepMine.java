package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mine.BreakMath;
import com.hunterbuddy.modules.mine.EnemyBlocks;
import com.hunterbuddy.modules.mine.MineRender;
import com.hunterbuddy.modules.mine.ToolHold;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.util.GrimBreakBalance;
import com.hunterbuddy.util.SessionStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class MlepMine extends Module {
    public enum SpeedmineMode {
        PACKET,
        DAMAGE
    }

    public enum Swap {
        NORMAL,
        SILENT,
        OFF
    }

    public enum Swing {
        NONE,
        MINER,
        HAMMER
    }

    public enum RenderStyle {
        CLASSIC,
        FILL,
        PULSE
    }

    private static final String PREFIX = "§7[§bh-mine§7] ";
    private static final int WAITING_LIMIT = 16;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgHunt = this.settings.createGroup("Auto Mine");
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final List<MiningData> active = new ArrayList<>();
    private final List<MiningData> waiting = new ArrayList<>();

    private final Setting<SpeedmineMode> mode = this.sgGeneral.add(new EnumSetting.Builder<SpeedmineMode>()
        .name("mode")
        .description("PACKET breaks with digging packets and lets the server time the break; DAMAGE only cleans up after the normal client break.")
        .defaultValue(SpeedmineMode.PACKET)
        .build());

    private final Setting<Boolean> keepWhileUsing = this.sgGeneral.add(new BoolSetting.Builder()
        .name("multitask")
        .description("Keep mining while you eat or use an item.")
        .defaultValue(false)
        .visible(this::packetMode)
        .build());

    private final Setting<Boolean> doubleBreak = this.sgGeneral.add(new BoolSetting.Builder()
        .name("double-break")
        .description("Work on two blocks at once.")
        .defaultValue(true)
        .visible(this::packetMode)
        .build());

    private final Setting<Double> range = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Farthest block, from your eyes, that is mined.")
        .defaultValue(6.0)
        .min(0.1)
        .sliderRange(0.1, 6.0)
        .visible(this::packetMode)
        .build());

    private final Setting<Double> speed = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Share of the break bar that must be full before the closing packet goes out.")
        .defaultValue(1.05)
        .min(0.1)
        .sliderRange(0.1, 1.1)
        .build());

    private final Setting<Boolean> instant = this.sgGeneral.add(new BoolSetting.Builder()
        .name("instant")
        .description("Keeps the spot you just cleared hot: a block placed back there breaks again on its own, with no click. Turn it off and a cleared spot is forgotten the moment it breaks.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> instantWindow = this.sgGeneral.add(new IntSetting.Builder()
        .name("instant-window")
        .description("How long a cleared spot stays hot, in milliseconds. 0 never forgets, which is what the server does anyway. After a spot goes cold the next block there needs a click and breaks at normal speed.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 30000)
        .visible(() -> this.instant.get())
        .build());

    private final Setting<Keybind> instantKey = this.sgGeneral.add(new KeybindSetting.Builder()
        .name("instant-toggle-key")
        .description("Key that switches instant on and off.")
        .defaultValue(Keybind.none())
        .build());

    private final Setting<Boolean> queue = this.sgGeneral.add(new BoolSetting.Builder()
        .name("queue")
        .description("Click several blocks and break them one after another. Nothing is sent for a waiting block, so the server only ever sees one break at a time.")
        .defaultValue(false)
        .visible(this::packetMode)
        .onChanged(value -> {
            if (!value) {
                this.waiting.clear();
            }
        })
        .build());

    private final Setting<Keybind> queueClearKey = this.sgGeneral.add(new KeybindSetting.Builder()
        .name("queue-clear-key")
        .description("Key that drops every block still waiting.")
        .defaultValue(Keybind.none())
        .visible(() -> this.queue.get() && this.packetMode())
        .build());

    private final Setting<Boolean> queueThrough = this.sgGeneral.add(new BoolSetting.Builder()
        .name("queue-through")
        .description("Clicking the block being mined lines up the first free block behind it along your crosshair, so a whole row can be queued before the front one breaks. One block per click.")
        .defaultValue(false)
        .visible(() -> this.queue.get() && this.packetMode())
        .build());

    private final Setting<Boolean> vanillaOneShot = this.sgGeneral.add(new BoolSetting.Builder()
        .name("vanilla-one-shot")
        .description("Leave any block your held item breaks in one hit to the normal client break, which is faster there and sends nothing an anticheat counts.")
        .defaultValue(true)
        .visible(this::packetMode)
        .build());

    private final Setting<List<Block>> leaveToVanilla = this.sgGeneral.add(new BlockListSetting.Builder()
        .name("leave-to-vanilla")
        .description("Blocks this module never touches; the normal client break takes them.")
        .visible(this::packetMode)
        .build());

    private final Setting<Boolean> persistent = this.sgGeneral.add(new BoolSetting.Builder()
        .name("persistent")
        .description("Keep the module on: it refuses to be switched off until you untick this or disconnect.")
        .defaultValue(true)
        .visible(this::packetMode)
        .onChanged(value -> {
            if (value && this.mc.player != null) {
                this.mc.player.sendMessage(Text.literal("§7[§bh-mine§7] §aPersistent mode enabled! Module cannot be disabled until you turn this off or disconnect."), false);
            }
        })
        .build());

    private final Setting<Swap> autoSwap = this.sgGeneral.add(new EnumSetting.Builder<Swap>()
        .name("auto-swap")
        .description("Hold the best hotbar tool from the first packet until the block is gone. Only hotbar slots can be selected.")
        .defaultValue(Swap.SILENT)
        .visible(this::packetMode)
        .build());

    private final Setting<Boolean> rotate = this.sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Turn towards the block when the closing packet goes out.")
        .defaultValue(false)
        .visible(this::packetMode)
        .build());

    private final Setting<Boolean> switchReset = this.sgGeneral.add(new BoolSetting.Builder()
        .name("switch-reset")
        .description("Restart the break bars when you change hotbar slot yourself.")
        .defaultValue(false)
        .visible(this::packetMode)
        .build());

    private final Setting<Boolean> grim = this.sgGeneral.add(new BoolSetting.Builder()
        .name("grim")
        .description("Follow the Grim anticheat's break timing.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> grimV3 = this.sgGeneral.add(new BoolSetting.Builder()
        .name("grim-v3")
        .description("Use the newer Grim digging sequence.")
        .defaultValue(true)
        .visible(() -> this.grim.get())
        .build());

    private final Setting<Boolean> startOnly = this.sgGeneral.add(new BoolSetting.Builder()
        .name("mining-fix")
        .description("Send only the start packet in the opening burst.")
        .defaultValue(false)
        .visible(() -> this.grim.get() && this.grimV3.get())
        .build());

    private final Setting<Boolean> grimBalanceLog = this.sgGeneral.add(new BoolSetting.Builder()
        .name("grim-balance-log")
        .description("Write a line per block to the game log with the predicted cost, both balances and how long the server took to confirm the break.")
        .defaultValue(false)
        .visible(() -> this.grim.get())
        .build());

    private final Setting<Swing> swing = this.sgGeneral.add(new EnumSetting.Builder<Swing>()
        .name("swing")
        .description("Arm animation while mining: NONE swings only inside the packet bursts, MINER swings every tick a block is worked, HAMMER swings once on each tick that sends a dig.")
        .defaultValue(Swing.NONE)
        .build());

    private final Setting<Keybind> huntKey = this.sgHunt.add(new KeybindSetting.Builder()
        .name("auto-mine-key")
        .description("Key that switches auto-mine on and off.")
        .defaultValue(Keybind.none())
        .build());

    private final Setting<Boolean> hunt = this.sgHunt.add(new BoolSetting.Builder()
        .name("auto-mine")
        .description("Mine the blocks around nearby enemies on your own.")
        .defaultValue(false)
        .build());

    private final Setting<Double> huntRadius = this.sgHunt.add(new DoubleSetting.Builder()
        .name("enemy-range")
        .description("How close an enemy must be.")
        .defaultValue(5.0)
        .min(1.0)
        .sliderRange(1.0, 10.0)
        .visible(() -> this.hunt.get())
        .build());

    private final Setting<Boolean> visibleFaceOnly = this.sgHunt.add(new BoolSetting.Builder()
        .name("strict-direction")
        .description("Only mine an enemy block through a face you can see.")
        .defaultValue(false)
        .visible(() -> this.hunt.get())
        .build());

    private final Setting<Boolean> includeHead = this.sgHunt.add(new BoolSetting.Builder()
        .name("target-head")
        .description("Also mine the block over an enemy's head.")
        .defaultValue(true)
        .visible(() -> this.hunt.get())
        .build());

    private final Setting<Boolean> huntAim = this.sgHunt.add(new BoolSetting.Builder()
        .name("auto-rotate")
        .description("Turn towards enemy blocks (needs rotate).")
        .defaultValue(false)
        .visible(() -> this.hunt.get())
        .build());

    private final Setting<Boolean> standUp = this.sgHunt.add(new BoolSetting.Builder()
        .name("anti-crawl")
        .description("When you are forced to crawl, mine the block over your head to stand up.")
        .defaultValue(false)
        .visible(() -> this.hunt.get())
        .build());

    private final Setting<SettingColor> queueColor = this.sgRender.add(new ColorSetting.Builder()
        .name("queue-color")
        .description("Colour of the waiting blocks.")
        .defaultValue(new SettingColor(120, 180, 255, 60))
        .visible(() -> this.queue.get())
        .build());

    private final Setting<Boolean> render = this.sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw the blocks being mined.")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> shapeMode = this.sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How boxes are drawn.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> mineColor = this.sgRender.add(new ColorSetting.Builder()
        .name("mine-color")
        .description("Colour of a block being mined.")
        .defaultValue(new SettingColor(0, 0, 255, 255))
        .visible(this::packetMode)
        .build());

    private final Setting<SettingColor> doneColor = this.sgRender.add(new ColorSetting.Builder()
        .name("done-color")
        .description("Colour of a block that is ready to break.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .visible(this::packetMode)
        .build());

    private final Setting<Integer> fadeTime = this.sgRender.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Fade-out time in milliseconds.")
        .defaultValue(250)
        .min(0)
        .sliderRange(0, 1000)
        .visible(() -> false)
        .build());

    private final Setting<RenderStyle> style = this.sgRender.add(new EnumSetting.Builder<RenderStyle>()
        .name("style")
        .description("CLASSIC grows a box from the middle, FILL raises a level inside the block, PULSE is FILL with a breath on it.")
        .defaultValue(RenderStyle.FILL)
        .visible(() -> this.render.get())
        .build());

    private final Setting<Double> pulseRate = this.sgRender.add(new DoubleSetting.Builder()
        .name("pulse-rate")
        .description("Breaths per second.")
        .defaultValue(1.2)
        .min(0.1)
        .sliderRange(0.1, 3.0)
        .visible(() -> this.render.get() && this.style.get() == RenderStyle.PULSE)
        .build());

    private final Setting<Boolean> blockOutline = this.sgRender.add(new BoolSetting.Builder()
        .name("block-outline")
        .description("Faint outline of the whole block behind the progress.")
        .defaultValue(true)
        .visible(() -> this.render.get())
        .build());

    private final Setting<Boolean> queueChain = this.sgRender.add(new BoolSetting.Builder()
        .name("queue-chain")
        .description("Link the waiting blocks with a line, in the order they will be mined.")
        .defaultValue(true)
        .visible(() -> this.render.get() && this.queue.get())
        .build());

    private final Setting<Boolean> queueNumbers = this.sgRender.add(new BoolSetting.Builder()
        .name("queue-numbers")
        .description("Show the rank of each waiting block; 1 is the next one.")
        .defaultValue(true)
        .visible(() -> this.render.get() && this.queue.get())
        .build());

    private final ToolHold tool = new ToolHold(() -> this.autoSwap.get());
    private final MineRender renderer = new MineRender();
    private final GrimBreakBalance balance = new GrimBreakBalance(this::costAt);
    private final Map<BlockPos, Long> stopSentAt = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
            return this.size() > 32;
        }
    };

    private int capacity = 1;
    private BlockPos blockedByPress;
    private int lastEventTick = -100;
    private long lastStopAt;
    private boolean lastWasInstant;
    private boolean workedThisTick;
    private boolean swungThisTick;
    private boolean huntKeyDown;
    private boolean queueClearKeyDown;
    private boolean instantKeyDown;
    private long lastAutoAt;
    private long lastCrawlAt;

    public MlepMine() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "h-mine", "Breaks blocks with packets, faster and more quietly than holding the button.");
    }

    public class MiningData {
        private final BlockPos position;
        private final Direction face;
        private boolean attempted;
        private long attemptAt;
        private long emptySince;
        private boolean resume;
        private float damage;
        private float previousDamage;
        private boolean started;

        public MiningData(BlockPos pos, Direction direction) {
            this.position = pos.toImmutable();
            this.face = direction;
        }

        public BlockPos pos() {
            return this.position;
        }

        public float progress() {
            return this.damage;
        }

        public float previousProgress() {
            return this.previousDamage;
        }

        private void markAttempt(boolean value) {
            this.attempted = value;
            if (value) {
                this.attemptAt = System.currentTimeMillis();
            }
        }

        private void refreshAttempt() {
            this.attemptAt = System.currentTimeMillis();
        }

        private void seenEmpty(long now) {
            if (this.emptySince == 0L) {
                this.emptySince = now;
                this.resume = true;
            }
        }

        private void seenFull() {
            this.emptySince = 0L;
        }

        private boolean takeResume() {
            boolean value = this.resume;
            this.resume = false;
            return value;
        }

        private boolean emptyFor(long now, long window) {
            return this.emptySince != 0L && now - this.emptySince >= window;
        }

        private boolean attemptOlder(long ms) {
            return System.currentTimeMillis() - this.attemptAt >= ms;
        }

        private void addDamage(float amount) {
            this.previousDamage = this.damage;
            this.damage += amount;
        }

        private void restart() {
            this.started = false;
            this.damage = 0.0F;
        }

        private BlockState state() {
            return MlepMine.this.mc.world.getBlockState(this.position);
        }

        private int bestTool() {
            return BreakMath.bestToolSlotOrNone(MlepMine.this.mc.player, this.state());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MiningData data && this.position.equals(data.position);
        }

        @Override
        public int hashCode() {
            return this.position.hashCode();
        }
    }

    private boolean packetMode() {
        return this.mode.get() == SpeedmineMode.PACKET;
    }

    private static boolean cleared(BlockState state) {
        return state.isAir() || state.getBlock() instanceof FluidBlock;
    }

    private void chat(String text) {
        if (this.mc.player != null) {
            this.mc.player.sendMessage(Text.literal(PREFIX + text), false);
        }
    }

    private void send(Packet<?> packet) {
        if (this.mc.getNetworkHandler() != null) {
            this.mc.getNetworkHandler().sendPacket(packet);
        }
    }

    private void sendAction(PlayerActionC2SPacket.Action action, BlockPos pos, Direction face) {
        this.send(new PlayerActionC2SPacket(action, pos, face));
    }

    private void swingOnce() {
        if (this.swungThisTick || this.mc.player == null) {
            return;
        }

        this.swungThisTick = true;
        this.mc.player.swingHand(Hand.MAIN_HAND);
    }

    private double costAt(BlockPos pos) {
        if (this.mc.player == null || this.mc.world == null) {
            return 0.0;
        }

        BlockState state = this.mc.world.getBlockState(pos);
        if (state.isAir()) {
            return Double.POSITIVE_INFINITY;
        }

        return this.breakDelta(state, pos);
    }

    private float breakDelta(BlockState state, BlockPos pos) {
        if (this.autoSwap.get() == Swap.OFF) {
            return state.calcBlockBreakingDelta(this.mc.player, this.mc.world, pos);
        }

        return BreakMath.delta(this.mc.player, state, this.mc.world, pos);
    }

    private double distSq(BlockPos pos) {
        return this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
    }

    private boolean grimDelay() {
        return this.grim.get() && System.currentTimeMillis() - this.lastStopAt <= 280L;
    }

    private boolean solidLeft() {
        for (MiningData d : this.active) {
            if (!cleared(d.state())) {
                return true;
            }
        }

        return false;
    }

    private boolean busy(BlockPos pos) {
        for (MiningData d : this.active) {
            if (d.position.equals(pos)) {
                return true;
            }
        }

        return false;
    }

    private boolean waitingHas(BlockPos pos) {
        for (MiningData d : this.waiting) {
            if (d.position.equals(pos)) {
                return true;
            }
        }

        return false;
    }

    private boolean leaveToVanilla(BlockState state, BlockPos pos) {
        if (cleared(state)) {
            return false;
        }

        if (this.leaveToVanilla.get().contains(state.getBlock())) {
            return true;
        }

        return this.vanillaOneShot.get() && state.calcBlockBreakingDelta(this.mc.player, this.mc.world, pos) >= 1.0F;
    }

    private boolean packetMined(MiningData d) {
        return this.active.size() == 2 && this.active.get(1) == d;
    }

    private MiningData first() {
        return this.active.isEmpty() ? null : this.active.get(0);
    }

    private void addFirst(MiningData d) {
        while (this.active.size() >= this.capacity && !this.active.isEmpty()) {
            MiningData dropped = this.active.remove(this.active.size() - 1);
            this.cancel(dropped);
        }

        this.active.add(0, d);
    }

    private void syncCapacity() {
        this.capacity = this.doubleBreak.get() ? 2 : 1;
        while (this.active.size() > this.capacity) {
            MiningData dropped = this.active.remove(this.active.size() - 1);
            this.cancel(dropped);
        }
    }

    private void resetAutoBlocks() {
        this.lastAutoAt = 0L;
        this.lastCrawlAt = 0L;
    }

    @Override
    public void toggle() {
        if (this.isActive() && this.persistent.get() && this.mc.getNetworkHandler() != null) {
            this.chat("§cCannot disable while Persistent mode is active! Disable Persistent first or disconnect from server.");
            return;
        }

        super.toggle();
    }

    @Override
    public void onActivate() {
        this.active.clear();
        this.capacity = this.doubleBreak.get() ? 2 : 1;
        this.tool.forget();
        this.tool.prepare();
        this.resetAutoBlocks();
    }

    @Override
    public void onDeactivate() {
        if (this.persistent.get() && this.mc.getNetworkHandler() != null) {
            return;
        }

        this.active.clear();
        this.waiting.clear();
        this.blockedByPress = null;
        this.renderer.clear();
        this.tool.shutDown();
        this.resetAutoBlocks();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.active.clear();
        this.waiting.clear();
        this.blockedByPress = null;
        this.renderer.clear();
        this.tool.forget();
        this.resetAutoBlocks();
        this.balance.reset();
        this.stopSentAt.clear();
    }

    private void handleKeys() {
        boolean free = this.mc.currentScreen == null;

        boolean autoPressed = free && this.huntKey.get().isPressed();
        if (autoPressed && !this.huntKeyDown) {
            this.hunt.set(!this.hunt.get());
            this.chat("§fAuto-mine " + (this.hunt.get() ? "§aenabled" : "§cdisabled"));
        }
        this.huntKeyDown = autoPressed;

        boolean clearPressed = free && this.queueClearKey.get().isPressed();
        if (clearPressed && !this.queueClearKeyDown) {
            int n = this.waiting.size();
            this.waiting.clear();
            this.blockedByPress = null;
            this.chat("§fQueue cleared (" + n + " block(s))");
        }
        this.queueClearKeyDown = clearPressed;

        boolean instantPressed = free && this.instantKey.get().isPressed();
        if (instantPressed && !this.instantKeyDown) {
            this.instant.set(!this.instant.get());
            if (!this.instant.get()) {
                this.active.clear();
            }
            this.chat("§fInstant mining " + (this.instant.get() ? "§aenabled" : "§cdisabled"));
        }
        this.instantKeyDown = instantPressed;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        this.workedThisTick = false;
        this.swungThisTick = false;

        if (this.mc.player == null || this.mc.player.isCreative() || this.mc.player.isSpectator()) {
            return;
        }

        this.handleKeys();

        if (this.mode.get() == SpeedmineMode.DAMAGE) {
            return;
        }

        this.syncCapacity();

        if (this.hunt.get() && this.packetMode()) {
            if (this.runAutoMine()) {
                return;
            }
        }

        if (!this.active.isEmpty()) {
            if (this.workActive()) {
                return;
            }
        }

        this.promoteWaiting();
        this.tool.release(this.solidLeft(), pos -> this.mc.world == null || cleared(this.mc.world.getBlockState(pos)));

        if (this.workedThisTick && this.swing.get() == Swing.MINER) {
            this.swingOnce();
        }
    }

    private boolean runAutoMine() {
        int cap = this.doubleBreak.get() ? 2 : 1;
        long now = System.currentTimeMillis();

        if (this.standUp.get() && this.mc.player.getPose() == EntityPose.SWIMMING && this.active.size() < cap
            && now - this.lastCrawlAt >= 100L) {
            BlockPos above = EnemyBlocks.crawlBlock(this.range.get());
            if (above != null && !this.busy(above)) {
                if (this.huntAim.get() && this.rotate.get()) {
                    this.aimAtPoint(Vec3d.ofCenter(above));
                }

                this.queueMiningData(new MiningData(above, Direction.DOWN));
                this.lastCrawlAt = now;
                if (this.active.isEmpty()) {
                    return true;
                }
            }
        }

        PlayerEntity target = EnemyBlocks.closestEnemy(this.huntRadius.get());
        if (target != null) {
            if (this.active.size() < cap && now - this.lastAutoAt >= 250L) {
                BlockPos block = EnemyBlocks.enemyBlock(target, this.range.get(), this.includeHead.get(), this::busy);
                if (block != null && !this.busy(block)) {
                    Direction face = EnemyBlocks.facing(block);
                    if (this.huntAim.get() && this.rotate.get()) {
                        this.aimAtPoint(Vec3d.ofCenter(block));
                    }

                    this.queueMiningData(new MiningData(block, face));
                    this.lastAutoAt = now;
                }
            }
        }

        return false;
    }

    private boolean workActive() {
        long now = System.currentTimeMillis();
        int window = this.instantWindow.get();
        List<MiningData> dropped = new ArrayList<>();

        for (MiningData d : new ArrayList<>(this.active)) {
            BlockState state = d.state();
            if (cleared(state)) {
                d.refreshAttempt();
                d.seenEmpty(now);
                boolean stayHot = this.instant.get() && (window <= 0 || !d.emptyFor(now, window));
                if (!stayHot) {
                    dropped.add(d);
                    continue;
                }
            } else {
                d.seenFull();
            }

            BlockState current = d.state();
            if (!this.packetMined(d) || (!cleared(current) && (!d.attempted || !d.attemptOlder(500L)))) {
                d.addDamage(this.breakDelta(current, d.position));
                this.workedThisTick = true;
                if (this.packetMined(d) && d.damage >= 1.0F && d.bestTool() != -1) {
                    if (this.mc.player.isUsingItem() && !this.keepWhileUsing.get()) {
                        return true;
                    }

                    if (!d.attempted) {
                        d.markAttempt(true);
                    }
                }
            } else {
                dropped.add(d);
            }
        }

        this.active.removeAll(dropped);

        MiningData p = this.first();
        if (p == null) {
            return false;
        }

        if (this.distSq(p.position) > this.range.get() * this.range.get()) {
            this.active.remove(p);
            return false;
        }

        if (cleared(p.state())) {
            return false;
        }

        if (p.damage >= this.speed.get() && p.attempted && p.attemptOlder(500L)) {
            this.cancel(p);
            this.active.remove(p);
        }

        if (p.damage >= this.speed.get() && (!this.grim.get() || this.balance.canFinish(p.position, now))) {
            if (this.mc.player.isUsingItem() && !this.keepWhileUsing.get()) {
                return true;
            }

            if (p.takeResume()) {
                this.tool.holdFor(p.state(), p.position);
            }

            this.finish(p);
            if (!p.attempted) {
                p.markAttempt(true);
            }

            if (!this.instant.get()) {
                this.active.remove(p);
            }
        }

        return false;
    }

    private void promoteWaiting() {
        if (this.waiting.isEmpty() || !this.packetMode() || this.mc.player == null || this.mc.world == null) {
            return;
        }

        if ((this.mc.player.isUsingItem() && !this.keepWhileUsing.get()) || this.grimDelay()) {
            return;
        }

        if (this.queue.get() && this.solidLeft()) {
            return;
        }

        while (!this.waiting.isEmpty()) {
            MiningData next = this.waiting.remove(0);
            BlockState state = this.mc.world.getBlockState(next.position);
            if (cleared(state)) {
                continue;
            }

            if (state.getHardness(this.mc.world, next.position) < 0.0F) {
                continue;
            }

            if (this.distSq(next.position) > this.range.get() * this.range.get()) {
                continue;
            }

            this.queueMiningData(new MiningData(next.position, next.face));
            return;
        }
    }

    private void aimAtPoint(Vec3d point) {
        if (this.mc.player == null) {
            return;
        }

        float[] r = EnemyBlocks.aimAt(this.mc.player.getEyePos(), point);
        if (this.grim.get()) {
            RotationUtils.getInstance().setRotationSilent(r[0], r[1]);
        } else {
            RotationUtils.getInstance().setRotationFull(r[0], r[1]);
        }
    }

    private boolean begin(MiningData d) {
        if (d.started) {
            return false;
        }

        d.started = true;
        BlockState state = d.state();
        this.tool.holdFor(state, d.position);

        float delta = this.breakDelta(state, d.position);
        boolean instantBreak = delta >= 1.0F;
        boolean afterInstant = this.lastWasInstant;
        this.lastWasInstant = instantBreak;

        BlockPos pos = d.position;
        Direction face = d.face;
        PlayerActionC2SPacket.Action start = PlayerActionC2SPacket.Action.START_DESTROY_BLOCK;
        PlayerActionC2SPacket.Action abort = PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK;
        PlayerActionC2SPacket.Action stop = PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK;

        if (this.grimV3.get()) {
            if (!this.startOnly.get()) {
                if (afterInstant) {
                    this.sendAction(stop, pos, face);
                }

                this.sendAction(start, pos, face);
                this.sendAction(abort, pos, face);
            } else {
                this.sendAction(start, pos, face);
            }

            if (!this.balance.burstWouldFlag(delta)) {
                this.sendAction(stop, pos, face);
            }

            if (this.swing.get() == Swing.NONE || this.swungThisTick) {
                this.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
            } else {
                this.swingOnce();
            }

            if (!instantBreak) {
                this.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
                this.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        } else {
            this.sendAction(start, pos, face);
            this.sendAction(abort, pos, face);
            this.sendAction(stop, pos, face);
            this.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
            if (!instantBreak) {
                this.sendAction(start, pos, face);
                this.sendAction(abort, pos, face);
                this.sendAction(stop, pos, face);
                this.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }

        return true;
    }

    private void cancel(MiningData d) {
        if (d.started && !cleared(d.state())) {
            this.sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, d.position, d.face);
        }
    }

    private void finish(MiningData d) {
        if (!d.started || cleared(d.state())) {
            return;
        }

        if (this.rotate.get()) {
            this.aimAtPoint(Vec3d.ofCenter(d.position));
        }

        Direction face = this.mc.player != null ? EnemyBlocks.facing(d.position) : d.face;
        this.sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, d.position, face);
        if (this.swing.get() == Swing.HAMMER) {
            this.swingOnce();
        }

        this.lastStopAt = System.currentTimeMillis();
        this.stopSentAt.put(d.position, this.lastStopAt);
    }

    public void queueMiningData(MiningData data) {
        if (cleared(data.state())) {
            return;
        }

        this.syncCapacity();

        if (this.busy(data.position)) {
            return;
        }

        if (this.begin(data)) {
            this.addFirst(data);
        }
    }

    public boolean isMining() {
        return !this.active.isEmpty();
    }

    public void clickMine(MiningData data) {
        this.click(data, true);
    }

    @EventHandler
    private void onStartBreaking(StartBreakingBlockEvent event) {
        if (this.mc.player == null || this.mc.world == null || this.mc.player.isCreative() || this.mc.player.isSpectator() || !this.packetMode()) {
            return;
        }

        int tick = this.mc.player.age;
        boolean fresh = tick < this.lastEventTick || tick - this.lastEventTick > 1;
        this.lastEventTick = tick;

        BlockPos pos = event.blockPos;
        BlockState state = this.mc.world.getBlockState(pos);
        if (this.leaveToVanilla(state, pos)) {
            return;
        }

        event.cancel();
        if (state.getHardness(this.mc.world, pos) != -1.0F && !state.isAir()) {
            this.click(new MiningData(pos, event.direction), fresh);
            this.swingOnce();
        }
    }

    private void click(MiningData d, boolean fresh) {
        boolean useQueue = this.queue.get() && this.packetMode();
        BlockPos pos = d.position;

        if (fresh) {
            this.blockedByPress = null;
        }

        if (useQueue && fresh && this.waitingHas(pos)) {
            this.waiting.removeIf(w -> w.position.equals(pos));
            this.blockedByPress = pos;
            return;
        }

        if (pos.equals(this.blockedByPress)) {
            return;
        }

        if (this.busy(pos) || this.waitingHas(pos)) {
            if (fresh && useQueue && this.queueThrough.get()) {
                this.traverse(pos, d.face);
            }

            return;
        }

        if (cleared(d.state())) {
            return;
        }

        if (useQueue && (this.solidLeft() || !this.waiting.isEmpty())) {
            if (this.waiting.size() >= WAITING_LIMIT) {
                return;
            }

            this.waiting.add(d);
            return;
        }

        if (this.grimDelay()) {
            this.park(d, useQueue);
            return;
        }

        int cap = this.doubleBreak.get() ? 2 : 1;
        this.syncCapacity();
        if (this.active.size() <= cap) {
            this.queueMiningData(d);
        }
    }

    private void park(MiningData d, boolean useQueue) {
        if (!useQueue) {
            this.waiting.clear();
        } else if (this.waiting.size() >= WAITING_LIMIT) {
            return;
        }

        this.waiting.add(d);
    }

    private void traverse(BlockPos from, Direction face) {
        Direction step = face.getOpposite();
        BlockPos c = from;

        for (int i = 0; i < 17; i++) {
            c = c.offset(step);
            if (this.busy(c) || this.waitingHas(c)) {
                continue;
            }

            BlockState state = this.mc.world.getBlockState(c);
            if (cleared(state)) {
                continue;
            }

            if (this.leaveToVanilla(state, c)) {
                return;
            }

            if (state.getHardness(this.mc.world, c) == -1.0F) {
                return;
            }

            this.click(new MiningData(c, face), true);
            return;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.packet;

        if (!event.isCancelled() && this.grim.get()) {
            long now = System.currentTimeMillis();
            if (packet instanceof PlayerActionC2SPacket action) {
                BlockPos pos = action.getPos();
                if (action.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
                    this.balance.onStart(pos, now);
                } else if (action.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
                    this.balance.onFinish(pos, now);
                    if (this.grimBalanceLog.get()) {
                        HunterBuddyAddon.LOG.info(String.format(
                            "h-mine: finish at %d %d %d, predicted %.0f ms, measured %.0f ms, diff %.0f -- delay balance %.0f, break balance %.0f, flag at %.0f",
                            pos.getX(), pos.getY(), pos.getZ(), this.balance.lastPredicted(), this.balance.lastReal(), this.balance.lastDiff(),
                            this.balance.delayBalance(), this.balance.breakBalance(), GrimBreakBalance.FLAG));
                    }
                }
            } else if (packet instanceof PlayerMoveC2SPacket || packet instanceof HandSwingC2SPacket) {
                this.balance.onFlying();
            }
        }

        if (packet instanceof PlayerActionC2SPacket action
            && action.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
            && this.mode.get() == SpeedmineMode.DAMAGE && this.grim.get()) {
            this.sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, action.getPos(), action.getDirection());
        }

        if (packet instanceof UpdateSelectedSlotC2SPacket && this.switchReset.get() && this.packetMode() && !this.tool.ownChange()) {
            this.syncCapacity();
            for (MiningData d : this.active) {
                d.restart();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (this.mc.player == null || !this.packetMode()) {
            return;
        }

        if (event.packet instanceof BlockUpdateS2CPacket update) {
            this.handleUpdate(update);
        } else if (event.packet instanceof BundleS2CPacket bundle) {
            for (Packet<?> inner : bundle.getPackets()) {
                if (inner instanceof BlockUpdateS2CPacket update) {
                    this.handleUpdate(update);
                }
            }
        }
    }

    private void handleUpdate(BlockUpdateS2CPacket update) {
        if (!cleared(update.getState())) {
            return;
        }

        BlockPos pos = update.getPos();
        Long sentAt = this.stopSentAt.remove(pos);
        if (sentAt != null) {
            SessionStats.get().onBlockMined();
            if (this.grimBalanceLog.get()) {
                HunterBuddyAddon.LOG.info(String.format("h-mine: server confirmed %d %d %d gone %d ms after the closing stop",
                    pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis() - sentAt));
            }
        }

        for (MiningData d : this.active) {
            if (d.attempted && d.position.equals(pos)) {
                d.markAttempt(false);
            }
        }
    }

    private MineRender.Look look() {
        return new MineRender.Look(this.style.get(), this.shapeMode.get(), this.mineColor.get(), this.doneColor.get(),
            this.queueColor.get(), this.blockOutline.get(), this.queueChain.get(), this.queueNumbers.get(), this.queue.get(),
            this.pulseRate.get(), this.fadeTime.get());
    }

    private List<BlockPos> waitingPositions() {
        List<BlockPos> positions = new ArrayList<>(this.waiting.size());
        for (MiningData d : this.waiting) {
            positions.add(d.position);
        }

        return positions;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.mc.player == null || this.mc.world == null || this.mc.player.isCreative() || !this.packetMode() || !this.render.get()) {
            return;
        }

        this.renderer.draw3D(event, this.mc.world, this.active, d -> this.packetMined(d) ? 1.0 : this.speed.get(),
            this.waitingPositions(), this.look());
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.mc.player == null || this.mc.world == null || !this.packetMode() || !this.render.get() || !this.queue.get()
            || !this.queueNumbers.get() || this.waiting.isEmpty()) {
            return;
        }

        this.renderer.draw2D(event, this.waitingPositions(), this.look());
    }
}
