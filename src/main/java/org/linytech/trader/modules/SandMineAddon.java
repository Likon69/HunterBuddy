package org.linytech.trader.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import baritone.api.BaritoneAPI;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.util.List;

public class SandMineAddon extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Ajout d'une case à cocher pour activer ou désactiver la commande "home"
    private final Setting<Boolean> useHomeCommand = sgGeneral.add(new BoolSetting.Builder()
        .name("home-command")
        .description("Execute the 'home' command when deactivating the module.")
        .defaultValue(false)  // Valeur par défaut: désactivé
        .build()
    );

    // Item filter setting to filter which items to mine
    private final Setting<List<Item>> itemFilter = sgGeneral.add(new ItemListSetting.Builder()
        .name("item-filter")
        .description("Only mine the items in this list.")
        .build()
    );

    public SandMineAddon(Category category) {
        super(category, "sand-mine-addon", "Mines only specified items.");
    }

    @Override
    public void onActivate() {
        System.out.println("[SandMineAddon] Module activated.");

    }

    // Gestionnaire d'événements pour capturer les ticks
    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Vérifier si l'inventaire est plein
        if (isInventoryFull()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
            // Vérifier si l'option "home-command" est activée

            if (useHomeCommand.get()) {

                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("home");

            }
            toggle(); // Désactiver le module
            return;
        }

        // Si l'inventaire n'est pas plein, continuer le minage
        List<Item> filters = itemFilter.get();
        if (filters.isEmpty()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("mine minecraft:sand");
        } else {
            // Boucle à travers les items dans le filtre et miner chaque item
            for (Item item : filters) {
                String itemName = Registries.ITEM.getId(item).toString(); // Obtenir le nom de l'item
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("mine " + itemName);
            }
        }
    }

    @Override
    public void onDeactivate() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
    }

    // Méthode pour vérifier si l'inventaire est plein
    private boolean isInventoryFull() {
        // Vérifier uniquement les emplacements 9 à 35 (inventaire principal, sans la hotbar)
        for (int i = 9; i < mc.player.getInventory().main.size(); i++) {
            if (mc.player.getInventory().main.get(i).isEmpty()) {
                return false; // Un emplacement est vide
            }
            if (mc.player.getInventory().main.get(i).getCount() < mc.player.getInventory().main.get(i).getMaxCount()) {
                return false; // Un emplacement n'est pas plein
            }
        }
        return true; // L'inventaire est plein
    }
}
