package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public final class PvpWatch {
    private final PvpSettings settings;
    private final PvpAnnouncer announcer;
    private final Set<BlockPos> anchors = ConcurrentHashMap.newKeySet();

    public PvpWatch(PvpSettings settings, PvpAnnouncer announcer) {
        this.settings = settings;
        this.announcer = announcer;
    }

    public void clear() {
        anchors.clear();
    }

    public void onPacket(Packet<?> packet) {
        if (packet instanceof BundleS2CPacket bundle) {
            for (Packet<?> inner : bundle.getPackets()) onPacket(inner);
        } else if (packet instanceof EntitySpawnS2CPacket spawn) {
            onSpawn(spawn);
        } else if (packet instanceof EntitiesDestroyS2CPacket destroy) {
            onDestroy(destroy);
        } else if (packet instanceof BlockUpdateS2CPacket update) {
            onBlockUpdate(update);
        }
    }

    public void forgetVanishedAnchors() {
        anchors.removeIf(position -> !MeteorClient.mc.world.getBlockState(position).isOf(Blocks.RESPAWN_ANCHOR));
    }

    private void onSpawn(EntitySpawnS2CPacket spawn) {
        if (spawn.getEntityType() != EntityType.END_CRYSTAL || !settings.crystalPlace.get()) return;
        announcer.announce(PvpEvent.CRYSTAL_PLACED, new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ()));
    }

    private void onDestroy(EntitiesDestroyS2CPacket destroy) {
        if (!settings.crystalPop.get() || MeteorClient.mc.world == null) return;
        destroy.getEntityIds().forEach(id -> {
            Entity entity = MeteorClient.mc.world.getEntityById(id);
            if (entity instanceof EndCrystalEntity) announcer.announce(PvpEvent.CRYSTAL_POPPED, entity.getEntityPos());
        });
    }

    private void onBlockUpdate(BlockUpdateS2CPacket update) {
        BlockPos position = update.getPos().toImmutable();
        boolean isAnchor = update.getState().isOf(Blocks.RESPAWN_ANCHOR);
        if (isAnchor && anchors.add(position)) {
            if (settings.anchorPlace.get()) announcer.announce(PvpEvent.ANCHOR_PLACED, Vec3d.ofCenter(position));
        } else if (!isAnchor && anchors.remove(position) && settings.anchorPop.get()) {
            announcer.announce(PvpEvent.ANCHOR_POPPED, Vec3d.ofCenter(position));
        }
    }
}
