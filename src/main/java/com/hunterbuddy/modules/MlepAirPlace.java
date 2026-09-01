package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.PlacementUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class MlepAirPlace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRange = settings.createGroup("range");

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("The delay in ticks between block placements.")
        .defaultValue(1)
        .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders a block overlay where the block will be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(12, 0, 204, 255))
        .build()
    );

    private final Setting<Boolean> customRange = sgRange.add(new BoolSetting.Builder()
        .name("custom-range")
        .description("Use custom range for air place.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> range = sgRange.add(new DoubleSetting.Builder()
        .name("range")
        .description("Custom range to place at.")
        .visible(customRange::get)
        .defaultValue(5.0)
        .min(0.0)
        .sliderMax(5.5)
        .build()
    );

    private HitResult hitResult;
    private int delay = 0;
    private boolean wasPressed = false;

    public MlepAirPlace() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "mlep-air-place", "Places a block in air where your crosshair is pointing.");
    }

    @Override
    public void onActivate() {
        delay = 0;
        wasPressed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (delay < placeDelay.get()) delay++;

        double r = customRange.get() ? range.get() : mc.player.getBlockInteractionRange();
        hitResult = mc.getCameraEntity().raycast(r, 0.0F, false);

        if (hitResult instanceof BlockHitResult blockHitResult
            && (mc.player.getMainHandStack().getItem() instanceof BlockItem
                || mc.player.getMainHandStack().getItem() instanceof SpawnEggItem)) {

            boolean isPressed = mc.options.useKey.isPressed();
            if (mc.currentScreen != null) {
                wasPressed = isPressed;
            } else {
                if (isPressed && !wasPressed && delay >= placeDelay.get()) {
                    BlockPos targetPos = blockHitResult.getBlockPos();
                    if (!mc.world.getBlockState(targetPos).isReplaceable()) {
                        wasPressed = isPressed;
                        return;
                    }
                    PlacementUtils.grimPlace(blockHitResult);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    delay = 0;
                }
                wasPressed = isPressed;
            }
        } else {
            wasPressed = false;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (hitResult instanceof BlockHitResult blockHitResult
            && mc.world.getBlockState(blockHitResult.getBlockPos()).isReplaceable()
            && (mc.player.getMainHandStack().getItem() instanceof BlockItem
                || mc.player.getMainHandStack().getItem() instanceof SpawnEggItem)
            && render.get()) {
            event.renderer.box(blockHitResult.getBlockPos(), (Color) sideColor.get(), (Color) lineColor.get(), (ShapeMode) shapeMode.get(), 0);
        }
    }
}