/*
 * Port of com.lambda.core.Loadable (lambda 1.21.11) — interface stub.
 * Lambda's core loadable interface for things that can be registered.
 */
package com.hunterbuddy.lambda;

public interface Loadable {
    String load();
    default void unload() {}
}
