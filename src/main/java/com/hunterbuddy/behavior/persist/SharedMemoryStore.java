package com.hunterbuddy.behavior.persist;

import com.hunterbuddy.behavior.core.Blackboard;
import com.hunterbuddy.behavior.core.Key;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class SharedMemoryStore {
    private final Set<String> owners = new LinkedHashSet<>();
    private final Map<String, CompletableFuture<Map<Key<?>, Object>>> loads = new LinkedHashMap<>();
    private final Map<String, Map<Key<?>, Object>> saved = new LinkedHashMap<>();
    private final Map<String, String> savedWorlds = new LinkedHashMap<>();

    public void register(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("empty shared memory owner");
        if (!owners.add(owner)) throw new IllegalArgumentException("duplicate shared memory owner");
    }

    public boolean contains(String owner) {
        return owners.contains(owner);
    }

    public void load(PersistentStore store, String world) {
        loads.clear();
        for (String owner : owners) loads.put(owner, store.load(world, owner));
    }

    public boolean loading() {
        return loads.values().stream().anyMatch(load -> !load.isDone());
    }

    public void restore(Blackboard board) {
        loads.forEach((owner, load) -> board.loadDurable(owner, load.exceptionally(ex -> Map.of()).join()));
        loads.clear();
    }

    public void save(PersistentStore store, String world, Blackboard board) {
        for (String owner : owners) save(owner, store, world, board);
    }

    public void save(String owner, PersistentStore store, String world, Blackboard board) {
        if (store == null || loads.containsKey(owner)) return;
        Map<Key<?>, Object> memory = board.durable(owner);
        if (memory.isEmpty() || world.equals(savedWorlds.get(owner)) && memory.equals(saved.get(owner))) return;
        store.save(world, owner, memory);
        saved.put(owner, memory);
        savedWorlds.put(owner, world);
    }
}
