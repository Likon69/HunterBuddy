package com.hunterbuddy.chatdock;

import java.util.function.BooleanSupplier;

public record DockEntry(String id, String label, BooleanSupplier visible, BooleanSupplier selected, Runnable action) {
}
