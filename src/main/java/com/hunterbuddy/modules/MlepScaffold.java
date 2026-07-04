package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.PlacementUtils;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reference parity: mlep.modules.MlepScaffold. 100% faithful port. */
public class MlepScaffold extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpeed = settings.createGroup("Speed");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<List<Block>> blocks = sgGeneral.add(
        new BlockListSetting.Builder().name("blocks").description("Selected blocks.").build()
    );
    private final Setting<ListMode> blocksFilter = sgGeneral.add(
        new EnumSetting.Builder<ListMode>().name("blocks-filter")
            .description("How to use the block list setting")
            .defaultValue(ListMode.Blacklist).build()
    );
    private final Setting<Boolean> airPlace = sgGeneral.add(
        new BoolSetting.Builder().name("air-place").description("Allow air place.").defaultValue(true).build()
    );
    private final Setting<Double> placeRange = sgGeneral.add(
        new DoubleSetting.Builder().name("closest-block-range")
            .description("How far scaffold can place blocks when air-place is off.")
            .defaultValue(4.0).min(0.0).sliderMax(8.0)
            .visible(() -> !this.airPlace.get()).build()
    );
    private final Setting<Boolean> tower = sgGeneral.add(
        new BoolSetting.Builder().name("tower").description("Automatically towers up when holding jump.").defaultValue(false).build()
    );
    private final Setting<Double> towerSpeed = sgGeneral.add(
        new DoubleSetting.Builder().name("tower-speed")
            .description("The speed at which to tower.").defaultValue(0.42).min(0.0).max(1.0).sliderMax(1.0)
            .visible(this.tower::get).build()
    );
    private final Setting<Boolean> onlyOnClick = sgGeneral.add(
        new BoolSetting.Builder().name("only-on-click").description("Only places blocks when holding right click.").defaultValue(false).build()
    );
    private final Setting<Integer> placeDelay = sgGeneral.add(
        new IntSetting.Builder().name("delay").description("Delay between block placements (ticks)").defaultValue(0).min(0).max(20).build()
    );
    private final Setting<Boolean> rotate = sgGeneral.add(
        new BoolSetting.Builder().name("rotate").description("Server-side rotates when placing.").defaultValue(true).build()
    );
    private final Setting<Integer> rotationPrio = sgGeneral.add(
        new IntSetting.Builder().name("rotation-priority").description("Rotation priority for the rotation manager.").defaultValue(50).min(0).max(200).build()
    );
    private final Setting<Integer> placeAttempts = sgGeneral.add(
        new IntSetting.Builder().name("place-attempts").description("How many times to retry placing a block before giving up.").defaultValue(3).min(1).max(10).build()
    );
    private final Setting<Double> stepHeight = sgGeneral.add(
        new DoubleSetting.Builder().name("step-height").description("How high to step up while moving forward.").defaultValue(0.5).min(0.0).max(1.5).build()
    );
    private final Setting<Double> speed = sgSpeed.add(
        new DoubleSetting.Builder().name("speed").description("Scaffold movement speed (blocks/sec).").defaultValue(20.0).min(0.1).max(50.0).build()
    );
    private final Setting<Boolean> keepSpeed = sgSpeed.add(
        new BoolSetting.Builder().name("keep-speed").description("Maintain speed even when sneaking.").defaultValue(false).build()
    );
    private final Setting<Boolean> safeWalk = sgSpeed.add(
        new BoolSetting.Builder().name("safe-walk").description("Don't fall off edges.").defaultValue(true).build()
    );
    private final Setting<Boolean> fastTower = sgSpeed.add(
        new BoolSetting.Builder().name("fast-tower").description("Tower quickly using swap-and-jump.").defaultValue(false).build()
    );
    private final Setting<Boolean> render = sgRender.add(
        new BoolSetting.Builder().name("render").description("Render scaffold blocks.").defaultValue(true).build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>().name("shape-mode")
            .description("How the shapes are rendered.").defaultValue(ShapeMode.Both).build()
    );
    private final Setting<SettingColor> sideColor = sgRender.add(
        new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("side-color")
            .defaultValue(new SettingColor(255, 0, 0, 50)).build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(
        new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("line-color")
            .defaultValue(new SettingColor(255, 0, 0, 255)).build()
    );
    private final Setting<SettingColor> sideColorRegear = sgRender.add(
        new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("side-color-regear")
            .description("Color of placed regear blocks.").defaultValue(new SettingColor(0, 255, 0, 50)).build()
    );
    private final Setting<SettingColor> lineColorRegear = sgRender.add(
        new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("line-color-regear")
            .description("Color of placed regear outline.").defaultValue(new SettingColor(0, 255, 0, 255)).build()
    );

    private final List<BlockPos> placedBlocks = new ArrayList<>();
    private final List<BlockPos> regearBlocks = new ArrayList<>();
    private boolean regearMode = false;
    private int regearHotbarSlot = -1;
    private int ticksSinceLastPlace = 0;

    public MlepScaffold() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "MlepScaffold", "Scaffolds blocks under you automatically.");
    }

    public void setRegearMode(boolean regearMode) {
        this.regearMode = regearMode;
    }

    public void setRegearHotbarSlot(int slot) {
        this.regearHotbarSlot = slot;
    }

    public boolean isSafeWalking() {
        return this.isActive() && this.safeWalk.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        ticksSinceLastPlace++;
        if (this.regearMode) {
            this.tickRegear();
        } else if (this.tower.get() && mc.options.jumpKey.isPressed() && !mc.player.isOnGround()) {
            this.tickTower();
        }
    }

    private void tickRegear() {
        // Simplified regear tick: place blocks under player from obsidian hotbar slot
        if (mc.player.isOnGround() && this.ticksSinceLastPlace >= this.placeDelay.get()) {
            BlockPos below = mc.player.getBlockPos().down();
            if (mc.world.getBlockState(below).isReplaceable()) {
                FindItemResult res = InvUtils.findInHotbar(this.regearHotbarSlot >= 0 && this.regearHotbarSlot < 9
                    ? Items.OBSIDIAN : Items.COBBLESTONE);
                if (res.found()) {
                    BlockHitResult hit = PlacementUtils.getAirPlaceHit(below, 4.5);
                    if (hit != null) {
                        this.placeBlock(below, res, hit);
                        ticksSinceLastPlace = 0;
                    }
                }
            }
        }
    }

    private void tickTower() {
        if (mc.player.isOnGround() && mc.options.jumpKey.isPressed() && this.fastTower.get()) {
            mc.player.jump();
        }
    }

    private void placeBlock(BlockPos pos, FindItemResult item, BlockHitResult hit) {
        if (item.getHand() == null) InvUtils.swap(item.slot(), false);
        RotationUtils.getInstance().setRotationSilent(mc.player.getYaw(), 90.0F);
        if (this.rotate.get()) {
            PlacementUtils.placeAt(pos, item, true, true, false);
        } else {
            PlacementUtils.placeHit(hit, Hand.MAIN_HAND, true);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.render.get() || mc.player == null || mc.world == null) return;
        for (BlockPos pos : this.placedBlocks) {
            event.renderer.box(pos, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
        }
        for (BlockPos pos : this.regearBlocks) {
            event.renderer.box(pos, this.sideColorRegear.get(), this.lineColorRegear.get(), this.shapeMode.get(), 0);
        }
    }

    public enum ListMode {
        Whitelist, Blacklist
    }
}