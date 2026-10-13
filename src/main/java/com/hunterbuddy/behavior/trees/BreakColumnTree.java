package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.BreakBlock;
import com.hunterbuddy.behavior.nodes.PillarUp;
import com.hunterbuddy.behavior.nodes.ProgressGuard;
import com.hunterbuddy.behavior.nodes.SwimUp;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.ScanColumn;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class BreakColumnTree {
    private static final Key<BlockScan> SCAN = new Key<>("break-column-scan", BlockScan.class, Scope.TASK, false);
    private static final Key<BlockPos> BLOCK = new Key<>("break-column-block", BlockPos.class, Scope.TASK, false);

    private BreakColumnTree() {
    }

    public static Node build(Key<BlockPos> origin, int height, WorldReader world, Outputs outputs) {
        Node scan = new Timeout(100, new ScanColumn(origin, height, SCAN, world));
        Node mine = new Timeout(300, new WithLease(Set.of(Resource.MINING), LeasePolicy.WAIT, new BreakBlock(BLOCK, true, world, outputs)));
        Node rise = new ProgressGuard(sensors -> sensors.y(), 0.5, 200, new Timeout(1_800,
            new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.INTERACT), LeasePolicy.WAIT,
                new PillarUp(ctx -> ctx.bb().get(ctx.owner(), BLOCK).orElseThrow().getY() - 3, world, outputs))));
        Node swim = new ProgressGuard(sensors -> sensors.y(), 0.5, 1_200, new Timeout(1_200,
            new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, new SwimUp(origin, world, outputs))));
        return new TaskScope(new Timeout(12_000, new Column(world, scan, mine, rise, swim)));
    }

    private static final class Column extends Composite {
        private final WorldReader world;
        private BlockScan scan;
        private int index;

        private Column(WorldReader world, Node scan, Node mine, Node rise, Node swim) {
            super("break-column", scan, mine, rise, swim);
            this.world = world;
        }

        @Override
        protected void onStart(TickContext ctx) {
            if (scan != null && scan.world().equals(world.identity())) return;
            scan = null;
            index = 0;
        }

        @Override
        protected void onReset() {
            scan = null;
            index = 0;
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            if (scan == null) {
                NodeResult result = children().getFirst().execute(ctx);
                if (!(result instanceof Success)) return result;
                scan = ctx.bb().get(ctx.owner(), SCAN).orElseThrow();
            }
            if (!scan.world().equals(world.identity())) return fail(FailureCode.REFUSED, "column world changed");
            if (index >= scan.blocks().size()) return Success.INSTANCE;
            if (!world.available()) return fail(FailureCode.UNREACHABLE, "Minecraft world is unavailable");
            BlockPos pos = scan.blocks().get(index);
            ctx.bb().set(ctx.owner(), BLOCK, pos);
            var eyes = world.eyes();
            if (Math.hypot(eyes.x() - pos.getX() - 0.5, eyes.z() - pos.getZ() - 0.5) > 4.5) {
                return fail(FailureCode.UNREACHABLE, "player moved outside the scanned column");
            }
            double distance = Math.sqrt(Math.pow(eyes.x() - pos.getX() - 0.5, 2) + Math.pow(eyes.y() - pos.getY() - 0.5, 2)
                + Math.pow(eyes.z() - pos.getZ() - 0.5, 2));
            if (children().get(2).active() || distance > 4.5) {
                int branch = !children().get(2).active() && ctx.sensors().submerged() && !world.openDryNeighbor(world.feet()) ? 3 : 2;
                NodeResult rise = children().get(branch).execute(ctx);
                if (rise instanceof Failure) return rise;
                if (rise instanceof Running) return rise;
                children().get(branch).resetMemory();
                return new Running("column rose into mining reach");
            }
            children().get(3).halt(ctx, ExitReason.COMPLETED);
            NodeResult mined = children().get(1).execute(ctx);
            if (!(mined instanceof Success)) return mined;
            index++;
            children().get(1).resetMemory();
            return index >= scan.blocks().size() ? Success.INSTANCE : new Running("column blocks=" + index + "/" + scan.blocks().size());
        }
    }
}
