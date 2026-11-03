package com.hunterbuddy.modules.rotationdetector;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

final class ChunkScanQueue {
    private final Set<Long> pending = new LinkedHashSet<>();

    synchronized void add(long chunkKey) {
        pending.add(chunkKey);
    }

    synchronized void clear() {
        pending.clear();
    }

    synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    synchronized Long poll() {
        Iterator<Long> first = pending.iterator();
        if (!first.hasNext()) return null;
        Long key = first.next();
        first.remove();
        return key;
    }
}
