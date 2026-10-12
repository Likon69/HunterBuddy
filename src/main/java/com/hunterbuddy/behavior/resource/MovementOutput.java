package com.hunterbuddy.behavior.resource;

import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MovementOutput {
    private static final List<Input> KEYS = List.of(Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT,
        Input.JUMP, Input.SNEAK, Input.SPRINT);
    private static final int SPRINT_HOLD_TICKS = 10;
    private final BaritoneBridge bridge;
    private final Set<Input> owned = EnumSet.noneOf(Input.class);
    private IInputOverrideHandler handler;
    private long sprintChangedAt = -SPRINT_HOLD_TICKS;

    public MovementOutput(BaritoneBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge);
    }

    public void apply(MovementIntent intent, long tick) {
        if (intent == null) {
            release(tick);
            return;
        }
        IInputOverrideHandler current = bridge.inputs().orElseThrow(() -> new IllegalStateException("Baritone movement is unavailable"));
        if (handler != current) {
            release(tick);
            handler = current;
        }
        set(Input.MOVE_FORWARD, intent.forward(), tick);
        set(Input.MOVE_BACK, intent.back(), tick);
        set(Input.MOVE_LEFT, intent.left(), tick);
        set(Input.MOVE_RIGHT, intent.right(), tick);
        set(Input.JUMP, intent.jump(), tick);
        set(Input.SNEAK, intent.sneak(), tick);
        boolean sprint = intent.sprint();
        if (sprint != owned.contains(Input.SPRINT) && tick - sprintChangedAt < SPRINT_HOLD_TICKS) sprint = owned.contains(Input.SPRINT);
        set(Input.SPRINT, sprint, tick);
    }

    private void set(Input input, boolean forced, long tick) {
        boolean wasOwned = owned.contains(input);
        if (!forced && !wasOwned) return;
        if (handler.isInputForcedDown(input) != forced) handler.setInputForceState(input, forced);
        if (forced) owned.add(input);
        else owned.remove(input);
        if (input == Input.SPRINT && forced != wasOwned) sprintChangedAt = tick;
    }

    public void release(long tick) {
        if (handler != null) {
            for (Input input : KEYS) {
                if (owned.contains(input)) set(input, false, tick);
            }
        }
        owned.clear();
        handler = null;
    }

    public boolean foreignInput() {
        return bridge.inputs().map(current -> KEYS.stream()
            .anyMatch(input -> current.isInputForcedDown(input) && (current != handler || !owned.contains(input)))).orElse(false);
    }

    public List<String> forcedInputs() {
        return bridge.inputs().map(current -> KEYS.stream().filter(current::isInputForcedDown).map(Enum::name).toList()).orElse(List.of());
    }
}
