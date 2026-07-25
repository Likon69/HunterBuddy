/*
 * Port of com.lambda.interaction.construction.verify.TargetState
 * (lambda 1.21.11) — direct 1:1 translation to Java.
 *
 * Sealed family of state targets (Empty, Air, Solid, Support, State, Block, Stack).
 * Each subclass overrides matches/getStack/getState/isEmpty.
 *
 * Uses BlockUtils for isEmpty, emptyState, matches with properties,
 * isSolidBlock(world, pos). Uses ContainerHandler.findDisposable for
 * getStack on Solid/Support.
 */
package com.hunterbuddy.lambda;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Collection;

public abstract class TargetState implements StateMatcher {

    public static final TargetState Empty = new Empty();
    public static final TargetState Air = new Air();

    public static TargetState Solid(java.util.Collection<Block> replace) {
        return new Solid(replace);
    }

    public static TargetState Support(Direction direction) {
        return new Support(direction);
    }

    public static TargetState State(BlockState blockState) {
        return new State(blockState);
    }

    public static TargetState Block(Block block) {
        return new BlockType(block);
    }

    public static TargetState Stack(ItemStack itemStack) {
        return new StackType(itemStack);
    }

    public static final class Empty extends TargetState {
        @Override public String toString() { return "Empty"; }
        @Override public boolean matches(BlockState s, BlockPos p, java.util.Collection<Property<?>> i) {
            return BlockUtils.isEmpty(s);
        }
        @Override public ItemStack getStack(BlockPos p) { return ItemStack.EMPTY; }
        @Override public BlockState getState(BlockPos p) { return Blocks.AIR.getDefaultState(); }
        @Override public boolean isEmpty() { return true; }
    }

    public static final class Air extends TargetState {
        @Override public String toString() { return "Air"; }
        @Override public boolean matches(BlockState s, BlockPos p, java.util.Collection<Property<?>> i) {
            return s.isAir();
        }
        @Override public ItemStack getStack(BlockPos p) { return ItemStack.EMPTY; }
        @Override public BlockState getState(BlockPos p) { return Blocks.AIR.getDefaultState(); }
        @Override public boolean isEmpty() { return true; }
    }

    public static final class Solid extends TargetState {
        private final java.util.Collection<Block> replace;
        public Solid(java.util.Collection<Block> replace) { this.replace = replace; }
        @Override public String toString() { return "Solid"; }
        @Override public boolean matches(BlockState s, BlockPos p, java.util.Collection<Property<?>> i) {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            return !BlockUtils.isEmpty(s)
                && BlockUtils.isSolidBlock(s, mc != null ? mc.world : null, p)
                && !replace.contains(s.getBlock());
        }
        @Override public ItemStack getStack(BlockPos p) {
            // Lambda: findDisposable()?.stacks?.firstOrNull { ... } ?: ItemStack(Items.NETHERRACK)
            // inventoryConfig.disposables defaults to a comprehensive set including netherrack
            ItemStack fromContainer = ContainerHandler.findDisposable(null);
            return fromContainer != null ? fromContainer : new ItemStack(Items.NETHERRACK);
        }
        @Override public BlockState getState(BlockPos p) {
            ItemStack s = getStack(p);
            Block b = s.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
            return b != null ? b.getDefaultState() : Blocks.AIR.getDefaultState();
        }
        @Override public boolean isEmpty() { return false; }
    }

    public static final class Support extends TargetState {
        private final Direction direction;
        public Support(Direction direction) { this.direction = direction; }
        @Override public String toString() { return "Support for " + direction.asString(); }
        @Override public boolean matches(BlockState s, BlockPos p, java.util.Collection<Property<?>> i) {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            var world = mc != null ? mc.world : null;
            if (world == null) return false;
            return BlockUtils.isSolidBlock(world.getBlockState(p.offset(direction)), world, p.offset(direction))
                || BlockUtils.isSolidBlock(s, world, p);
        }
        @Override public ItemStack getStack(BlockPos p) {
            ItemStack fromContainer = ContainerHandler.findDisposable(null);
            return fromContainer != null ? fromContainer : new ItemStack(Items.NETHERRACK);
        }
        @Override public BlockState getState(BlockPos p) {
            ItemStack s = getStack(p);
            Block b = s.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
            return b != null ? b.getDefaultState() : Blocks.AIR.getDefaultState();
        }
        @Override public boolean isEmpty() { return false; }
    }

    public static final class State extends TargetState {
        private final BlockState blockState;
        public State(BlockState blockState) { this.blockState = blockState; }
        @Override public String toString() { return "State of " + blockState; }
        @Override public boolean matches(BlockState s, BlockPos p, java.util.Collection<Property<?>> i) {
            return BlockUtils.matches(s, blockState, i);
        }
        @Override public ItemStack getStack(BlockPos p) {
            return new ItemStack(blockState.getBlock());
        }
        @Override public BlockState getState(BlockPos p) { return blockState; }
        @Override public boolean isEmpty() { return blockState.isAir(); }
    }

    public static final class BlockType extends TargetState {
        private final Block block;
        public BlockType(Block block) { this.block = block; }
        @Override public String toString() { return "Block of " + block.getName().getString(); }
        @Override public boolean matches(BlockState s, BlockPos p, java.util.Collection<Property<?>> i) {
            return s.getBlock() == block;
        }
        @Override public ItemStack getStack(BlockPos p) { return new ItemStack(block); }
        @Override public BlockState getState(BlockPos p) { return block.getDefaultState(); }
        @Override public boolean isEmpty() { return block.getDefaultState().isAir(); }
    }

    public static final class StackType extends TargetState {
        private final ItemStack itemStack;
        public StackType(ItemStack itemStack) { this.itemStack = itemStack; }
        @Override public String toString() { return "Stack of " + itemStack.getItem().getName().getString(); }
        @Override public boolean matches(BlockState s, BlockPos p, java.util.Collection<Property<?>> i) {
            Block b = itemStack.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
            return s.getBlock() == b;
        }
        @Override public ItemStack getStack(BlockPos p) { return itemStack; }
        @Override public BlockState getState(BlockPos p) {
            Block b = itemStack.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
            return b != null ? b.getDefaultState() : Blocks.AIR.getDefaultState();
        }
        @Override public boolean isEmpty() { return false; }
    }
}
