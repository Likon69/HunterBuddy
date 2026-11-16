package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mine.MiningSource;
import com.hunterbuddy.modules.nuker.NukerSettings;
import com.hunterbuddy.modules.nuker.NukerTargets;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.math.BlockPos;

public final class HNuker extends Module implements MiningSource {
    private final NukerSettings config = new NukerSettings(settings);
    private final NukerTargets targets = new NukerTargets(config);
    private String status = "Idle";

    public HNuker() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "h-nuker", "Selects nearby blocks for HMine, using its tools, timing and double-break engine.");
    }

    @Override
    public void onActivate() {
        status = "Idle";
        HMine mine = Modules.get().get(HMine.class);
        if (mine != null && !mine.isActive()) mine.toggle();
    }

    @Override
    public void onDeactivate() {
        HMine mine = Modules.get().get(HMine.class);
        if (mine != null) mine.releaseSource(this);
        status = "Idle";
    }

    @EventHandler(priority = 100)
    private void onTick(TickEvent.Pre event) {
        HMine mine = Modules.get().get(HMine.class);
        if (mine == null || !mine.isActive()) {
            status = "HMine off";
            return;
        }
        if (!ready()) {
            mine.releaseSource(this);
            status = "Paused";
            return;
        }
        if (!mine.packetMode()) {
            status = "HMine needs PACKET";
            return;
        }
        if (!mine.acquireSource(this)) status = "HMine busy";
    }

    @Override
    public boolean ready() {
        return isActive() && mc.player != null && mc.world != null && mc.interactionManager != null
            && mc.getNetworkHandler() != null && mc.player.isAlive() && !mc.player.isCreative()
            && !mc.player.isSpectator() && mc.player.getAbilities().allowModifyWorld
            && !(mc.player.currentScreenHandler != mc.player.playerScreenHandler || mc.currentScreen instanceof InventoryScreen)
            && !mc.options.attackKey.isPressed();
    }

    @Override
    public boolean accepts(BlockPos pos) {
        HMine mine = Modules.get().get(HMine.class);
        return mine != null && targets.accepts(mine, pos);
    }

    @Override
    public BlockPos next(Predicate<BlockPos> unavailable) {
        HMine mine = Modules.get().get(HMine.class);
        if (mine == null || !ready()) return null;
        BlockPos next = targets.next(mine, unavailable);
        status = next == null ? "No target" : "Mining";
        return next;
    }

    @Override
    public boolean shouldRender() {
        return config.render.get();
    }

    @Override
    public String getInfoString() {
        return status;
    }
}
