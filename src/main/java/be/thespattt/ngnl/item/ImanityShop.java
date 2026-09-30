package be.thespattt.ngnl.item;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.player.faction.FactionType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The shop of the Imanity faction: its members buy special items with emeralds or gold ingots.
 * Prices are random and drawn once per game. Opened with /shop.
 */
public class ImanityShop implements CommandExecutor, Listener {

    /** Items sold (special item id, slot). */
    private static final String[] SOLD_ITEMS = {"blood_bomb", "elf_runes", "imanity_crown", "ex_machina_core", "old_deus_fragment"};

    private final NoGameNoLife plugin;
    private final Map<String, Price> prices = new LinkedHashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public ImanityShop(NoGameNoLife plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Draw new random prices (called when a game starts).
     */
    public void rollPrices() {
        prices.clear();
        for (String itemId : SOLD_ITEMS) {
            boolean emerald = ThreadLocalRandom.current().nextBoolean();
            Material currency = emerald ? Material.EMERALD : Material.GOLD_INGOT;
            int amount = emerald ? ThreadLocalRandom.current().nextInt(3, 9) : ThreadLocalRandom.current().nextInt(6, 15);
            prices.put(itemId, new Price(currency, amount));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!plugin.getGameManager().isGameRunning() || !isImanity(player)) {
            MessageUtil.sendMessage(player, "&cSeule la faction Imanity a accès à la boutique pendant la partie.");
            return true;
        }
        if (prices.isEmpty()) {
            rollPrices();
        }
        openShop(player);
        return true;
    }

    /**
     * Check whether a player belongs to the Imanity faction.
     *
     * @param player Player to check
     * @return True for Imanity members
     */
    private boolean isImanity(Player player) {
        NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        return data != null && data.getFaction() == FactionType.IMANITY;
    }

    /**
     * Open the shop inventory.
     *
     * @param player Customer
     */
    private void openShop(Player player) {
        ShopHolder holder = new ShopHolder();
        Inventory inventory = Bukkit.createInventory(holder, 9, "Boutique d'Imanity");
        holder.inventory = inventory;

        int slot = 2;
        for (Map.Entry<String, Price> entry : prices.entrySet()) {
            ItemStack display = plugin.getItemManager().getSpecialItem(entry.getKey()).clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§6Prix : §f" + entry.getValue().amount + " " + entry.getValue().currencyName());
            lore.add("§eClique pour acheter");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inventory.setItem(slot, display);
            holder.slots.put(slot, entry.getKey());
            slot++;
        }
        player.openInventory(inventory);
    }

    /**
     * Handle a purchase click.
     *
     * @param event Click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String itemId = holder.slots.get(event.getRawSlot());
        if (itemId != null) {
            buy(player, itemId);
        }
    }

    /**
     * Take the payment and give the item.
     *
     * @param player Customer
     * @param itemId Identifier of the special item
     */
    private void buy(Player player, String itemId) {
        Price price = prices.get(itemId);
        if (price == null || !player.getInventory().containsAtLeast(new ItemStack(price.currency), price.amount)) {
            MessageUtil.sendMessage(player, "&cIl te faut " + (price != null ? price.amount + " " + price.currencyName() : "plus de ressources") + ".");
            return;
        }
        player.getInventory().removeItem(new ItemStack(price.currency, price.amount));
        plugin.getItemManager().giveSpecialItem(player, itemId);
    }

    /**
     * A price in emeralds or gold ingots.
     */
    private static final class Price {
        private final Material currency;
        private final int amount;

        private Price(Material currency, int amount) {
            this.currency = currency;
            this.amount = amount;
        }

        private String currencyName() {
            return currency == Material.EMERALD ? "émeraude(s)" : "lingot(s) d'or";
        }
    }

    /**
     * Holder identifying the shop inventory.
     */
    private static final class ShopHolder implements InventoryHolder {
        private final Map<Integer, String> slots = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
