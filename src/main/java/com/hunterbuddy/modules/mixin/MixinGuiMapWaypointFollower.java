package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.WaypointFollower;
import com.hunterbuddy.modules.mapwaypoint.MapWaypointOption;
import com.hunterbuddy.modules.mapwaypoint.MapWaypointPlacer;
import com.hunterbuddy.modules.mapwaypoint.WaypointKind;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMapWaypointFollower implements IRightClickableElement {
    @Unique
    private static final String KEYSYM_TYPE = "KEYSYM";
    @Unique
    private static final int NO_KEY = -1;
    @Unique
    private static final String TEXT_FIELD_MARKER = "TextField";

    @Shadow
    private int rightClickX;
    @Shadow
    private int rightClickY;
    @Shadow
    private int rightClickZ;
    @Shadow
    private double rightClickCoordinateScale;
    @Shadow
    private int mouseBlockPosX;
    @Shadow
    private int mouseBlockPosY;
    @Shadow
    private int mouseBlockPosZ;
    @Shadow
    private double mouseBlockCoordinateScale;

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
    private void hunterBuddy$addWaypointOptions(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        WaypointFollower follower = Modules.get().get(WaypointFollower.class);
        if (follower == null) return;
        ArrayList<RightClickOption> options = cir.getReturnValue();
        hunterBuddy$appendOption(options, WaypointKind.HUNT, follower);
        hunterBuddy$appendOption(options, WaypointKind.ROUTE_MARK, follower);
    }

    @Inject(
        method = "onInputPress(Lnet/minecraft/class_3675$class_307;I)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hunterBuddy$handleKey(@Coerce Object type, int code, CallbackInfoReturnable<Boolean> cir) {
        if (!KEYSYM_TYPE.equals(type.toString()) || hunterBuddy$typingInTextField()) return;
        WaypointFollower follower = Modules.get().get(WaypointFollower.class);
        if (follower == null) return;
        if (code != NO_KEY && code == follower.getSeedRouteKeyCode()) {
            follower.seedRouteFromMap();
            cir.setReturnValue(true);
        } else if (code == follower.getAddRouteKeyCode()) {
            hunterBuddy$placeAtMouse(follower, WaypointKind.ROUTE_MARK);
            cir.setReturnValue(true);
        } else if (code == follower.getAddWaypointKeyCode()) {
            hunterBuddy$placeAtMouse(follower, WaypointKind.HUNT);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void hunterBuddy$appendOption(ArrayList<RightClickOption> options, WaypointKind kind, WaypointFollower follower) {
        Runnable placeAtClick = () -> new MapWaypointPlacer(follower).place(rightClickX, rightClickZ, kind);
        options.add(new MapWaypointOption(kind.menuLabel(), options.size(), this, placeAtClick));
    }

    @Unique
    private void hunterBuddy$placeAtMouse(WaypointFollower follower, WaypointKind kind) {
        rightClickX = mouseBlockPosX;
        rightClickY = mouseBlockPosY;
        rightClickZ = mouseBlockPosZ;
        rightClickCoordinateScale = mouseBlockCoordinateScale;
        new MapWaypointPlacer(follower).place(rightClickX, rightClickZ, kind);
    }

    @Unique
    private static boolean hunterBuddy$typingInTextField() {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        Element focused = screen == null ? null : screen.getFocused();
        if (focused == null) return false;
        return focused instanceof TextFieldWidget || focused.getClass().getName().contains(TEXT_FIELD_MARKER);
    }
}
