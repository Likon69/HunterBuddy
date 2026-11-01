package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.DroneCamera;
import com.hunterbuddy.render.DronePose;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 1100)
public abstract class DroneCameraMixin {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"))
    private void hb$droneCamera(World area, Entity focusedEntity, boolean requestedThirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        DroneCamera droneCamera = Modules.get().get(DroneCamera.class);
        if (droneCamera == null) return;

        Camera self = (Camera) (Object) this;
        DronePose pose = droneCamera.pose(self.getCameraPos(), self.getYaw(), self.getPitch(), focusedEntity, tickProgress);
        if (pose == null) return;

        if (pose.thirdPerson()) thirdPerson = true;
        setRotation(pose.yaw(), pose.pitch());
        setPos(pose.x(), pose.y(), pose.z());
    }
}
