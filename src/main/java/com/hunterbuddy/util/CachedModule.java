package com.hunterbuddy.util;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class CachedModule<T extends Module> {
    private final Class<T> type;
    private T module;

    public CachedModule(Class<T> type) {
        this.type = type;
    }

    public T get() {
        if (module == null) module = Modules.get().get(type);
        return module;
    }
}
