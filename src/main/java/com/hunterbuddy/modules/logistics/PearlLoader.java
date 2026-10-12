package com.hunterbuddy.modules.logistics;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PearlLoader extends Module {
    private static final String SAVE_KEY = "loadLocations";
    private static final long CLICK_GAP_MS = 500L;
    private static final long RECLOSE_CHECK_MS = 500L;
    private static final double SAFE_REACH = 5.0;
    private static final int AIM_LIMIT = 50;
    private static final double AIM_RATE = 0.1;
    private static final double AIM_TOLERANCE = 2.0;

    public enum LoadMode {
        TRAPDOOR,
        WALK_TO;

        @Override
        public String toString() {
            return switch (this) {
                case TRAPDOOR -> "Trapdoor - Interact with trapdoor to load pearl";
                case WALK_TO -> "Walk To - Walk to position and return";
            };
        }
    }

    private enum Phase {
        LOOP_TO_FIRST,
        LOOP_TO_SECOND,
        DOOR_APPROACH,
        DOOR_SETTLE,
        DOOR_AIM,
        DOOR_CLOSE,
        DOOR_HOLD,
        DOOR_OPEN,
        SPOT_APPROACH,
        SPOT_SETTLE,
        SPOT_STAND,
        SPOT_BACK
    }

    public static class LoadLocation {
        public String word = "!pearl";
        public LoadMode kind = LoadMode.TRAPDOOR;
        public BlockPos spot = new BlockPos(0, 64, 0);
        public double closedSecs = 2.0;
        public double standSecs = 1.0;
    }

    private final SettingGroup sgDefault = settings.getDefaultGroup();
    private final SettingGroup sgCoords = settings.createGroup("Coordinates");
    private final SettingGroup sgList = settings.createGroup("Whitelist");

    private final Setting<BlockPos> loopA = sgCoords.add(new BlockPosSetting.Builder()
        .name("Walk Point 1")
        .description("First end of the walking loop.")
        .defaultValue(new BlockPos(0, 64, 0))
        .build()
    );

    private final Setting<BlockPos> loopB = sgCoords.add(new BlockPosSetting.Builder()
        .name("Walk Point 2")
        .description("Second end of the walking loop.")
        .defaultValue(new BlockPos(10, 64, 0))
        .build()
    );

    private final Setting<Boolean> filterSenders = sgList.add(new BoolSetting.Builder()
        .name("Use Whitelist")
        .description("Only react to keywords sent by the players listed below.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> allowedSenders = sgList.add(new StringListSetting.Builder()
        .name("Whitelisted Players")
        .description("Players allowed to trigger a pearl load.")
        .defaultValue(new ArrayList<>())
        .visible(filterSenders::get)
        .build()
    );

    private final Setting<Double> arriveRadius = sgDefault.add(new DoubleSetting.Builder()
        .name("Reach Threshold")
        .description("How close, in blocks, counts as arrived.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderRange(0.1, 5.0)
        .build()
    );

    private final Setting<Boolean> verbose = sgDefault.add(new BoolSetting.Builder()
        .name("Debug Mode")
        .description("Report every step in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> settleTicks = sgDefault.add(new IntSetting.Builder()
        .name("Arrival Wait Ticks")
        .description("Ticks to stand still after arriving, to let the server catch up.")
        .defaultValue(19)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    private final List<LoadLocation> places = new ArrayList<>();

    private Phase phase = Phase.LOOP_TO_SECOND;
    private boolean loading = false;
    private LoadLocation activePlace = null;
    private BlockPos goal = null;
    private long phaseStart = 0L;
    private long lastClick = 0L;
    private int aimSteps = 0;
    private Boolean baritoneKnown = null;

    public PearlLoader() {
        super(HunterBuddyAddon.LOGISTICS_CATEGORY, "pearl-butler", "Walks back and forth to stay online, and loads an ender pearl stasis when a chat keyword arrives.", "PearlLoader");
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        NbtList saved = new NbtList();
        for (LoadLocation place : places) {
            NbtCompound entry = new NbtCompound();
            entry.putString("keyword", place.word);
            entry.putString("mode", place.kind.name());
            entry.putInt("x", place.spot.getX());
            entry.putInt("y", place.spot.getY());
            entry.putInt("z", place.spot.getZ());
            entry.putDouble("closeTime", place.closedSecs);
            entry.putDouble("standTime", place.standSecs);
            saved.add(entry);
        }
        tag.put(SAVE_KEY, saved);
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);
        places.clear();
        Optional<NbtList> saved = tag.getList(SAVE_KEY);
        if (saved.isPresent()) {
            NbtList entries = saved.get();
            for (int i = 0; i < entries.size(); i++) {
                Optional<NbtCompound> entry = entries.getCompound(i);
                if (entry.isPresent()) places.add(readPlace(entry.get()));
            }
        }
        return this;
    }

    private static LoadLocation readPlace(NbtCompound entry) {
        LoadLocation place = new LoadLocation();
        place.word = entry.getString("keyword").orElse("");
        place.kind = parseKind(entry.getString("mode").orElse("TRAPDOOR"));
        place.spot = new BlockPos(
            entry.getInt("x").orElse(0),
            entry.getInt("y").orElse(0),
            entry.getInt("z").orElse(0)
        );
        place.closedSecs = entry.getDouble("closeTime").orElse(1.0);
        place.standSecs = entry.getDouble("standTime").orElse(0.0);
        return place;
    }

    private static LoadMode parseKind(String name) {
        try {
            return LoadMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return LoadMode.TRAPDOOR;
        }
    }

    @Override
    public void onActivate() {
        phase = Phase.LOOP_TO_SECOND;
        loading = false;
        phaseStart = System.currentTimeMillis();
        goal = loopB.get();
        walkTo(goal);
        info("pearl-butler on: walking the anti-AFK loop.");
    }

    @Override
    public void onDeactivate() {
        haltWalking();
        loading = false;
        phase = Phase.LOOP_TO_SECOND;
        phaseStart = 0L;
        goal = null;
        info("pearl-butler off.");
    }

    private void say(String text) {
        info(text.replace("%", "%%"));
    }

    private void trace(String text) {
        if (verbose.get()) say(text);
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (!isActive() || loading) return;
        String raw = event.getMessage().getString();
        String lowered = raw.toLowerCase();
        for (LoadLocation place : places) {
            String word = place.word.toLowerCase();
            if (!lowered.contains(word)) continue;
            if (!filterSenders.get()) {
                say("Pearl load triggered by keyword: " + place.word);
                beginLoad(place);
                return;
            }
            String sender = findSender(raw);
            if (sender != null && isAllowed(sender)) {
                say("Pearl load triggered by " + sender + " with keyword: " + place.word);
                beginLoad(place);
                return;
            }
            trace("Trigger ignored - player not whitelisted: " + sender);
        }
    }

    private boolean isAllowed(String sender) {
        for (String name : allowedSenders.get()) {
            if (name.equalsIgnoreCase(sender)) return true;
        }
        return false;
    }

    private static String stripColors(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    private String findSender(String message) {
        if (message.contains(" whispers: ")) {
            String head = stripColors(message.substring(0, message.indexOf(" whispers: "))).trim();
            String[] parts = head.split(" ");
            String name = parts[parts.length - 1];
            trace("Extracted whisper sender: " + name);
            return name;
        }
        if (message.contains(": ")) {
            String head = stripColors(message.substring(0, message.indexOf(": ")));
            int open = head.lastIndexOf('<');
            int close = head.lastIndexOf('>');
            if (open >= 0 && close >= 0 && open < close) {
                String name = head.substring(open + 1, close).trim();
                trace("Extracted public sender: " + name);
                return name;
            }
            String[] parts = head.trim().split(" ");
            String name = parts[parts.length - 1].replaceAll("[<>\\[\\]]", "").trim();
            trace("Extracted sender: " + name);
            return name;
        }
        if (verbose.get()) warning("Could not extract sender from message: " + message.replace("%", "%%"));
        return null;
    }

    private boolean baritoneThere() {
        if (baritoneKnown == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                baritoneKnown = true;
            } catch (ClassNotFoundException | LinkageError e) {
                baritoneKnown = false;
            }
        }
        return baritoneKnown;
    }

    private void walkTo(BlockPos pos) {
        if (pos == null) return;
        if (baritoneThere()) {
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                .getCustomGoalProcess()
                .setGoalAndPath(new baritone.api.pathing.goals.GoalBlock(pos));
        } else {
            error("Baritone is missing, cannot walk.");
        }
    }

    private void haltWalking() {
        if (!baritoneThere()) return;
        try {
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                .getPathingBehavior()
                .cancelEverything();
        } catch (Throwable ignored) {
        }
    }

    private boolean walkFinished() {
        try {
            return !baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                .getPathingBehavior()
                .isPathing();
        } catch (Throwable e) {
            return false;
        }
    }

    private double gap(BlockPos pos) {
        if (mc.player == null || pos == null) return Double.POSITIVE_INFINITY;
        return mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos));
    }

    private boolean isNear(BlockPos pos) {
        return gap(pos) <= arriveRadius.get();
    }

    private void enter(Phase next) {
        phase = next;
        phaseStart = System.currentTimeMillis();
    }

    private long inPhase() {
        return System.currentTimeMillis() - phaseStart;
    }

    private void beginLoad(LoadLocation place) {
        if (loading) return;
        loading = true;
        activePlace = place;
        haltWalking();
        if (place.kind == LoadMode.TRAPDOOR) {
            phase = Phase.DOOR_APPROACH;
            walkTo(standingSpotFor(place.spot));
            trace("Starting trapdoor pearl load sequence for: " + place.word);
        } else {
            phase = Phase.SPOT_APPROACH;
            walkTo(place.spot);
            trace("Starting walk-to pearl load sequence for: " + place.word);
        }
        phaseStart = System.currentTimeMillis();
    }

    private void backToLoop() {
        loading = false;
        activePlace = null;
        phase = Phase.LOOP_TO_FIRST;
        goal = loopA.get();
        walkTo(goal);
        phaseStart = System.currentTimeMillis();
    }

    private BlockPos standingSpotFor(BlockPos door) {
        BlockPos best = null;
        double bestGap = Double.POSITIVE_INFINITY;
        for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos cand = door.offset(side);
            if (!mc.world.getBlockState(cand).isAir()) continue;
            BlockPos under = cand.down();
            if (!mc.world.getBlockState(under).isSolidBlock(mc.world, under)) continue;
            double d = gap(cand);
            if (d < bestGap) {
                bestGap = d;
                best = cand;
            }
        }
        return best != null ? best : door.north();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.world == null) return;
        if (!loading) {
            stepLoop();
            return;
        }
        stepLoad();
    }

    private void stepLoop() {
        if (goal == null || !walkFinished()) return;
        if (phase == Phase.LOOP_TO_FIRST) {
            phase = Phase.LOOP_TO_SECOND;
            goal = loopB.get();
            walkTo(goal);
            trace("Reached Point 1, walking to Point 2");
        } else {
            phase = Phase.LOOP_TO_FIRST;
            goal = loopA.get();
            walkTo(goal);
            trace("Reached Point 2, walking to Point 1");
        }
    }

    private void stepLoad() {
        switch (phase) {
            case DOOR_APPROACH -> stepDoorApproach();
            case DOOR_SETTLE -> stepDoorSettle();
            case DOOR_AIM -> stepDoorAim();
            case DOOR_CLOSE -> stepDoorClose();
            case DOOR_HOLD -> stepDoorHold();
            case DOOR_OPEN -> stepDoorOpen();
            case SPOT_APPROACH -> stepSpotApproach();
            case SPOT_SETTLE -> stepSpotSettle();
            case SPOT_STAND -> stepSpotStand();
            case SPOT_BACK -> stepSpotBack();
            default -> {
            }
        }
    }

    private boolean settled() {
        return inPhase() >= settleTicks.get() * 50L;
    }

    private void stepDoorApproach() {
        BlockPos stand = standingSpotFor(activePlace.spot);
        if (isNear(stand)) {
            haltWalking();
            enter(Phase.DOOR_SETTLE);
            trace("Arrived next to the trapdoor, waiting for the server");
        } else if (walkFinished()) {
            walkTo(stand);
        }
    }

    private void stepDoorSettle() {
        BlockPos stand = standingSpotFor(activePlace.spot);
        if (gap(stand) > arriveRadius.get()) {
            phase = Phase.DOOR_APPROACH;
            walkTo(stand);
            trace("Drifted away from the trapdoor, walking back");
        } else if (settled()) {
            enter(Phase.DOOR_AIM);
            aimSteps = 0;
            trace("Aiming at the trapdoor");
        }
    }

    private void stepDoorAim() {
        if (!aimAt(Vec3d.ofCenter(activePlace.spot))) return;
        BlockState state = mc.world.getBlockState(activePlace.spot);
        if (!(state.getBlock() instanceof TrapdoorBlock)) {
            error("No trapdoor at the saved spot.");
            backToLoop();
            return;
        }
        enter(Phase.DOOR_CLOSE);
        trace("Aim done, closing the trapdoor");
    }

    private boolean tooFarFromDoor() {
        if (gap(activePlace.spot) <= SAFE_REACH) return false;
        error("Trapdoor out of reach, walking back to it.");
        phase = Phase.DOOR_APPROACH;
        walkTo(standingSpotFor(activePlace.spot));
        return true;
    }

    private boolean doorIsOpen() {
        BlockState state = mc.world.getBlockState(activePlace.spot);
        return state.getBlock() instanceof TrapdoorBlock && state.get(TrapdoorBlock.OPEN);
    }

    private void stepDoorClose() {
        if (tooFarFromDoor()) return;
        if (doorIsOpen()) pressDoor();
        enter(Phase.DOOR_HOLD);
        trace("Trapdoor closed, holding it shut");
    }

    private void stepDoorHold() {
        long elapsed = inPhase();
        if (elapsed > RECLOSE_CHECK_MS && doorIsOpen()) {
            enter(Phase.DOOR_CLOSE);
            trace("Trapdoor still open, closing it again");
            return;
        }
        if (elapsed >= activePlace.closedSecs * 1000.0) {
            enter(Phase.DOOR_OPEN);
            trace("Hold time over, reopening the trapdoor");
        }
    }

    private void stepDoorOpen() {
        if (tooFarFromDoor()) return;
        if (!doorIsOpen()) pressDoor();
        say("Pearl loading complete");
        backToLoop();
    }

    private void stepSpotApproach() {
        BlockPos spot = activePlace.spot;
        if (isNear(spot)) {
            haltWalking();
            enter(Phase.SPOT_SETTLE);
            trace("Arrived at the load position, waiting for the server");
        } else if (walkFinished()) {
            walkTo(spot);
        }
    }

    private void stepSpotSettle() {
        BlockPos spot = activePlace.spot;
        if (gap(spot) > arriveRadius.get()) {
            phase = Phase.SPOT_APPROACH;
            walkTo(spot);
            trace("Drifted away from the load position, walking back");
        } else if (settled()) {
            enter(Phase.SPOT_STAND);
            trace("Standing on the load position");
        }
    }

    private void stepSpotStand() {
        if (inPhase() < activePlace.standSecs * 1000.0) return;
        phase = Phase.SPOT_BACK;
        goal = loopA.get();
        walkTo(goal);
        phaseStart = System.currentTimeMillis();
        trace("Stand time over, walking back to Point 1");
    }

    private void stepSpotBack() {
        if (walkFinished() || isNear(goal)) {
            say("Pearl loading complete");
            backToLoop();
        }
    }

    private boolean aimAt(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = (float) MathHelper.clamp(Math.toDegrees(Math.atan2(-dy, flat)), -90.0, 90.0);
        aimSteps++;
        float yawGap = MathHelper.wrapDegrees(wantYaw - mc.player.getYaw());
        float pitchGap = wantPitch - mc.player.getPitch();
        mc.player.setYaw((float) (mc.player.getYaw() + yawGap * AIM_RATE));
        mc.player.setPitch((float) (mc.player.getPitch() + pitchGap * AIM_RATE));
        boolean close = Math.abs(yawGap) < AIM_TOLERANCE && Math.abs(pitchGap) < AIM_TOLERANCE;
        return close || aimSteps > AIM_LIMIT;
    }

    private void pressDoor() {
        long now = System.currentTimeMillis();
        if (now - lastClick < CLICK_GAP_MS) return;
        lastClick = now;
        BlockPos pos = activePlace.spot;
        Vec3d center = Vec3d.ofCenter(pos);
        double offX = mc.player.getX() - center.x;
        double offZ = mc.player.getZ() - center.z;
        Direction face;
        if (Math.abs(offX) > Math.abs(offZ)) {
            face = offX > 0 ? Direction.EAST : Direction.WEST;
        } else {
            face = offZ > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        BlockHitResult hit = new BlockHitResult(center, face, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList root = theme.verticalList();

        WButton add = root.add(theme.button("Add Load Location")).expandX().widget();
        add.action = () -> {
            LoadLocation fresh = new LoadLocation();
            fresh.word = "!pearl" + (places.size() + 1);
            places.add(fresh);
            info("Spot saved. Reopen the settings to see it.");
        };

        for (int i = 0; i < places.size(); i++) {
            addPlaceRows(theme, root, places.get(i), i);
        }

        root.add(theme.horizontalSeparator()).expandX();
        WButton test = root.add(theme.button("Test Walking Loop")).expandX().widget();
        test.action = () -> {
            if (isActive()) {
                info("Walking the loop once to test it.");
                backToLoop();
            }
        };
        return root;
    }

    private void addPlaceRows(GuiTheme theme, WVerticalList root, LoadLocation place, int index) {
        root.add(theme.horizontalSeparator()).expandX();

        WHorizontalList head = root.add(theme.horizontalList()).expandX().widget();
        head.add(theme.label("Location " + (index + 1) + ":"));
        WButton remove = head.add(theme.button("-")).widget();
        remove.action = () -> {
            places.remove(place);
            info("Spot deleted. Reopen the settings to see it.");
        };
        WButton trigger = head.add(theme.button("Trigger")).widget();
        trigger.action = () -> {
            if (isActive() && !loading) {
                say("Manually triggering pearl load: " + place.word);
                beginLoad(place);
            }
        };

        WTextBox word = root.add(theme.textBox(place.word)).expandX().widget();
        word.action = () -> place.word = word.get();

        root.add(theme.label("Position: " + place.spot.toShortString()));
        WButton here = root.add(theme.button("Set to Player Pos")).expandX().widget();
        here.action = () -> {
            if (mc.player != null) place.spot = mc.player.getBlockPos();
        };

        root.add(theme.label("Mode: " + place.kind));
        WHorizontalList modes = root.add(theme.horizontalList()).expandX().widget();
        WButton door = modes.add(theme.button("Trapdoor")).widget();
        door.action = () -> place.kind = LoadMode.TRAPDOOR;
        WButton walk = modes.add(theme.button("Walk To")).widget();
        walk.action = () -> place.kind = LoadMode.WALK_TO;

        if (place.kind == LoadMode.TRAPDOOR) {
            root.add(theme.label("Close Time: " + place.closedSecs + "s"));
        } else {
            root.add(theme.label("Stand Time: " + place.standSecs + "s"));
        }
    }
}
