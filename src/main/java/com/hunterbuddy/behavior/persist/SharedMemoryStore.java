package com.hunterbuddy.behavior.persist;

import com.hunterbuddy.behavior.core.Blackboard;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class SharedMemoryStore {
    private final Set<String> owners = new LinkedHashSet<>();
    private final Map<String, CompletableFuture<Map<Key<?>, Object>>> loads = new LinkedHashMap<>();
    private final Map<String, Map<Key<?>, Object>> saved = new LinkedHashMap<>();
    private final Map<String, String> savedWorlds = new LinkedHashMap<>();
    private final Set<String> readFailed = new LinkedHashSet<>();
    private final Map<String, PendingWrite> writes = new LinkedHashMap<>();

    public void register(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("empty shared memory owner");
        if (!owners.add(owner)) throw new IllegalArgumentException("duplicate shared memory owner");
    }

    public boolean contains(String owner) {
        return owners.contains(owner);
    }

    public void load(PersistentStore store, String world) {
        loads.clear();
        readFailed.clear();
        for (String owner : owners) loads.put(owner, store.load(world, owner));
    }

    public boolean loading() {
        return loads.values().stream().anyMatch(load -> !load.isDone());
    }

    public void restore(Blackboard board, Consumer<String> alerts) {
        loads.forEach((owner, load) -> {
            try {
                board.loadDurable(owner, load.join());
            } catch (CompletionException ex) {
                readFailed.add(owner);
                board.clear(owner, Scope.OWNER);
                board.clear(owner, Scope.WORLD);
                alerts.accept("Shared memory unavailable: " + ex.getCause().getMessage());
            }
        });
        loads.clear();
    }

    public void save(PersistentStore store, String world, Blackboard board) {
        for (String owner : owners) save(owner, store, world, board);
    }

    public void save(String owner, PersistentStore store, String world, Blackboard board) {
        if (store == null || loads.containsKey(owner) || readFailed.contains(owner)) return;
        Map<Key<?>, Object> memory = board.durable(owner);
        PendingWrite previous = writes.get(owner);
        if (previous != null) {
            if (!previous.future().isDone()) {
                if (world.equals(previous.world()) && memory.equals(previous.memory())) return;
                saved.remove(owner);
                savedWorlds.remove(owner);
            } else {
                writes.remove(owner);
                if (previous.future().isCompletedExceptionally()) {
                    saved.remove(owner);
                    savedWorlds.remove(owner);
                } else {
                    saved.put(owner, previous.memory());
                    savedWorlds.put(owner, previous.world());
                }
            }
        }
        if (memory.isEmpty() || world.equals(savedWorlds.get(owner)) && memory.equals(saved.get(owner))) return;
        writes.put(owner, new PendingWrite(world, memory, store.save(world, owner, memory)));
    }

    private record PendingWrite(String world, Map<Key<?>, Object> memory, CompletableFuture<Void> future) {}
}
