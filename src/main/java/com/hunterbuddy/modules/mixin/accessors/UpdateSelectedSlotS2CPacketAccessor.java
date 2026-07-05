package com.hunterbuddy.modules.mixin.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

@Mixin(UpdateSelectedSlotS2CPacket.class)
public interface UpdateSelectedSlotS2CPacketAccessor {
   @Accessor("slot")
   int getSlot();
}
