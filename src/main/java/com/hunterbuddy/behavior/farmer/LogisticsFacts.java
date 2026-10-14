package com.hunterbuddy.behavior.farmer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class LogisticsFacts {
    public String profile;
    public String dimension;
    public boolean store;
    public boolean regear;
    public boolean exitTop;
    public boolean groundExitRequired;
    public boolean baseRequested;
    public int step;
    public String materialSite = "";
    public boolean reusedShelter;
    public int[] site;
    public int[] departure;
    public List<int[]> zone = new ArrayList<>();
    public int siteAttempts;
    public int[] building;
    public String buildingItem = "";
    public List<int[]> walls = new ArrayList<>();
    public List<String> wallItems = new ArrayList<>();
    public String enderContents = "";
    public String incomplete = "";
    public String exitItem = "";
    public int exitBaseline;
    public boolean exitBreaking;
    public ShulkerCycleFacts material = new ShulkerCycleFacts();
    public ShulkerCycleFacts divers = new ShulkerCycleFacts();
    public ShulkerCycleFacts reserve = new ShulkerCycleFacts();
    public TransferFacts transfer = new TransferFacts();

    public static int[] coordinates(BlockPos pos) {
        return new int[]{
            pos.getX(), pos.getY(), pos.getZ()
        };
    }

    public static BlockPos position(int[] pos) {
        return new BlockPos(pos[0], pos[1], pos[2]);
    }

    public FarmerZone activeZone() {
        return FarmerZone.restore(zone.stream().map(LogisticsFacts::position).toList());
    }

    public BlockPos origin() {
        return position(site);
    }

    public BlockPos chest() {
        return origin().east();
    }

    public BlockPos shulker() {
        return origin().south();
    }

    public BlockPos door() {
        return origin().north(2);
    }
}
