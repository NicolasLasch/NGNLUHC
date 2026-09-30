package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generic chest GUI that lets a player pick another player (displayed as a head).
 * Used by abilities that need a target (Aka Si Anse, Oracle Card, TP Stick, Royal Recall...).
 */
public class PlayerPicker implements Listener {

    /** Maximum number of heads a picker can show (6 rows). */
    private static final int MAX_HEADS = 54;

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public PlayerPicker(NoGameNoLife plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Open the picker for a viewer.
     *
     * @param viewer     Player choosing
     * @param title      Inventory title
     * @param candidates UUIDs that can be chosen
     * @param onPick     Callback called with the chosen UUID
     */
    public void open(Player viewer, String title, List<UUID> candidates, Consumer<UUID> onPick) {
        if (candidates.isEmpty()) {
            viewer.sendMessage("§cAucun joueur disponible.");
            return;
        }

        PickerHolder holder = new PickerHolder(onPick);
        Inventory inventory = Bukkit.createInventory(holder, inventorySize(candidates.size()), title);
        holder.inventory = inventory;

        int slot = 0;
        for (UUID candidate : candidates.subList(0, Math.min(MAX_HEADS, candidates.size()))) {
            inventory.setItem(slot, buildHead(candidate));
            holder.slots.put(slot, candidate);
            slot++;
        }
        viewer.openInventory(inventory);
    }

    /**
     * Compute the smallest chest size holding the given number of heads.
     *
     * @param count Number of heads
     * @return Inventory size (multiple of 9)
     */
    private int inventorySize(int count) {
        int rows = (int) Math.ceil(Math.min(count, MAX_HEADS) / 9.0);
        return Math.max(1, rows) * 9;
    }

    /**
     * Build the head item representing a player.
     *
     * @param playerId UUID of the player
     * @return Head item
     */
    private ItemStack buildHead(UUID playerId) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            meta.setDisplayName("§e" + (target.getName() != null ? target.getName() : playerId.toString()));
            meta.setLore(List.of("§7Clique pour choisir"));
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Handle a click in a picker inventory.
     *
     * @param event Click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PickerHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }

        UUID chosen = holder.slots.get(event.getRawSlot());
        if (chosen == null) {
            return;
        }
        viewer.closeInventory();
        holder.onPick.accept(chosen);
    }

    /**
     * Inventory holder remembering which head maps to which player.
     */
    private static final class PickerHolder implements InventoryHolder {
        private final Consumer<UUID> onPick;
        private final Map<Integer, UUID> slots = new HashMap<>();
        private Inventory inventory;

        private PickerHolder(Consumer<UUID> onPick) {
            this.onPick = onPick;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * Convenience: candidates = every alive player except the viewer and optional exclusions.
     *
     * @param viewer     Player choosing
     * @param exclusions UUIDs to leave out
     * @return List of candidate UUIDs
     */
    public List<UUID> aliveCandidates(Player viewer, UUID... exclusions) {
        List<UUID> result = new ArrayList<>();
        for (UUID id : plugin.getGameManager().getGame().getAlivePlayers()) {
            boolean excluded = id.equals(viewer.getUniqueId());
            for (UUID exclusion : exclusions) {
                excluded = excluded || id.equals(exclusion);
            }
            if (!excluded) {
                result.add(id);
            }
        }
        return result;
    }
}
