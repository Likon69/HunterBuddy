package com.hunterbuddy.hud.toast;

import java.util.function.Consumer;

public final class ModuleToastInput {
    private static final ThreadLocal<ModuleToastInput> LOCAL = ThreadLocal.withInitial(ModuleToastInput::new);
    private Object pending;
    private int depth;
    private int authorized;

    private ModuleToastInput() {
    }

    public static ModuleToastInput current() {
        return LOCAL.get();
    }

    public static <T> void perform(T module, Consumer<T> action) {
        ModuleToastInput input = current();
        Object previous = input.pending;
        input.pending = module;
        try {
            action.accept(module);
        } finally {
            input.pending = previous;
        }
    }

    public int enter(Object module) {
        int previous = authorized;
        depth++;
        if (pending == module) authorized = depth;
        pending = null;
        return previous;
    }

    public void leave(int previous) {
        authorized = previous;
        depth--;
    }

    public boolean accepts() {
        return depth > 0 && authorized == depth;
    }
}
