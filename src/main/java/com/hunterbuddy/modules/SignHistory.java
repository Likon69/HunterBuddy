package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mixin.accessors.SignEditScreenAccessor;
import com.hunterbuddy.modules.signs.SignGhostRenderer;
import com.hunterbuddy.modules.signs.SignHistoryStore;
import com.hunterbuddy.modules.signs.SignKey;
import com.hunterbuddy.modules.signs.SignRecord;
import com.hunterbuddy.modules.signs.SignRestorer;
import com.hunterbuddy.modules.signs.SignStyler;
import com.hunterbuddy.modules.signs.SignWatch;
import com.hunterbuddy.modules.stash.StashDimensions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;

public class SignHistory extends Module {
    private static final int SCAN_INTERVAL = 10;
    private static final int SAVE_INTERVAL = 200;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance in blocks at which remembered signs are drawn.")
        .defaultValue(64)
        .min(8)
        .sliderMax(256)
        .build()
    );

    private final Setting<Boolean> restore = sgGeneral.add(new BoolSetting.Builder()
        .name("restore")
        .description("Replaces the sign editor on a destroyed or changed sign with the original text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> restoreDelay = sgGeneral.add(new IntSetting.Builder()
        .name("restore-delay")
        .description("Ticks to wait before sending the original text.")
        .defaultValue(20)
        .range(0, 100)
        .sliderRange(0, 100)
        .visible(restore::get)
        .build()
    );

    private final Setting<Boolean> restoreColors = sgGeneral.add(new BoolSetting.Builder()
        .name("restore-colors")
        .description("Applies the original dye and glow ink with items from your hotbar after the text is restored.")
        .defaultValue(true)
        .visible(restore::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> showDestroyed = sgRender.add(new BoolSetting.Builder()
        .name("show-destroyed")
        .description("Draws a box on signs that were destroyed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> destroyedSideColor = sgRender.add(new ColorSetting.Builder()
        .name("destroyed-side-color")
        .description("The side color of destroyed signs.")
        .defaultValue(new SettingColor(255, 42, 0, 40))
        .visible(showDestroyed::get)
        .build()
    );

    private final Setting<SettingColor> destroyedLineColor = sgRender.add(new ColorSetting.Builder()
        .name("destroyed-line-color")
        .description("The line color of destroyed signs.")
        .defaultValue(new SettingColor(255, 42, 0, 255))
        .visible(showDestroyed::get)
        .build()
    );

    private final Setting<Boolean> showChanged = sgRender.add(new BoolSetting.Builder()
        .name("show-changed")
        .description("Draws a box on signs whose text changed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> changedSideColor = sgRender.add(new ColorSetting.Builder()
        .name("changed-side-color")
        .description("The side color of changed signs.")
        .defaultValue(new SettingColor(237, 255, 42, 40))
        .visible(showChanged::get)
        .build()
    );

    private final Setting<SettingColor> changedLineColor = sgRender.add(new ColorSetting.Builder()
        .name("changed-line-color")
        .description("The line color of changed signs.")
        .defaultValue(new SettingColor(237, 255, 42, 255))
        .visible(showChanged::get)
        .build()
    );

    private final Setting<Boolean> showOriginalText = sgRender.add(new BoolSetting.Builder()
        .name("show-original-text")
        .description("Shows the original text above destroyed and changed signs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("text-color")
        .description("The color of the original text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(showOriginalText::get)
        .build()
    );

    private final SignHistoryStore store = new SignHistoryStore();
    private final SignGhostRenderer ghostRenderer = new SignGhostRenderer();
    private final SignRestorer restorer = new SignRestorer();
    private final SignStyler styler = new SignStyler();

    private CompletableFuture<Map<SignKey, SignRecord>> loading;
    private SignWatch watch;
    private String world;
    private int ticks;

    public SignHistory() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "sign-history", "Remembers every sign you see, shows destroyed and changed signs with their original text and can write it back.");
    }

    @Override
    public void onDeactivate() {
        close();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        close();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.getNetworkHandler().getPlayerList().size() <= 1) return;

        followWorld();
        if (!loaded()) return;

        ticks++;
        if (ticks % SCAN_INTERVAL == 0) watch.scan(mc.world, StashDimensions.current());
        if (ticks % SAVE_INTERVAL == 0) save();
        if (restorer.tick(mc.getNetworkHandler())) info("Sign text restored");
        if (styler.tick(mc)) info("Sign colors applied");
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!restore.get() || watch == null || !(event.screen instanceof AbstractSignEditScreen screen)) return;

        SignEditScreenAccessor accessor = (SignEditScreenAccessor) screen;
        SignKey key = SignKey.of(StashDimensions.current(), accessor.getSign().getPos());
        SignRecord original = watch.damagedOriginal(key);
        if (original == null) return;

        event.cancel();
        restorer.queue(original, accessor.isFront(), restoreDelay.get());
        if (restoreColors.get()) styler.queue(original, accessor.isFront(), restoreDelay.get());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onSend(PacketEvent.Send event) {
        if (watch == null || event.isCancelled()) return;

        String dimension = StashDimensions.current();
        if (event.packet instanceof PlayerActionC2SPacket action && breaksSign(action)) {
            watch.forget(SignKey.of(dimension, action.getPos()));
        } else if (event.packet instanceof UpdateSignC2SPacket update && !restorer.isOwn(update)) {
            watch.adopt(SignKey.of(dimension, update.getPos()), update.isFront(), new ArrayList<>(Arrays.asList(update.getText())));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (watch == null || mc.player == null) return;

        String dimension = StashDimensions.current();
        if (showDestroyed.get()) {
            ghostRenderer.boxes(event, watch.destroyed(dimension), mc.player.getEyePos(), range.get(), destroyedSideColor.get(), destroyedLineColor.get(), shapeMode.get());
        }
        if (showChanged.get()) {
            ghostRenderer.boxes(event, watch.modified(dimension), mc.player.getEyePos(), range.get(), changedSideColor.get(), changedLineColor.get(), shapeMode.get());
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (watch == null || mc.player == null || !showOriginalText.get()) return;

        String dimension = StashDimensions.current();
        if (showDestroyed.get()) ghostRenderer.labels(watch.destroyed(dimension), mc.player.getEyePos(), range.get(), textColor.get());
        if (showChanged.get()) ghostRenderer.labels(watch.modified(dimension), mc.player.getEyePos(), range.get(), textColor.get());
    }

    private void followWorld() {
        String current = Utils.getFileWorldName();
        if (current.equals(world)) return;

        save();
        world = current;
        watch = null;
        loading = store.read(current);
        restorer.clear();
        styler.clear();
    }

    private boolean loaded() {
        if (watch != null) return true;
        if (loading == null || !loading.isDone()) return false;

        watch = new SignWatch(loading.join());
        loading = null;
        return true;
    }

    private boolean breaksSign(PlayerActionC2SPacket action) {
        return action.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
            || action.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK && mc.player.isCreative();
    }

    private void save() {
        if (watch != null && watch.takeDirty()) store.write(world, watch.snapshot());
    }

    private void close() {
        save();
        watch = null;
        loading = null;
        world = null;
        restorer.clear();
        styler.clear();
    }
}
