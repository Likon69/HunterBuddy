package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.pet.PetBrain;
import com.hunterbuddy.pet.PetSfx;
import com.hunterbuddy.pet.PetSfxPlayback;
import com.hunterbuddy.pet.PetSfxCues;
import com.hunterbuddy.pet.PetSfxAction;
import com.hunterbuddy.pet.PetFullPose;
import com.hunterbuddy.pet.PetHudCapture;
import com.hunterbuddy.pet.PetSurfaces;
import com.hunterbuddy.pet.PetVisibility;
import com.hunterbuddy.pet.PetState;
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
    private final SettingGroup audio = settings.createGroup("Sound Effects");
    private final Setting<Boolean> soundEffects = audio.add(new BoolSetting.Builder().name("enabled").description("Play animation sounds supplied by the resource pack.")
        .defaultValue(true).build());
    private final Setting<Double> effectsVolume = audio.add(new DoubleSetting.Builder().name("volume").description("Sound effects volume, independent of dialogue.")
        .defaultValue(0.65).min(0).max(1).sliderRange(0, 1).build());
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
    private final Setting<Double> minimumSleep = general.add(new DoubleSetting.Builder().name("minimum-sleep-seconds")
        .description("Minimum time spent fully asleep, excluding posture transitions.").defaultValue(30).min(30).max(120).sliderRange(30, 120).build());
    private final Setting<Double> maximumSleep = general.add(new DoubleSetting.Builder().name("maximum-sleep-seconds")
        .description("Maximum time spent fully asleep; never below the minimum.").defaultValue(120).min(30).max(120).sliderRange(30, 120).build());
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
    private final Setting<Boolean> combatSilence = silence.add(new BoolSetting.Builder().name("combat").description("Mute ambient dialogue and animation sounds during combat.")
        .defaultValue(true).build());
    private final Setting<Boolean> regearSilence = silence.add(new BoolSetting.Builder().name("regear").description("Mute ambient dialogue and animation sounds during regear.")
        .defaultValue(true).build());
    private final Setting<Boolean> landingSilence = silence.add(new BoolSetting.Builder().name("landing").description("Mute ambient dialogue and animation sounds during landing.")
        .defaultValue(true).build());
    private final SettingGroup surfaces = settings.createGroup("Surfaces");
    private final Setting<Boolean> floor = surfaces.add(new BoolSetting.Builder().name("screen-bottom").description("Use the bottom screen edge.").defaultValue(true).build());
    private final Setting<Boolean> hotbar = surfaces.add(new BoolSetting.Builder().name("hotbar").description("Use the visible hotbar.").defaultValue(true).build());
    private final Setting<Boolean> statusBars = surfaces.add(new BoolSetting.Builder().name("status-bars").description("Use visible health, food, armor and experience bars.").defaultValue(true).build());
    private final Setting<Boolean> meteorHud = surfaces.add(new BoolSetting.Builder().name("meteor-hud").description("Use visible Meteor HUD boxes.").defaultValue(true).build());
    private final Setting<Boolean> climbing = surfaces.add(new BoolSetting.Builder().name("climbing").description("Climb platform edges.").defaultValue(true).build());
    private final Setting<Double> poseFrequency = general.add(new DoubleSetting.Builder().name("pose-frequency").description("Spontaneous pack poses; zero keeps dialogue poses only.")
        .defaultValue(1).min(0).max(5).sliderRange(0, 5).build());
    private final PetFullPose fullPose = new PetFullPose();
    private final PetBrain brain = new PetBrain();
    private final PetPose pose = new PetPose();
    private final PetMotion motion = new PetMotion();
    private final PetSfx sfx = new PetSfx(new PetSfxPlayback());
    private final PetSfxCues cues = new PetSfxCues(sfx);
    private final PetEffects effects = new PetEffects(sfx);
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
    private PetResources.Line lastLine;

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
        PetSurfaces.INSTANCE.enabled = true;
        PetEvents.listen(true);
    }

    @Override
    public void onDeactivate() {
        PetSurfaces.INSTANCE.enabled = false;
        PetEvents.listen(false);
        dialogue.reset();
        sfx.reset();
        cues.reset();
        effects.reset();
        memory.save();
    }

    @Override
    public String getInfoString() {
        return PetResources.INSTANCE.status;
    }

    private void reset() {
        lastLine = null;
        sfx.reset();
        cues.reset();
        fullPose.reset();
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
        PetSurfaces.INSTANCE.enabled = true;
        PetEvents.listen(true);
    }

    @EventHandler
    private void onLeft(GameLeftEvent event) {
        sfx.reset();
        cues.reset();
        memory.save();
        monitor.reset();
        observed = false;
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        input(System.currentTimeMillis());
    }

    @EventHandler
    private void onClick(MouseClickEvent event) {
        input(System.currentTimeMillis());
    }

    @EventHandler
    private void onScroll(MouseScrollEvent event) {
        input(System.currentTimeMillis());
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
        }
        boolean quiet = combatSilence.get() && monitor.combatQuiet || regearSilence.get() && monitor.regearQuiet
            || landingSilence.get() && monitor.landingQuiet;
        boolean visible = inWorld && rig != null && mc.isWindowFocused()
            && (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen);
        sfx.update(now, soundEffects.get() && visible, quiet, effectsVolume.get().floatValue());
        if (inWorld) {
            if (rig != null) {
                brain.externalDanger = monitor.danger;
                brain.highEdge = motion.highEdge;
                brain.atEdge = motion.atEdge;
                brain.seatAvailable = motion.seatAvailable;
                brain.elevated = motion.elevated;
                brain.grounded = motion.travel == PetMotion.Travel.GROUNDED;
                brain.roomToLie = motion.roomToLie;
                brain.surfaceKind = motion.surfaceKind;
                brain.night = mc.world.isNight();
                brain.minimumSleep = minimumSleep.get().floatValue();
                brain.maximumSleep = maximumSleep.get().floatValue();
                brain.danceImageCount = rig.dances().length;
                brain.canDrink = pose.drink.available || PetFullPose.find(rig, "drink") != null;
                brain.update(0.05f, now - lastInput > sleepMinutes.get() * 60000, maxDrunk.get(), chatter.get(), bottles.get());
                PetResources.Line requested = dialogue.current != null ? dialogue.current : dialogue.queued;
                fullPose.update(0.05f, rig, requested == null ? null : requested.pose, brain, motion, pose, poseFrequency.get());
                motion.update(brain, 0.05f, PetSurfaces.INSTANCE, size.get(), climbing.get(), fullPose.blocksMotion() || brain.resting()
                    || brain.state == PetState.SIT && motion.atEdge);
                pose.update(brain, rig, motion, 0.05f);
                renderer.prepare(rig, motion, pose, size.get(), 1);
                renderer.eyes(rig, motion, fullPose, size.get(), 1);
                PetVisibility.keepEyesVisible(motion, renderer.anchorX, renderer.anchorY, size.get());
                renderer.prepare(rig, motion, pose, size.get(), 1);
                renderer.eyes(rig, motion, fullPose, size.get(), 1);
                float headX = renderer.anchorX;
                float headY = renderer.anchorY;
                cues.update(brain, motion, pose, fullPose);
                if (cues.beerOpened) effects.word(2, headX, headY);
                renderer.anchor(rig, 0);
                effects.update(0.05f, brain, motion, rig, size.get(), renderer.anchorX, renderer.anchorY, headX, headY, fullPose.blend == 0);
            }
        }
        if (!bottles.get()) effects.clearBottles();
        long pending = PetEvents.drain();
        for (PetEvent value : PetEvent.ALL) {
            if ((pending & (1L << value.ordinal())) == 0) continue;
            memory.note(value, hide.get(), PetEvents.count(value));
            brain.react(value, hide.get());
            if (rig != null && inWorld && value == PetEvent.THROW_BOTTLE && bottles.get() && fullPose.blend == 0) {
                renderer.anchor(rig, 1);
                effects.throwBottle(renderer.anchorX, renderer.anchorY, motion.direction, crosshair.get(), motion.width, motion.height, size.get());
            }
            if (rig != null && value == PetEvent.DRINK) {
                sfx.play(PetSfxAction.DRINK);
            }
            if (chatter.get() > 0 || value.priority > 0) dialogue.offer(value, now);
        }
        dialogue.update(now, mc.isWindowFocused() && rig != null, quiet, sounds.get(), gap.get(), fullPose, rig);
        if (dialogue.current != null && dialogue.current != lastLine) brain.spoke();
        lastLine = dialogue.current;
    }

    private void input(long now) {
        if (now - lastInput > 60000) brain.greet();
        lastInput = now;
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
            || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed() || mc.options.attackKey.isPressed() || mc.options.useKey.isPressed()) input(now);
        mouseX = mx;
        mouseY = my;
        playerX = x;
        playerY = y;
        playerZ = z;
        yaw = currentYaw;
        pitch = currentPitch;
        observed = true;
    }

    @EventHandler(priority = -1000)
    private void onRender(Render2DEvent event) {
        PetSurfaces support = PetSurfaces.INSTANCE;
        support.bottom = floor.get();
        support.hotbar = hotbar.get();
        support.bars = statusBars.get();
        support.meteor = meteorHud.get();
        support.climbing = climbing.get();
        support.petSize = size.get();
        PetHudCapture.finish();
        PetResources.Rig rig = PetResources.INSTANCE.rig;
        if (rig == null || mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) return;
        float guiScale = mc.getWindow().getScaleFactor();
        event.drawContext.getMatrices().pushMatrix();
        event.drawContext.getMatrices().scale(1 / guiScale, 1 / guiScale);
        event.drawContext.enableScissor(0, 0, motion.width, motion.height);
        try {
            renderer.render(event.drawContext, rig, motion, pose, effects, dialogue, fullPose, size.get(), event.tickDelta);
        } finally {
            event.drawContext.disableScissor();
            event.drawContext.getMatrices().popMatrix();
        }
    }
}
