package com.hunterbuddy.modules.signs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.world.ClientWorld;

public final class SignWatch {
    private static final int FORGET_SCANS = 3;

    private final Map<SignKey, SignRecord> originals;
    private final Set<SignKey> destroyed = new HashSet<>();
    private final Set<SignKey> modified = new HashSet<>();
    private final Map<SignKey, Integer> forgotten = new HashMap<>();
    private List<SignRecord> destroyedList = List.of();
    private List<SignRecord> modifiedList = List.of();
    private String listedDimension = "";
    private boolean dirty;

    public SignWatch(Map<SignKey, SignRecord> originals) {
        this.originals = originals;
    }

    public void scan(ClientWorld world, String dimension) {
        Set<SignKey> present = new HashSet<>();
        for (BlockEntity entity : Utils.blockEntities()) {
            if (entity instanceof SignBlockEntity sign) present.add(observe(SignRecord.of(dimension, sign)));
        }
        for (SignKey key : originals.keySet()) {
            if (!key.dimension().equals(dimension) || present.contains(key)) continue;
            if (!world.getChunkManager().isChunkLoaded(key.x() >> 4, key.z() >> 4)) continue;
            destroyed.add(key);
            modified.remove(key);
        }
        expireForgotten(present);
        rebuild(dimension);
    }

    public List<SignRecord> destroyed(String dimension) {
        return dimension.equals(listedDimension) ? destroyedList : List.of();
    }

    public List<SignRecord> modified(String dimension) {
        return dimension.equals(listedDimension) ? modifiedList : List.of();
    }

    public SignRecord damagedOriginal(SignKey key) {
        return destroyed.contains(key) || modified.contains(key) ? originals.get(key) : null;
    }

    public void forget(SignKey key) {
        if (originals.remove(key) == null) return;
        destroyed.remove(key);
        modified.remove(key);
        forgotten.put(key, FORGET_SCANS);
        dirty = true;
        rebuild(listedDimension);
    }

    public void adopt(SignKey key, boolean front, List<String> lines) {
        SignRecord original = originals.get(key);
        if (original == null) return;
        originals.put(key, original.withFace(front, lines));
        destroyed.remove(key);
        modified.remove(key);
        dirty = true;
        rebuild(listedDimension);
    }

    public boolean takeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    public List<SignRecord> snapshot() {
        return new ArrayList<>(originals.values());
    }

    private SignKey observe(SignRecord seen) {
        SignKey key = seen.key();
        SignRecord original = originals.get(key);
        destroyed.remove(key);
        if (original == null) {
            if (!seen.isBlank() && !forgotten.containsKey(key)) {
                originals.put(key, seen);
                dirty = true;
            }
        } else if (original.sameText(seen)) {
            modified.remove(key);
        } else {
            modified.add(key);
        }
        return key;
    }

    private void expireForgotten(Set<SignKey> present) {
        Iterator<Map.Entry<SignKey, Integer>> entries = forgotten.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<SignKey, Integer> entry = entries.next();
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() <= 0 || !present.contains(entry.getKey())) entries.remove();
        }
    }

    private void rebuild(String dimension) {
        listedDimension = dimension;
        destroyedList = originalsOf(destroyed, dimension);
        modifiedList = originalsOf(modified, dimension);
    }

    private List<SignRecord> originalsOf(Set<SignKey> keys, String dimension) {
        List<SignRecord> records = new ArrayList<>();
        for (SignKey key : keys) {
            if (key.dimension().equals(dimension)) records.add(originals.get(key));
        }
        return records;
    }
}
