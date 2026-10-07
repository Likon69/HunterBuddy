package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.sorter.model.SorterProfile;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import static meteordevelopment.meteorclient.MeteorClient.mc;

final class SorterActivation {
    private record AutoStart(ClientWorld world, SorterProfile profile, BlockPos pos) { }

    private static final long AUTO_START_DELAY = 30_000_000_000L;
    private static final long OPEN_DELAY = 5_000_000_000L;

    private final ShulkerSorter owner;
    private final SorterSafety safety;
    private ClientWorld inputWorld;
    private SorterProfile inputProfile;
    private BlockPos inputPos;
    private long interactedAt;
    private GenericContainerScreenHandler inputHandler;
    private GenericContainerScreenHandler manuallyClosing;
    private AutoStart pendingAutoStart;
    private boolean dryRunNotified;
    private long lastAutoStart;
    private boolean pendingResume;
    private ClientWorld resumeWorld;
    private long resumeClearSince;
    private boolean internalToggle;

    SorterActivation(ShulkerSorter owner, SorterSafety safety) {
        this.owner = owner;
        this.safety = safety;
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    void onToggle() {
        if (!internalToggle) {
            pendingResume = false;
            resumeClearSince = 0;
        }
        pendingAutoStart = null;
        forgetInput();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onInteract(InteractBlockEvent event) {
        if (event.isCancelled() || !canWatchInput() || mc.currentScreen != null) {
            return;
        }
        forgetInput();
        SorterProfile profile = SorterStore.get();
        BlockPos pos = owner.setup().registeredPosition(profile, event.result.getBlockPos());
        if (profile.inputs.contains(SorterStore.pos(pos))) {
            inputWorld = mc.world;
            inputProfile = profile;
            inputPos = pos.toImmutable();
            interactedAt = System.nanoTime();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onScreen(OpenScreenEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (event.screen instanceof GenericContainerScreen screen && canWatchInput()
            && inputPos != null && mc.world == inputWorld && SorterStore.get() == inputProfile
            && System.nanoTime() - interactedAt <= OPEN_DELAY) {
            inputHandler = screen.getScreenHandler();
        } else {
            forgetInput();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onKey(KeyEvent event) {
        if (event.action == KeyAction.Press && !event.isCancelled() && canWatchInput()
            && inputHandler != null && mc.player.currentScreenHandler == inputHandler
            && (event.input.key() == GLFW.GLFW_KEY_ESCAPE || mc.options.inventoryKey.matchesKey(event.input))) {
            manuallyClosing = inputHandler;
        }
    }

    @EventHandler
    private void onPacketSent(PacketEvent.Sent event) {
        if (!(event.packet instanceof CloseHandledScreenC2SPacket packet) || !canWatchInput()
            || inputHandler == null || manuallyClosing != inputHandler
            || mc.player.currentScreenHandler != inputHandler || packet.getSyncId() != inputHandler.syncId
            || mc.world != inputWorld || SorterStore.get() != inputProfile
            || !inputProfile.inputs.contains(SorterStore.pos(inputPos))) {
            return;
        }
        manuallyClosing = null;
        boolean occupied = false;
        for (int slot = 0; slot < inputHandler.getRows() * 9; slot++) {
            if (!inputHandler.getSlot(slot).getStack().isEmpty()) {
                occupied = true;
                break;
            }
        }
        if (!occupied || safety.waitingReason(owner.ignoreFriends()) != null) {
            return;
        }
        if (owner.dryRun()) {
            if (!dryRunNotified) {
                owner.warning("Auto-start requires dry-run to be off.");
                dryRunNotified = true;
            }
            return;
        }
        if (lastAutoStart != 0 && System.nanoTime() - lastAutoStart < AUTO_START_DELAY) {
            return;
        }
        pendingAutoStart = new AutoStart(inputWorld, inputProfile, inputPos);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (owner.isActive()) {
            pendingResume = owner.resumeAfterReconnect() && !owner.isWithdrawing();
            internalToggle = true;
            try {
                owner.stopForDisconnect();
            } finally {
                internalToggle = false;
            }
        }
        pendingAutoStart = null;
        forgetInput();
        resumeWorld = null;
        resumeClearSince = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        manuallyClosing = null;
        if (!owner.autoStart() || !owner.dryRun()) {
            dryRunNotified = false;
        }
        if (!owner.resumeAfterReconnect()) {
            pendingResume = false;
        }
        if (!canWatchInput() || mc.world != inputWorld
            || inputHandler != null && mc.player.currentScreenHandler != inputHandler
            || inputHandler == null && System.nanoTime() - interactedAt > OPEN_DELAY) {
            forgetInput();
        }
        if (pendingAutoStart != null) {
            AutoStart requested = pendingAutoStart;
            pendingAutoStart = null;
            if (canWatchInput() && !owner.dryRun() && mc.world == requested.world()
                && SorterStore.get() == requested.profile()
                && requested.profile().inputs.contains(SorterStore.pos(requested.pos())) && readyScreen()
                && safety.waitingReason(owner.ignoreFriends()) == null) {
                lastAutoStart = System.nanoTime();
                activate();
            }
        }
        if (resumeWorld != mc.world) {
            resumeWorld = mc.world;
            resumeClearSince = 0;
        }
        if (!pendingResume || owner.isActive() || mc.world == null || mc.player == null
            || owner.setup().isInspecting() || !readyScreen()
            || safety.waitingReason(owner.ignoreFriends()) != null || !nearInput()) {
            resumeClearSince = 0;
            return;
        }
        long now = System.nanoTime();
        if (resumeClearSince == 0) {
            resumeClearSince = now;
        } else if (now - resumeClearSince >= SorterSafety.RESUME_DELAY_NANOS) {
            activate();
        }
    }

    private boolean canWatchInput() {
        return !owner.isActive() && owner.autoStart() && !owner.setup().isInspecting()
            && mc.world != null && mc.player != null;
    }

    private boolean readyScreen() {
        return mc.currentScreen == null && mc.player.currentScreenHandler == mc.player.playerScreenHandler
            && mc.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    private boolean nearInput() {
        for (SorterProfile.Pos pos : SorterStore.get().inputs) {
            if (mc.player.squaredDistanceTo(Vec3d.ofCenter(SorterStore.blockPos(pos))) <= 64 * 64) {
                return true;
            }
        }
        return false;
    }

    private void activate() {
        pendingResume = false;
        resumeClearSince = 0;
        internalToggle = true;
        try {
            owner.toggle();
        } finally {
            internalToggle = false;
        }
    }

    private void forgetInput() {
        inputWorld = null;
        inputProfile = null;
        inputPos = null;
        inputHandler = null;
        manuallyClosing = null;
    }
}
