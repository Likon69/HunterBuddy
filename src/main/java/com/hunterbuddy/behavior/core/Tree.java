package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.hooks.Hook;
import java.util.Objects;

public record Tree(Node root, Recovery recovery) {
    public Tree {
        Objects.requireNonNull(root);
        Objects.requireNonNull(recovery);
        TreeValidator.validate(root, recovery);
    }

    public static Tree of(Node root, Recovery recovery) {
        Tree tree = new Tree(root, recovery);
        TreeValidator.bind(root, "root", "core", Hook.BOT);
        TreeValidator.bind(recovery, "recovery", "core", Hook.RECOVERY);
        recovery.taskOwner(recovery.taskOwner());
        return tree;
    }
}
