package com.hunterbuddy.modules.vanityesp;

import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

record EntityRule(Predicate<Entity> matches, Function<Entity, Box> boxOf, Function<Entity, Vec3d> tracerTarget) {
}
