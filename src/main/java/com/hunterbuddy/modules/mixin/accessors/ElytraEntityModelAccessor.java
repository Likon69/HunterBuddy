package com.hunterbuddy.modules.mixin.accessors;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ElytraEntityModel.class)
public interface ElytraEntityModelAccessor {
   @Accessor("leftWing")
   ModelPart getLeftWing();

   @Accessor("rightWing")
   ModelPart getRightWing();
}
