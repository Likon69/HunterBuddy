package com.hunterbuddy.behavior.routine;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalXZ;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.BaritoneGoTo;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.GateWait;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.util.math.BlockPos;

public final class GateCombatRoutine extends CoreRoutine {
    private final WorldReader world;
    private final BaritoneBridge baritone;
    private final Outputs outputs;

    public GateCombatRoutine(WorldReader world, BaritoneBridge baritone, Outputs outputs) {
        super("gate-combat", Hook.COMBAT, Contributor.ROUTINE);
        this.world = Objects.requireNonNull(world);
        this.baritone = Objects.requireNonNull(baritone);
        this.outputs = Objects.requireNonNull(outputs);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        Objects.requireNonNull(settings);
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        Node flee = new Deferred("flee-from-known-attacker", () -> flee(context));
        GateWait wait = new GateWait("combat gate", ctx -> ctx.sensors().combatTarget() || ctx.series().attackedWithin(100), 60_000, false);
        HookEntry entry = entry(new Condition("combat target or recent attack", sensors -> !flee.active() && !flee.suspended()
            && (sensors.combatTarget() || context.ticks().series().attackedWithin(100))), wait, false);
        entry.guard().onFailureCooldown(200);
        Condition attackerPresent = new Condition("recent attacker still present", sensors -> world.available()
            && sensors.lastAttackerId() >= 0 && world.client().world.getEntityById(sensors.lastAttackerId()) != null
            && context.ticks().series().attackedWithin(100));
        Guard escape = new Guard(new Condition("combat cooldown with known attacker", sensors ->
            !entry.guard().cooledDown(context.ticks().tick()) && attackerPresent.test(sensors)), flee)
            .stayWhile(attackerPresent).onFailureCooldown(200);
        return List.of(entry, new HookEntry(Hook.BOT_PRE, entry.owner(), Contributor.ROUTINE, 0, escape, false, flee));
    }

    private Node flee(RoutineContext context) {
        if (!world.available()) return new Succeed();
        var attacker = world.client().world.getEntityById(context.ticks().sensors().lastAttackerId());
        if (attacker == null) return new Succeed();
        double dx = world.client().player.getX() - attacker.getX();
        double dz = world.client().player.getZ() - attacker.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 0.001) return new Succeed();
        BlockPos destination = BlockPos.ofFloored(world.client().player.getX() + dx * 48 / distance,
            world.client().player.getY(), world.client().player.getZ() + dz * 48 / distance);
        Key<Goal> goal = new Key<>("combat-flee-goal", Goal.class, Scope.OWNER, false);
        return new TaskScope(new Timeout(2400, new Sequence("walk-away-without-breaking",
            new SetValue<>(goal, ctx -> new GoalXZ(destination.getX(), destination.getZ())),
            new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.BARITONE), LeasePolicy.WAIT,
                new BaritoneGoTo(goal, () -> false, false, baritone, outputs)))));
    }

    @Override
    protected String readyDetail() {
        return "KillAuraPlus; attacks=100 ticks; wait=60s; cooldown=200 ticks; flee=48 blocks";
    }
}
