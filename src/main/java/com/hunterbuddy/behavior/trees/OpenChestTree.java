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
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.InventoryBridge;
import com.hunterbuddy.behavior.mc.InventoryTransaction;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.ClickSlot;
import com.hunterbuddy.behavior.nodes.CloseScreen;
import com.hunterbuddy.behavior.nodes.LookAt;
import com.hunterbuddy.behavior.nodes.OpenContainer;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitScreen;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;

public final class OpenChestTree {
    private static final Key<TargetPoint> TARGET = new Key<>("chest-target", TargetPoint.class, Scope.OWNER, false);

    private OpenChestTree() {
    }

    public static Node build(BlockPos pos, WorldReader world, BaritoneBridge baritone, Outputs outputs) {
        return build(pos, world, baritone, outputs, outputs.inventory()::firstTransfer);
    }

    public static Node build(BlockPos pos, WorldReader world, BaritoneBridge baritone, Outputs outputs,
                             Supplier<InventoryTransaction> transaction) {
        TargetPoint target = new TargetPoint(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        InventoryBridge inventory = outputs.inventory();
        Node open = new Timeout(100, new WithLease(Set.of(Resource.LOOK, Resource.INTERACT, Resource.SCREEN), LeasePolicy.WAIT,
            new Sequence("open-owned-screen", new LookAt(TARGET, 90, world, outputs),
                new OpenContainer(pos, inventory), new WaitScreen(inventory))));
        Node transfer = new Timeout(100, new WithLease(Set.of(Resource.INVENTORY), LeasePolicy.WAIT,
            new ClickSlot(transaction, inventory)));
        Node close = new Timeout(100, new WithLease(Set.of(Resource.SCREEN), LeasePolicy.WAIT, new CloseScreen(inventory)));
        return new TaskScope(new Timeout(12_000, new Sequence("open-chest",
            new SetValue<>(TARGET, ctx -> target),
            GoToTree.build(TARGET, target, 3, world, baritone, outputs),
            new Flow(inventory, open, transfer, close))));
    }

    private static final class Flow extends Composite {
        private final InventoryBridge inventory;
        private Stage stage;
        private int reopens;

        private Flow(InventoryBridge inventory, Node open, Node transfer, Node close) {
            super("chest-transaction", open, transfer, close);
            this.inventory = inventory;
        }

        @Override
        protected void onStart(TickContext ctx) { stage = Stage.OPEN; reopens = 0; }

        @Override
        protected void onResume(TickContext ctx, long ticks, long ms) {
            if (stage != Stage.CLOSE && !inventory.ready() && !inventory.transactionPending()) {
                children().forEach(child -> child.halt(ctx, ExitReason.PREEMPTED));
                children().forEach(Node::resetMemory);
                inventory.retryOpen();
                stage = Stage.OPEN;
                reopens++;
            }
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            if (reopens > 3) return fail(FailureCode.UNREACHABLE, "chest changed after three reopen attempts");
            if (stage == Stage.OPEN) {
                NodeResult result = children().getFirst().execute(ctx);
                if (result instanceof Running || result instanceof Failure) return result;
                stage = Stage.TRANSFER;
            }
            if (stage == Stage.TRANSFER) {
                NodeResult result = children().get(1).execute(ctx);
                if (result instanceof Running || result instanceof Failure) return result;
                stage = Stage.CLOSE;
            }
            if (!inventory.isOurs()) return com.hunterbuddy.behavior.core.Success.INSTANCE;
            return children().get(2).execute(ctx);
        }

        private enum Stage { OPEN, TRANSFER, CLOSE }
    }
}
