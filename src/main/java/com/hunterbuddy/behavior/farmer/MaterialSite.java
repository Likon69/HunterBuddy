package com.hunterbuddy.behavior.farmer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class MaterialSite {
    public static final int SHULKER_BLOCKS = 1728;
    public long shelterId;
    public int x;
    public int y;
    public int z;
    public int quantity;
    public long observedAt;
    public long verifiedAt;
    public long flightAvoidedUntil;
    public String status = "observed";
    public String reason = "";
    public boolean lowerBound;
    public List<Long> blocks = new ArrayList<>();

    public MaterialSite(BlockPos seed, int quantity, boolean lowerBound, long now) {
        shelterId = seed.asLong();
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

    public boolean flightAvoided() {
        return flightAvoidedUntil > System.currentTimeMillis();
    }

    public void avoidFlight(String reason) {
        flightAvoidedUntil = System.currentTimeMillis() + 1_800_000L;
        this.reason = reason;
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
