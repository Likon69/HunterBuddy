package com.hunterbuddy.modules.scaffold;

import com.hunterbuddy.modules.regear.util.PlacementUtils;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

final class ScaffoldPlacement {
    private final ScaffoldSettings settings;
    private final PlacedBlocks placed;
    private final PlaceDelay delay;
    private boolean sent;
    private final ScaffoldPrediction prediction = new ScaffoldPrediction();
    private final ScaffoldRotation rotation = new ScaffoldRotation();

    ScaffoldPlacement(ScaffoldSettings settings, PlacedBlocks placed, PlaceDelay delay) {
        this.settings = settings;
        this.placed = placed;
        this.delay = delay;
    }

    void reconcile() { prediction.reconcile(); }

    void reset() { cancel(); prediction.clear(); }

    void cancel() {
        rotation.reset();
    }

    void pre() {
        rotation.pre();
    }

    void post() {
        rotation.post();
    }

    void movementSent(PlayerMoveC2SPacket packet) {
        rotation.sent(packet);
    }

    boolean place(PlacementTarget target, FindItemResult block, Runnable onPlaced) {
        if (!block.found() || !block.isOffhand() && !block.isMainHand() && !settings.autoSwitch.get()) return false;
        if (target.hit().isInsideBlock()) {
            if (!placeNow(target, block)) return false;
            if (onPlaced != null) onPlaced.run();
            return true;
        }
        return rotation.schedule(target.hit().getPos(), settings.rotate.get(), settings.postRotate.get(), () -> {
            if (placeNow(target, block) && onPlaced != null) onPlaced.run();
        });
    }

    private boolean placeNow(PlacementTarget target, FindItemResult block) {
        sent = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!block.found() || mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return false;
        if (!SupportHit.usable(target.position())) return false;
        BlockHitResult hit = target.hit();
        if (hit.isInsideBlock() && (!settings.airPlace.get() || block.isOffhand())) return false;
        if (!SupportHit.visible(hit, mc.player.getEyePos(), SupportHit.reach(settings))) return false;
        ItemStack stack = block.isOffhand() ? mc.player.getOffHandStack() : mc.player.getInventory().getStack(block.slot());
        if (!(stack.getItem() instanceof BlockItem item) || !BlockUtils.canPlaceBlock(target.position(), true, item.getBlock())) return false;
        if (!settings.rotate.get()) {
            BlockHitResult aimed = mc.world.raycast(new RaycastContext(mc.player.getEyePos(),
                mc.player.getEyePos().add(mc.player.getRotationVec(1).multiply(SupportHit.reach(settings))),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (!hit.isInsideBlock()) {
                if (aimed.getType() != HitResult.Type.BLOCK || !aimed.getBlockPos().equals(hit.getBlockPos()) || aimed.getSide() != hit.getSide()) return false;
                hit = aimed;
            }
        }
        BlockHitResult click = hit;
        ScaffoldHand.run(block, settings.autoSwitch.get(), () -> send(click, block.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND, item.getBlock().getDefaultState()));
        if (sent) {
            placed.add(target.position());
            delay.reset();
        }
        return sent;
    }

    private void send(BlockHitResult hit, Hand hand, BlockState state) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        ((IClientPlayerInteractionManager) mc.interactionManager).meteor$syncSelected();
        PlayerInput input = player.input.playerInput;
        boolean sneak = !hit.isInsideBlock() && BlockUtils.isClickable(mc.world.getBlockState(hit.getBlockPos()).getBlock()) && !input.sneak();
        try {
            if (sneak) player.networkHandler.sendPacket(new PlayerInputC2SPacket(new PlayerInput(
                input.forward(), input.backward(), input.left(), input.right(), input.jump(), true, input.sprint())));
            if (hit.isInsideBlock()) prediction.sent(mc.world, PlacementUtils.grimPlacePredicted(hit, state));
            else mc.interactionManager.sendSequencedPacket(mc.world, sequence -> new PlayerInteractBlockC2SPacket(hand, hit, sequence));
            sent = true;
            player.swingHand(hand);
        } finally {
            if (sneak) player.networkHandler.sendPacket(new PlayerInputC2SPacket(input));
        }
    }

}
