/*
 * Port of com.lambda.util.BlockUtils (lambda 1.21.11) — direct 1:1 translation.
 * Extension methods on BlockState, ItemStack, PlayerEntity, BlockPos.
 */
package com.hunterbuddy.lambda;

import net.minecraft.block.AbstractCauldronBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.CandleCakeBlock;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.block.CaveVinesBodyBlock;
import net.minecraft.block.CaveVinesHeadBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ChiseledBookshelfBlock;
import net.minecraft.block.CommandBlock;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.ComposterBlock;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.DragonEggBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.JigsawBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.LightBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.PistonExtensionBlock;
import net.minecraft.block.PumpkinBlock;
import net.minecraft.block.RedstoneOreBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.StructureBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.EightWayDirection;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class BlockUtils {
    public static final Set<Block> SIGNS = Set.of(
        Blocks.OAK_SIGN, Blocks.BIRCH_SIGN, Blocks.ACACIA_SIGN, Blocks.CHERRY_SIGN,
        Blocks.JUNGLE_SIGN, Blocks.DARK_OAK_SIGN, Blocks.MANGROVE_SIGN, Blocks.BAMBOO_SIGN,
        Blocks.CRIMSON_SIGN, Blocks.WARPED_SIGN, Blocks.SPRUCE_SIGN
    );

    public static final Set<Block> WALL_SIGNS = Set.of(
        Blocks.OAK_WALL_SIGN, Blocks.BIRCH_WALL_SIGN, Blocks.ACACIA_WALL_SIGN,
        Blocks.CHERRY_WALL_SIGN, Blocks.JUNGLE_WALL_SIGN, Blocks.DARK_OAK_WALL_SIGN,
        Blocks.MANGROVE_WALL_SIGN, Blocks.BAMBOO_WALL_SIGN, Blocks.CRIMSON_WALL_SIGN,
        Blocks.WARPED_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN
    );

    public static final Set<Block> HANGING_SIGNS = Set.of(
        Blocks.OAK_HANGING_SIGN, Blocks.BIRCH_HANGING_SIGN, Blocks.ACACIA_HANGING_SIGN,
        Blocks.CHERRY_HANGING_SIGN, Blocks.JUNGLE_HANGING_SIGN, Blocks.DARK_OAK_HANGING_SIGN,
        Blocks.MANGROVE_HANGING_SIGN, Blocks.BAMBOO_HANGING_SIGN, Blocks.CRIMSON_HANGING_SIGN,
        Blocks.WARPED_HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN
    );

    public static final Set<Block> HANGING_WALL_SIGNS = Set.of(
        Blocks.OAK_WALL_HANGING_SIGN, Blocks.BIRCH_WALL_HANGING_SIGN, Blocks.ACACIA_WALL_HANGING_SIGN,
        Blocks.CHERRY_WALL_HANGING_SIGN, Blocks.JUNGLE_WALL_HANGING_SIGN, Blocks.DARK_OAK_WALL_HANGING_SIGN,
        Blocks.MANGROVE_WALL_HANGING_SIGN, Blocks.BAMBOO_WALL_HANGING_SIGN, Blocks.CRIMSON_WALL_HANGING_SIGN,
        Blocks.WARPED_WALL_HANGING_SIGN, Blocks.SPRUCE_WALL_HANGING_SIGN
    );

    public static final Set<Block> ALL_SIGNS;
    static {
        Set<Block> all = new HashSet<>();
        all.addAll(SIGNS);
        all.addAll(WALL_SIGNS);
        all.addAll(HANGING_SIGNS);
        all.addAll(HANGING_WALL_SIGNS);
        ALL_SIGNS = Set.copyOf(all);
    }

    public static final Set<Block> POTTED_BLOCKS = Set.of(
        Blocks.POTTED_WARPED_FUNGUS, Blocks.POTTED_AZALEA_BUSH, Blocks.POTTED_CLOSED_EYEBLOSSOM,
        Blocks.POTTED_CACTUS, Blocks.POTTED_PINK_TULIP, Blocks.POTTED_FLOWERING_AZALEA_BUSH,
        Blocks.POTTED_RED_TULIP, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_DANDELION,
        Blocks.POTTED_SPRUCE_SAPLING, Blocks.POTTED_WHITE_TULIP, Blocks.POTTED_OAK_SAPLING,
        Blocks.POTTED_WITHER_ROSE, Blocks.POTTED_PALE_OAK_SAPLING, Blocks.POTTED_ACACIA_SAPLING,
        Blocks.POTTED_BIRCH_SAPLING, Blocks.POTTED_ALLIUM, Blocks.POTTED_CRIMSON_FUNGUS,
        Blocks.POTTED_CRIMSON_ROOTS, Blocks.POTTED_JUNGLE_SAPLING, Blocks.POTTED_DEAD_BUSH,
        Blocks.POTTED_TORCHFLOWER, Blocks.POTTED_BLUE_ORCHID, Blocks.POTTED_BROWN_MUSHROOM,
        Blocks.POTTED_BAMBOO, Blocks.POTTED_MANGROVE_PROPAGULE, Blocks.POTTED_CHERRY_SAPLING,
        Blocks.POTTED_AZURE_BLUET, Blocks.POTTED_DARK_OAK_SAPLING, Blocks.POTTED_RED_MUSHROOM,
        Blocks.POTTED_WARPED_ROOTS, Blocks.POTTED_OPEN_EYEBLOSSOM, Blocks.POTTED_ORANGE_TULIP,
        Blocks.POTTED_OXEYE_DAISY, Blocks.POTTED_POPPY, Blocks.POTTED_LILY_OF_THE_VALLEY,
        Blocks.POTTED_FERN
    );

    public static final Set<Class<?>> INTERACTION_BLOCKS = Set.of(
        AbstractCauldronBlock.class, AbstractFurnaceBlock.class, AbstractSignBlock.class,
        AnvilBlock.class, BarrelBlock.class, BeaconBlock.class, BedBlock.class,
        BeehiveBlock.class, BellBlock.class, BrewingStandBlock.class, ButtonBlock.class,
        CakeBlock.class, CampfireBlock.class, CandleBlock.class, CandleCakeBlock.class,
        CartographyTableBlock.class, CaveVinesBodyBlock.class, CaveVinesHeadBlock.class,
        ChestBlock.class, ChiseledBookshelfBlock.class, CommandBlock.class,
        ComparatorBlock.class, ComposterBlock.class, CrafterBlock.class,
        CraftingTableBlock.class, DaylightDetectorBlock.class, DecoratedPotBlock.class,
        DispenserBlock.class, DropperBlock.class, DoorBlock.class, DragonEggBlock.class,
        EnchantingTableBlock.class, EnderChestBlock.class, FenceBlock.class,
        FenceGateBlock.class, FlowerPotBlock.class, GrindstoneBlock.class, HopperBlock.class,
        JigsawBlock.class, JukeboxBlock.class, LecternBlock.class, LeverBlock.class,
        LightBlock.class, LoomBlock.class, NoteBlock.class, PistonExtensionBlock.class,
        PumpkinBlock.class, RedstoneOreBlock.class, RedstoneWireBlock.class,
        RepeaterBlock.class, RespawnAnchorBlock.class, ShulkerBoxBlock.class,
        SmithingTableBlock.class, StonecutterBlock.class, StructureBlock.class,
        SweetBerryBushBlock.class, TntBlock.class, TrapdoorBlock.class
    );

    public static final java.util.List<net.minecraft.fluid.Fluid> FLUIDS = java.util.List.of(
        Fluids.LAVA, Fluids.FLOWING_LAVA, Fluids.WATER, Fluids.FLOWING_WATER, Fluids.EMPTY
    );

    private BlockUtils() {}

    // ===== BlockState extensions =====

    public static boolean isEmpty(BlockState self) {
        return self.isAir() || matches(self, self.getFluidState().getBlockState(), java.util.Collections.emptySet());
    }

    public static boolean isNotEmpty(BlockState self) {
        return !isEmpty(self);
    }

    public static boolean hasFluid(BlockState self) {
        return !self.getFluidState().isEmpty();
    }

    public static BlockState emptyState(BlockState self) {
        return self.getFluidState().getBlockState();
    }

    public static boolean matches(BlockState self, BlockState state, Collection<Property<?>> ignoredProperties) {
        if (self.getBlock() != state.getBlock()) return false;
        for (Property<?> p : self.getProperties()) {
            if (ignoredProperties.contains(p)) continue;
            if (self.get(p) != state.get(p)) return false;
        }
        return true;
    }

    public static boolean matches(BlockState self, BlockState state) {
        return matches(self, state, java.util.Collections.emptySet());
    }

    public static boolean isSolidBlock(BlockState self, net.minecraft.world.WorldView world, BlockPos pos) {
        return self.isSolid();
    }

    // ===== Broken state checks =====

    public static boolean isBroken(BlockState oldState, BlockState newState) {
        return isNotEmpty(oldState) && matches(emptyState(oldState), newState, java.util.Collections.emptySet());
    }

    public static boolean isNotBroken(BlockState oldState, BlockState newState) {
        return !isBroken(oldState, newState);
    }

    // ===== Breakability =====

    public static float calcItemBlockBreakingDelta(net.minecraft.world.WorldView world, PlayerEntity player,
                                                    BlockPos pos, BlockState state, ItemStack stack) {
        float hardness = state.getHardness(world, pos);
        if (hardness == -1.0f) return 0.0f;
        boolean canHarvest = canHarvest(stack, state);
        int divisor = canHarvest ? 30 : 100;
        return getItemBlockBreakingSpeed(player, state, stack) / hardness / divisor;
    }

    public static boolean canHarvest(ItemStack stack, BlockState state) {
        return !state.isToolRequired() || stack.isSuitableFor(state);
    }

    /** Lambda: `item.getEnchantment(Enchantments.EFFICIENCY)` —
     *  iterates the ItemEnchantmentsComponent entries and finds the
     *  matching key. Returns 0 if absent. */
    public static int getEnchantmentLevel(ItemStack stack,
                                          net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key) {
        var component = stack.getEnchantments();
        for (var entry : component.getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(key)) return entry.getIntValue();
        }
        return 0;
    }

    public static float getItemBlockBreakingSpeed(PlayerEntity player, BlockState state, ItemStack stack) {
        float speedMultiplier = stack.getMiningSpeedMultiplier(state);
        if (speedMultiplier > 1.0f) {
            int eff = getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
            speedMultiplier += eff > 0 ? (eff * eff) + 1 : 0;
        }
        if (StatusEffectUtil.hasHaste(player)) {
            speedMultiplier *= 1.0f + (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2f;
        }
        var fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
        if (fatigue != null) {
            float fatigueMultiplier = switch (fatigue.getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                default -> 8.1E-4f;
            };
            speedMultiplier *= fatigueMultiplier;
        }
        speedMultiplier *= (float) player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER)) {
            var speed = player.getAttributeInstance(EntityAttributes.SUBMERGED_MINING_SPEED);
            if (speed != null) speedMultiplier *= (float) speed.getValue();
        }
        if (!player.isOnGround()) {
            speedMultiplier /= 5.0f;
        }
        return speedMultiplier;
    }

    public static boolean instantBreakable(net.minecraft.world.WorldView world, PlayerEntity player,
                                          BlockState blockState, BlockPos blockPos,
                                          float breakThreshold) {
        float ticksNeeded = 1.0f / (blockState.calcBlockBreakingDelta(player, (net.minecraft.world.BlockView) world, blockPos) / breakThreshold);
        return (ticksNeeded <= 1.0f && ticksNeeded != 0.0f) || player.isCreative();
    }

    // ===== Other extensions =====

    public static Item asItem(Block self) {
        return self.asItem();
    }

    public static Vec3d vecOf(BlockPos self, Direction direction) {
        return self.toCenterPos().add(Vec3d.of(direction.getVector()).multiply(0.5));
    }

    public static BlockPos offsetEightWay(BlockPos self, EightWayDirection eightWayDirection, int amount) {
        return self.add(eightWayDirection.getOffsetX() * amount, 0, eightWayDirection.getOffsetZ() * amount);
    }

    public static BlockPos toBlockPos(Vec3i self) {
        return new BlockPos(self);
    }

    // ===== SafeContext extensions (called from a SafeContext) =====

    public static BlockState blockStateFromWorld(net.minecraft.world.WorldView world, BlockPos pos) {
        return world.getBlockState(pos);
    }

    public static FluidState fluidStateFromWorld(net.minecraft.world.WorldView world, BlockPos pos) {
        return world.getFluidState(pos);
    }
}
