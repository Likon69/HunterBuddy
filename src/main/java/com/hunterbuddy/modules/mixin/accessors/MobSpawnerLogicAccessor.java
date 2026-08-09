package com.hunterbuddy.modules.mixin.accessors;

import net.minecraft.block.spawner.MobSpawnerEntry;
import net.minecraft.block.spawner.MobSpawnerLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the countdown and the queued mob out of a spawner.
 *
 * <p>Both fields are private in 1.21.11. Older mappings had them protected, which is why
 * every published version of this detector reaches for them directly and would not compile
 * here.
 *
 * <p>{@code spawnDelay} is the whole detection: the server only ticks it down while a player
 * stands close enough, so a value that has moved away from its default is proof somebody has
 * been here — and on a server nobody else has touched, that somebody left a base.
 */
@Mixin(MobSpawnerLogic.class)
public interface MobSpawnerLogicAccessor {
    @Accessor("spawnDelay")
    int hb$getSpawnDelay();

    @Accessor("spawnEntry")
    MobSpawnerEntry hb$getSpawnEntry();
}
