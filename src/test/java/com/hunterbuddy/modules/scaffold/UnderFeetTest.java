package com.hunterbuddy.modules.scaffold;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UnderFeetTest {
    @Test
    void stationaryAirPlacementAndTowerStayBelowTheFeetWithoutLeadingThePlayer() {
        assertEquals(new BlockPos(4, 63, -3), NormalTarget.underFeet(new Vec3d(4.8, 64, -2.1)));
        assertEquals(new BlockPos(4, 63, -3), NormalTarget.underFeet(new Vec3d(4.8, 64.95, -2.1)));
        assertEquals(new BlockPos(4, 64, -3), NormalTarget.underFeet(new Vec3d(4.8, 65, -2.1)));
        assertEquals(new BlockPos(-1, -2, -1), NormalTarget.underFeet(new Vec3d(-0.1, -0.01, -0.1)));
    }
}
