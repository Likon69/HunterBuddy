/*
 * Port of com.lambda.util.collections.UpdatableLazy (lambda 1.21.11).
 * A lazy value that caches its computed result until invalidated.
 */
package com.hunterbuddy.lambda;

public class UpdatableLazy<T> {
    private final java.util.function.Supplier<T> supplier;
    private T cached;
    private boolean computed;

    public UpdatableLazy(java.util.function.Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public T getValue() {
        if (!computed) {
            cached = supplier.get();
            computed = true;
        }
        return cached;
    }

    public void invalidate() {
        computed = false;
        cached = null;
    }

    public static <T> UpdatableLazy<T> of(java.util.function.Supplier<T> supplier) {
        return new UpdatableLazy<>(supplier);
    }
}
