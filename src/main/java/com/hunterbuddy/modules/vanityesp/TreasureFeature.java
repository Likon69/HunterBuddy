package com.hunterbuddy.modules.vanityesp;

import com.hunterbuddy.modules.regear.util.MapUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

final class TreasureFeature implements VanityFeature {
    private static final int MAX_ANNOUNCED = 100;
    private static final double SHOW_DISTANCE = 64.0;
    private static final String WAYPOINT_NAME = "Buried Treasure";
    private static final String WAYPOINT_INITIALS = "✖";
    private static final Set<Block> BURIAL_BLOCKS = Set.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.GRASS_BLOCK,
        Blocks.MYCELIUM, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY);

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final VanityOptions options;
    private final AlertDispatcher alerts;
    private final List<BlockPos> announced = new ArrayList<>();
    private final Set<BlockPos> looted = new HashSet<>();

    TreasureFeature(VanityOptions options) {
        this.options = options;
        this.alerts = new AlertDispatcher(options.treasureAlerts);
    }

    @Override
    public void activate() {
        if (!options.buriedTreasure.get() || mc.world == null || mc.player == null) return;
        int view = mc.options.getViewDistance().getValue();
        ChunkPos centre = mc.player.getChunkPos();
        for (int x = centre.x - view; x < centre.x + view; x++) {
            for (int z = centre.z - view; z < centre.z + view; z++) {
                chunkData(mc.world.getChunkManager().getWorldChunk(x, z));
            }
        }
    }

    @Override
    public void deactivate() {
        announced.clear();
        looted.clear();
    }

    @Override
    public void chunkData(WorldChunk chunk) {
        if (!options.buriedTreasure.get() || chunk == null) return;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof ChestBlockEntity && isNewBuriedChest(blockEntity.getPos())) {
                announce(blockEntity.getPos());
            }
        }
    }

    private boolean isNewBuriedChest(BlockPos pos) {
        return !announced.contains(pos) && BURIAL_BLOCKS.contains(mc.world.getBlockState(pos.up()).getBlock());
    }

    private void announce(BlockPos pos) {
        alerts.waypoint(pos, WAYPOINT_NAME, WAYPOINT_INITIALS, MapUtil.WpColor.Dark_Red);
        alerts.sound(SoundEvents.BLOCK_CHEST_OPEN, 1.0f);
        alerts.chat(Formatting.GREEN + "Found " + Formatting.GOLD + "buried treasure" + Formatting.GRAY, pos);
        announced.add(pos);
        if (announced.size() > MAX_ANNOUNCED) {
            announced.remove(0);
        }
    }

    @Override
    public void interact(InteractBlockEvent event) {
        if (!options.buriedTreasure.get() || mc.world == null) return;
        BlockPos clicked = event.result.getBlockPos();
        if (!announced.contains(clicked) || !(mc.world.getBlockState(clicked).getBlock() instanceof ChestBlock)) return;
        looted.add(clicked);
        alerts.removeWaypoint(clicked);
    }

    @Override
    public void render(Render3DEvent event) {
        if (!options.buriedTreasure.get() || !options.treasureStyle.drawsShapes() || mc.player == null) return;
        Vec3d player = mc.player.getEntityPos();
        for (BlockPos pos : announced) {
            if (!looted.contains(pos) && pos.isWithinDistance(player, SHOW_DISTANCE)) {
                Outline.block(event, pos, options.treasureStyle);
                Outline.tracer(event, Vec3d.ofCenter(pos), options.treasureStyle);
            }
        }
    }
}
