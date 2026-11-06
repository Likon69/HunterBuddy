package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.pet.PetBrain;
import com.hunterbuddy.pet.PetDialogue;
import com.hunterbuddy.pet.PetEffects;
import com.hunterbuddy.pet.PetEvent;
import com.hunterbuddy.pet.PetEvents;
import com.hunterbuddy.pet.PetMemory;
import com.hunterbuddy.pet.PetMonitor;
import com.hunterbuddy.pet.PetMotion;
import com.hunterbuddy.pet.PetPose;
import com.hunterbuddy.pet.PetRenderer;
import com.hunterbuddy.pet.PetResources;
import meteordevelopment.meteorclient.events.entity.player.FinishUsingItemEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.resource.ReloadableResourceManagerImpl;

public final class Pet extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup speech = settings.createGroup("Speech");
    private final SettingGroup silence = settings.createGroup("Silence");
    private final Setting<Integer> size = general.add(new IntSetting.Builder().name("size").description("Character height in screen pixels.")
        .defaultValue(80).min(24).max(512).sliderRange(40, 200).build());
    private final Setting<Boolean> bottles = general.add(new BoolSetting.Builder().name("bottles").description("Allow thrown bottles.")
        .defaultValue(true).build());
    private final Setting<Boolean> crosshair = general.add(new BoolSetting.Builder().name("throw-at-crosshair").description("Aim bottles at the screen center.")
        .defaultValue(false).build());
    private final Setting<Double> maxDrunk = general.add(new DoubleSetting.Builder().name("maximum-drunkenness").description("Maximum drunkenness.")
        .defaultValue(1.0).min(0).max(1).sliderRange(0, 1).build());
    private final Setting<Double> sleepMinutes = general.add(new DoubleSetting.Builder().name("sleep-after-minutes").description("Sleep after this many minutes without player input.")
        .defaultValue(5.0).min(0.1).max(120).sliderRange(1, 30).build());
    private final Setting<Boolean> hide = general.add(new BoolSetting.Builder().name("hide-on-arrival").description("Hide beside the hotbar when players approach.")
        .defaultValue(true).build());
    private final Setting<Boolean> sounds = speech.add(new BoolSetting.Builder().name("sounds").description("Play dialogue supplied by the resource pack.")
        .defaultValue(true).build());
    private final Setting<Double> volume = speech.add(new DoubleSetting.Builder().name("volume").description("Dialogue volume.")
        .defaultValue(0.65).min(0).max(1).sliderRange(0, 1).onChanged(value -> PetResources.INSTANCE.volume(value.floatValue())).build());
    private final Setting<Double> chatter = speech.add(new DoubleSetting.Builder().name("chatter").description("Ambient dialogue frequency; zero disables ambient dialogue.")
        .defaultValue(1.0).min(0).max(5).sliderRange(0, 5).build());
    private final Setting<Double> gap = speech.add(new DoubleSetting.Builder().name("line-delay").description("Minimum seconds between all dialogue starts, including alerts.")
        .defaultValue(8.0).min(0).max(120).sliderRange(0, 60).build());
    private final Setting<Boolean> combatSilence = silence.add(new BoolSetting.Builder().name("combat").description("Mute ambient dialogue while KillAuraPlus is active.")
        .defaultValue(true).build());
    private final Setting<Boolean> regearSilence = silence.add(new BoolSetting.Builder().name("regear").description("Mute ambient dialogue during regear.")
        .defaultValue(true).build());
    private final Setting<Boolean> landingSilence = silence.add(new BoolSetting.Builder().name("landing").description("Mute ambient dialogue during WaypointFollower landing.")
        .defaultValue(true).build());
    private final PetBrain brain = new PetBrain();
    private final PetPose pose = new PetPose();
    private final PetMotion motion = new PetMotion();
    private final PetEffects effects = new PetEffects();
    private final PetDialogue dialogue = new PetDialogue();
    private final PetMonitor monitor = new PetMonitor();
    private final PetRenderer renderer = new PetRenderer();
    private final PetMemory memory = new PetMemory();
    private long lastInput;
    private double mouseX;
    private double mouseY;
    private double playerX;
    private double playerY;
    private double playerZ;
    private float yaw;
    private float pitch;
    private boolean observed;
    private int revision;

    public Pet() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "pet", "Animated companion supplied entirely by a local resource pack. See module info for pack status.");
        runInMainMenu = true;
        memory.load();
        PetResources.INSTANCE.volume(volume.get().floatValue());
        if (mc.getResourceManager() instanceof ReloadableResourceManagerImpl manager) manager.registerReloader(PetResources.INSTANCE);
        PetResources.INSTANCE.reload(mc.getResourceManager());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> memory.save());
    }

    @Override
    public void onActivate() {
        reset();
        PetEvents.listen(true);
    }

    @Override
    public void onDeactivate() {
        PetEvents.listen(false);
        dialogue.reset();
        effects.reset();
        memory.save();
    }

    @Override
    public String getInfoString() {
        return PetResources.INSTANCE.status;
    }

    private void reset() {
        brain.reset();
        pose.reset();
        motion.reset();
        effects.reset();
        dialogue.reset();
        monitor.reset();
        lastInput = System.currentTimeMillis();
        observed = false;
        revision = PetResources.INSTANCE.revision;
    }

    @EventHandler
    private void onJoined(GameJoinedEvent event) {
        reset();
        PetEvents.listen(true);
    }

    @EventHandler
    private void onLeft(GameLeftEvent event) {
        memory.save();
        monitor.reset();
        observed = false;
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        lastInput = System.currentTimeMillis();
    }

    @EventHandler
    private void onClick(MouseClickEvent event) {
        lastInput = System.currentTimeMillis();
    }

    @EventHandler
    private void onScroll(MouseScrollEvent event) {
        lastInput = System.currentTimeMillis();
    }

    @EventHandler
    private void onConsumed(FinishUsingItemEvent event) {
        if (event.itemStack.isOf(Items.GOLDEN_APPLE) || event.itemStack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            PetEvents.publish(PetEvent.EAT_GAPPLE);
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (event.packet instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35 && packet.getEntity(mc.world) == mc.player) {
            PetEvents.publish(PetEvent.TOTEM_POP);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (revision != PetResources.INSTANCE.revision) reset();
        boolean inWorld = mc.player != null && mc.world != null;
        PetResources.Rig rig = PetResources.INSTANCE.rig;
        if (inWorld) {
            noteInput(now);
            monitor.update();
            if (rig != null) {
                brain.update(0.05f, now - lastInput > sleepMinutes.get() * 60000, maxDrunk.get(), chatter.get(), bottles.get());
                pose.update(brain, rig, 0.05f);
                motion.update(brain, 0.05f, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), size.get(), mc.getWindow().getScaleFactor());
                renderer.anchor(rig, motion, pose, size.get(), 0, 2);
                effects.update(0.05f, brain, motion, rig, size.get(), renderer.anchorX, renderer.anchorY);
            }
        }
        if (!bottles.get()) effects.clearBottles();
        long pending = PetEvents.drain();
        for (PetEvent value : PetEvent.ALL) {
            if ((pending & (1L << value.ordinal())) == 0) continue;
            memory.note(value, hide.get(), PetEvents.count(value));
            brain.react(value, hide.get());
            if (rig != null && inWorld && value == PetEvent.THROW_BOTTLE && bottles.get()) {
                renderer.anchor(rig, motion, pose, size.get(), 1, 3);
                effects.throwBottle(renderer.anchorX, renderer.anchorY, motion.direction, crosshair.get(), motion.width, motion.height, size.get());
            }
            if (value == PetEvent.DRINK) effects.word(2, motion.x, motion.y - size.get());
            if (chatter.get() > 0 || value.priority > 0) dialogue.offer(value, now);
        }
        boolean quiet = combatSilence.get() && monitor.combatQuiet || regearSilence.get() && monitor.regearQuiet
            || landingSilence.get() && monitor.landingQuiet;
        dialogue.update(now, mc.isWindowFocused() && rig != null, quiet, sounds.get(), gap.get());
    }

    private void noteInput(long now) {
        double mx = mc.mouse.getX();
        double my = mc.mouse.getY();
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        if (!observed || mx != mouseX || my != mouseY || x != playerX || y != playerY || z != playerZ || currentYaw != yaw || currentPitch != pitch
            || mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()
            || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed() || mc.options.attackKey.isPressed() || mc.options.useKey.isPressed()) lastInput = now;
        mouseX = mx;
        mouseY = my;
        playerX = x;
        playerY = y;
        playerZ = z;
        yaw = currentYaw;
        pitch = currentPitch;
        observed = true;
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        PetResources.Rig rig = PetResources.INSTANCE.rig;
        if (rig == null || mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) return;
        float guiScale = mc.getWindow().getScaleFactor();
        event.drawContext.getMatrices().pushMatrix();
        event.drawContext.getMatrices().scale(1 / guiScale, 1 / guiScale);
        renderer.render(event.drawContext, rig, motion, pose, brain, effects, dialogue, size.get(), event.tickDelta, guiScale);
        event.drawContext.getMatrices().popMatrix();
    }
}
