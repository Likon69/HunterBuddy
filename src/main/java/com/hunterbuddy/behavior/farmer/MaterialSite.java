package com.hunterbuddy.behavior.farmer;

import net.minecraft.util.math.BlockPos;

public final class MaterialSite {
    public static final int SHULKER_BLOCKS = 1728;
    public int x;
    public int y;
    public int z;
    public int quantity;
    public long observedAt;
    public long verifiedAt;
    public String status = "observed";
    public String reason = "";
    public boolean lowerBound;

    public MaterialSite(BlockPos seed, int quantity, boolean lowerBound, long now) {
        x = seed.getX();
        y = seed.getY();
        z = seed.getZ();
        this.quantity = quantity;
        this.lowerBound = lowerBound;
        observedAt = now;
    }

    public BlockPos seed() {
        return new BlockPos(x, y, z);
    }

    public boolean usable() {
        return quantity >= SHULKER_BLOCKS && (status.equals("observed") || status.equals("verified"));
    }

    public void unavailable(String status, String reason) {
        if (!status.equals("exhausted") && !status.equals("inaccessible")) {
            throw new IllegalArgumentException("invalid site disposition");
        }
        this.status = status;
        this.reason = reason;
        verifiedAt = 0;
    }
}
