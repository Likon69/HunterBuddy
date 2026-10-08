package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.sorter.ShulkerSorter.ContainerType;
import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Destination;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Pos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SorterSetup {
    public enum Role { INPUT, RESERVE, STATION, ENDER, DESTINATION }

    public record RoleChoice(Role role, String category) {
        @Override
        public String toString() {
            return switch (role) {
                case INPUT -> "Input";
                case RESERVE -> "Reserve";
                case STATION -> "Station";
                case ENDER -> "Ender chest";
                case DESTINATION -> category;
            };
        }
    }

    private enum TagState { IDLE, OPEN, READ, CLOSE }

    private final ShulkerSorter owner;
    private TagState tagState = TagState.IDLE;
    private BlockPos tagPos;
    private ClientWorld tagWorld;
    private SorterProfile tagProfile;
    private GenericContainerScreenHandler tagHandler;
    private int ticks;
    private int attempts;
    private int receivedSyncId = -1;
    private RoleChoice inferredRole;

    SorterSetup(ShulkerSorter owner) {
        this.owner = owner;
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public boolean isInspecting() {
        return tagState != TagState.IDLE;
    }

    public BlockPos lookedAt() {
        if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) {
            owner.error("Look at a chest, barrel or station support first.");
            return null;
        }
        return hit.getBlockPos();
    }

    BlockPos registeredPosition(SorterProfile profile, BlockPos pos) {
        if (profile.whatIs(SorterStore.pos(pos)) != null) {
            return pos;
        }
        BlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.offset(ChestBlock.getFacing(state));
            if (profile.whatIs(SorterStore.pos(other)) != null) {
                return other;
            }
        }
        return pos;
    }

    private boolean editable() {
        if (mc.world == null || mc.player == null) {
            owner.error("Join a world before configuring the sorter.");
            return false;
        }
        if (owner.isActive() || isInspecting()) {
            owner.warning("Stop the sorter and wait for tagging to finish before changing its setup.");
            return false;
        }
        return true;
    }

    static String blockedChestReason(BlockPos pos) {
        if (mc.world == null) {
            return null;
        }
        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }
        BlockPos blocked = null;
        if (ChestBlock.isChestBlocked(mc.world, pos)) {
            blocked = pos;
        } else if (state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.offset(ChestBlock.getFacing(state));
            if (mc.world.getBlockState(other).isOf(state.getBlock()) && ChestBlock.isChestBlocked(mc.world, other)) {
                blocked = other;
            }
        }
        return blocked == null ? null : "Chest at " + SorterStore.pos(blocked) + " cannot open: clear the block above it.";
    }

    private boolean valid(BlockPos pos, RoleChoice choice) {
        if (choice.role() == Role.ENDER) {
            if (!(mc.world.getBlockState(pos).getBlock() instanceof EnderChestBlock)) {
                owner.error("That is not an ender chest at %s.", pos.toShortString());
                return false;
            }
            return true;
        }
        if (choice.role() == Role.STATION) {
            if (!Purifier.validStation(pos)) {
                owner.error("Station needs a solid support, free space above, room for the lid and an accessible side at %s.", pos.toShortString());
                return false;
            }
            return true;
        }
        BlockEntity block = mc.world.getBlockEntity(pos);
        if (!(block instanceof ChestBlockEntity) && !(block instanceof BarrelBlockEntity)) {
            owner.error("That is not a chest or barrel at %s.", pos.toShortString());
            return false;
        }
        String blocked = blockedChestReason(pos);
        if (blocked != null) {
            owner.error("%s", blocked);
            return false;
        }
        if (choice.role() == Role.INPUT && block instanceof TrappedChestBlockEntity) {
            owner.error("Trapped chests cannot be inputs at %s.", pos.toShortString());
            return false;
        }
        if (choice.role() == Role.DESTINATION && !SorterStore.get().rules.isKnown(choice.category())) {
            owner.error("Unknown category '%s'.", choice.category());
            return false;
        }
        return true;
    }

    public boolean assign(BlockPos pos, RoleChoice choice, boolean replace) {
        if (pos == null || !editable()) {
            return false;
        }
        SorterProfile profile = SorterStore.get();
        pos = registeredPosition(profile, pos);
        if (!valid(pos, choice)) {
            return false;
        }
        Pos at = SorterStore.pos(pos);
        String used = profile.whatIs(at);
        if (used != null && !replace && !(choice.role() == Role.STATION && at.equals(profile.unpackStation))) {
            owner.warning("Already %s at %s; category: %s.", used, at, categoryAt(profile, at));
            return false;
        }
        apply(profile, at, choice);
        owner.info("%s; category: %s; position: %s.", choice, choice.category() == null ? "none" : choice.category(), at);
        return true;
    }

    private void apply(SorterProfile profile, Pos at, RoleChoice choice) {
        removeRole(profile, at);
        switch (choice.role()) {
            case INPUT -> profile.inputs.add(at);
            case RESERVE -> profile.reserves.add(at);
            case STATION -> profile.unpackStation = at;
            case ENDER -> profile.enderChest = at;
            case DESTINATION -> profile.getOrCreate(choice.category()).chests.add(at);
        }
        SorterStore.save();
        owner.refreshSnapshot();
    }

    private boolean removeRole(SorterProfile profile, Pos at) {
        boolean removed = profile.inputs.remove(at);
        removed |= profile.reserves.remove(at);
        for (Destination destination : profile.destinations) {
            removed |= destination.chests.remove(at);
            destination.full.remove(at);
        }
        profile.destinations.removeIf(destination -> destination.chests.isEmpty());
        if (at.equals(profile.unpackStation)) {
            profile.unpackStation = null;
            removed = true;
        }
        if (at.equals(profile.enderChest)) {
            profile.enderChest = null;
            removed = true;
        }
        return removed;
    }

    public void remove(BlockPos pos) {
        if (pos == null || !editable()) {
            return;
        }
        SorterProfile profile = SorterStore.get();
        Pos at = SorterStore.pos(registeredPosition(profile, pos));
        boolean removed = removeRole(profile, at);
        SorterStore.save();
        owner.info(removed ? "Removed %s." : "%s was not used by the sorter.", at);
    }

    public void resetFull() {
        if (editable()) {
            SorterStore.get().destinations.forEach(destination -> destination.full.clear());
            SorterStore.save();
            owner.info("All destination chests marked as not full.");
        }
    }

    public void reload() {
        if (editable()) {
            SorterStore.reload();
            owner.info("Sorter profile reloaded.");
        }
    }

    Destination assignReserve(String category, Pos reserve) {
        SorterProfile profile = SorterStore.get();
        if (!profile.reserves.contains(reserve) || !profile.rules.isKnown(category)) {
            return null;
        }
        RoleChoice choice = new RoleChoice(Role.DESTINATION, category);
        apply(profile, reserve, choice);
        owner.info("Reserve assigned to %s at %s.", category, reserve);
        return profile.getOrCreate(category);
    }

    private String categoryAt(SorterProfile profile, Pos at) {
        for (Destination destination : profile.destinations) {
            if (destination.chests.contains(at)) {
                return destination.category;
            }
        }
        return "none";
    }

    public void tag() {
        if (!editable()) {
            return;
        }
        if (mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            owner.warning("Close the screen and empty the cursor before tagging.");
            return;
        }
        BlockPos pos = lookedAt();
        if (pos == null) {
            return;
        }
        SorterProfile profile = SorterStore.get();
        pos = registeredPosition(profile, pos);
        String blocked = blockedChestReason(pos);
        if (blocked != null) {
            owner.error("%s", blocked);
            return;
        }
        Pos at = SorterStore.pos(pos);
        String used = profile.whatIs(at);
        if (used != null) {
            owner.info("Already %s; category: %s; position: %s.", used, categoryAt(profile, at), at);
            return;
        }
        if (mc.world.getBlockState(pos).getBlock() instanceof EnderChestBlock) {
            assign(pos, new RoleChoice(Role.ENDER, null), false);
            return;
        }
        BlockEntity block = mc.world.getBlockEntity(pos);
        if (!(block instanceof ChestBlockEntity) && !(block instanceof BarrelBlockEntity)) {
            if (block instanceof Inventory || mc.world.getBlockState(pos).getBlock() instanceof EnderChestBlock
                || !mc.world.getBlockState(pos).isSolidBlock(mc.world, pos)) {
                owner.error("Tagging supports chests, barrels or a non-container station support at %s.", at);
                return;
            }
            assign(pos, new RoleChoice(Role.STATION, null), false);
            return;
        }
        if (!owner.inReach(pos)) {
            owner.warning("Container out of reach at %s.", at);
            return;
        }
        tagPos = pos.toImmutable();
        tagWorld = mc.world;
        tagProfile = profile;
        tagHandler = null;
        receivedSyncId = -1;
        attempts = 0;
        inferredRole = null;
        enter(TagState.OPEN);
        owner.info("Inspecting container at %s; role and category pending.", at);
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (!owner.isActive() && mc.currentScreen == null && event.action == KeyAction.Release
            && owner.tagKey().matches(event.input)) {
            tag();
        }
    }

    @EventHandler
    private void onMouse(MouseClickEvent event) {
        if (!owner.isActive() && mc.currentScreen == null && event.action == KeyAction.Release
            && owner.tagKey().matches(event.input)) {
            tag();
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (isInspecting() && mc.player != null
            && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler
            && event.packet.syncId() == handler.syncId) {
            receivedSyncId = handler.syncId;
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isInspecting()) {
            abort("Disconnected before the role could be assigned");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!owner.isActive()) {
            owner.refreshSnapshot();
        }
        if (!isInspecting()) {
            return;
        }
        if (mc.player == null || mc.world != tagWorld || SorterStore.get() != tagProfile) {
            abort("World or profile changed during inspection");
            return;
        }
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            abort("Cursor changed; empty it manually before closing the screen");
            return;
        }
        ticks++;
        switch (tagState) {
            case OPEN -> {
                if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
                    tagHandler = handler;
                    enter(TagState.READ);
                } else if (mc.currentScreen != null) {
                    abort("Another screen opened during inspection");
                } else if (ticks == 1 || (ticks - 1) % owner.openTimeout() == 0) {
                    if (attempts == 3) {
                        String blocked = blockedChestReason(tagPos);
                        abort(blocked == null ? "Container did not open after three attempts" : blocked);
                    } else if (mc.player.isSneaking() || !owner.inReach(tagPos)) {
                        abort("Release sneak and stay within reach while tagging");
                    } else if (!(mc.world.getBlockEntity(tagPos) instanceof ChestBlockEntity)
                        && !(mc.world.getBlockEntity(tagPos) instanceof BarrelBlockEntity)) {
                        abort("Target is no longer a chest or barrel");
                    } else {
                        attempts++;
                        if (!owner.interact(tagPos, ContainerType.STORAGE)) {
                            abort("Target changed or moved out of reach during inspection");
                        }
                    }
                }
            }
            case READ -> {
                if (mc.player.currentScreenHandler != tagHandler) {
                    abort("Container closed before its contents were read");
                } else if (ticks > owner.openTimeout() + owner.actionDelay()) {
                    abort("Container contents did not arrive");
                } else if (receivedSyncId == tagHandler.syncId && ticks > owner.actionDelay()) {
                    inferredRole = infer(tagHandler, tagProfile.rules);
                    mc.player.closeHandledScreen();
                    enter(TagState.CLOSE);
                }
            }
            case CLOSE -> {
                if (mc.player.currentScreenHandler == mc.player.playerScreenHandler && mc.currentScreen == null) {
                    BlockPos pos = tagPos;
                    RoleChoice choice = inferredRole;
                    clearInspection();
                    assign(pos, choice, false);
                } else if (ticks > owner.actionDelay() + 40) {
                    abort("Container did not close");
                }
            }
            default -> { }
        }
    }

    private RoleChoice infer(GenericContainerScreenHandler handler, CategoryRules rules) {
        Set<String> categories = new LinkedHashSet<>();
        boolean occupied = false;
        for (int i = 0; i < handler.getRows() * 9; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) {
                continue;
            }
            occupied = true;
            if (ShulkerSorter.isShulker(stack)) {
                ShulkerClassifier.Result result = SorterShulkers.classify(stack, rules, owner.purity.get());
                if (result.empty()) {
                    categories.add(CategoryRules.SHULKERS);
                } else {
                    categories.addAll(result.slotsByCategory().keySet());
                }
            } else {
                categories.add(rules.categoryOf(SorterShulkers.itemId(stack)));
            }
        }
        if (!occupied) {
            return new RoleChoice(Role.RESERVE, null);
        }
        return categories.size() == 1 ? new RoleChoice(Role.DESTINATION, categories.iterator().next())
            : new RoleChoice(Role.INPUT, null);
    }

    private void enter(TagState state) {
        tagState = state;
        ticks = 0;
    }

    private void clearInspection() {
        tagState = TagState.IDLE;
        tagWorld = null;
        tagProfile = null;
        tagHandler = null;
        tagPos = null;
        inferredRole = null;
    }

    private void abort(String reason) {
        owner.warning("%s at %s; no role assigned.", reason, tagPos.toShortString());
        if (mc.player != null && mc.world == tagWorld && tagHandler != null
            && mc.player.currentScreenHandler == tagHandler && tagHandler.getCursorStack().isEmpty()) {
            mc.player.closeHandledScreen();
        }
        clearInspection();
    }

    WWidget widget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WTable table = list.add(theme.table()).expandX().widget();
        fillTable(theme, table);
        return list;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        table.clear();
        if (mc.world == null || mc.player == null) {
            table.add(theme.label("Join a world to configure the sorter."));
            return;
        }
        SorterProfile profile = SorterStore.get();
        Map<Pos, RoleChoice> roles = new LinkedHashMap<>();
        for (Pos pos : profile.inputs) {
            roles.put(pos, new RoleChoice(Role.INPUT, null));
        }
        for (Pos pos : profile.reserves) {
            roles.put(pos, new RoleChoice(Role.RESERVE, null));
        }
        for (Destination destination : profile.destinations) {
            for (Pos pos : destination.chests) {
                roles.put(pos, new RoleChoice(Role.DESTINATION, destination.category));
            }
        }
        if (profile.unpackStation != null) {
            roles.put(profile.unpackStation, new RoleChoice(Role.STATION, null));
        }
        if (profile.enderChest != null) {
            roles.put(profile.enderChest, new RoleChoice(Role.ENDER, null));
        }
        List<RoleChoice> options = new ArrayList<>();
        options.add(new RoleChoice(Role.INPUT, null));
        options.add(new RoleChoice(Role.RESERVE, null));
        options.add(new RoleChoice(Role.STATION, null));
        options.add(new RoleChoice(Role.ENDER, null));
        for (String category : profile.rules.names()) {
            options.add(new RoleChoice(Role.DESTINATION, category));
        }
        for (Map.Entry<Pos, RoleChoice> entry : roles.entrySet()) {
            Pos pos = entry.getKey();
            RoleChoice role = entry.getValue();
            boolean full = profile.destinations.stream().anyMatch(destination -> destination.full.contains(pos));
            String label = role.role() == Role.DESTINATION ? "Destination " + role.category() : role.toString();
            String capacity = role.role() == Role.DESTINATION ? (full ? "full" : "not marked full") : "—";
            table.add(theme.label(label + " | " + pos + " | " + capacity)).expandX();
            WDropdown<RoleChoice> dropdown = table.add(theme.dropdown(options.toArray(RoleChoice[]::new), role)).widget();
            dropdown.action = () -> widgetAction(theme, table, profile,
                () -> assign(SorterStore.blockPos(pos), dropdown.get(), true));
            WButton remove = table.add(theme.button("Remove")).widget();
            remove.action = () -> widgetAction(theme, table, profile, () -> remove(SorterStore.blockPos(pos)));
            table.row();
        }
        if (roles.isEmpty()) {
            table.add(theme.label("No registered blocks. Use tag-key while looking at a block."));
            table.row();
        }
        WButton reset = table.add(theme.button("Reset full")).widget();
        reset.action = () -> widgetAction(theme, table, profile, this::resetFull);
        WButton reload = table.add(theme.button("Reload profile")).widget();
        reload.action = () -> widgetAction(theme, table, profile, this::reload);
        table.row();
        table.add(theme.horizontalSeparator("Withdraw")).expandX();
        table.row();
        String[] categories = profile.rules.names().stream().filter(name -> !CategoryRules.MIXED.equals(name)).toArray(String[]::new);
        WDropdown<String> category = table.add(theme.dropdown(categories, categories[0])).widget();
        WIntEdit quantity = table.add(theme.intEdit(1, 1, 27, 1, 27)).widget();
        String[] destinations = profile.enderChest == null ? new String[] { "Inventory" } : new String[] { "Inventory", "Ender chest" };
        WDropdown<String> destination = table.add(theme.dropdown(destinations, destinations[0])).widget();
        table.row();
        table.add(theme.label("Full shulkers only"));
        WCheckbox fullOnly = table.add(theme.checkbox(true)).widget();
        WButton withdraw = table.add(theme.button("Withdraw")).widget();
        withdraw.action = () -> {
            if (mc.world == null || SorterStore.get() != profile) {
                owner.warning("Profile changed; reopen the withdrawal controls.");
                fillTable(theme, table);
                return;
            }
            owner.withdraw(category.get(), quantity.get(), destination.get().equals("Ender chest"), fullOnly.checked);
        };
    }

    private void widgetAction(GuiTheme theme, WTable table, SorterProfile displayed, Runnable action) {
        if (mc.world != null && SorterStore.get() == displayed) {
            action.run();
        } else {
            owner.warning("Profile changed; review the refreshed list before editing.");
        }
        fillTable(theme, table);
    }
}
