package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.config.GameConfig;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manager class for the configuration GUI system
 */
public class ConfigGUIManager implements Listener {

    private final NoGameNoLife plugin;
    private final Map<UUID, ConfigScreen> activeScreens = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public ConfigGUIManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Open the main configuration screen for a player
     *
     * @param player Player to open the screen for
     */
    public void openMainScreen(Player player) {
        MainConfigScreen screen = new MainConfigScreen(plugin, player);
        activeScreens.put(player.getUniqueId(), screen);
        player.openInventory(screen.getInventory());
    }

    /**
     * Open a specific configuration screen for a player
     *
     * @param player Player to open the screen for
     * @param screenType Type of screen to open
     */
    public void openScreen(Player player, ScreenType screenType) {
        ConfigScreen screen;

        switch(screenType) {
            case MAIN:
                screen = new MainConfigScreen(plugin, player);
                break;
            case GENERAL:
                screen = new GeneralConfigScreen(plugin, player);
                break;
            case ROLES:
                screen = new RoleConfigScreen(plugin, player);
                break;
            case MINI_GAMES:
                screen = new MiniGameConfigScreen(plugin, player);
                break;
            case ARENA:
                screen = new ArenaConfigScreen(plugin, player);
                break;
            case FACTIONS:
                screen = new FactionConfigScreen(plugin, player);
                break;
            default:
                screen = new MainConfigScreen(plugin, player);
                break;
        }

        activeScreens.put(player.getUniqueId(), screen);
        player.openInventory(screen.getInventory());
    }

    /**
     * Handle inventory click events
     *
     * @param event The click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        if (!activeScreens.containsKey(playerId)) {
            return;
        }

        // Get the active screen
        ConfigScreen screen = activeScreens.get(playerId);

        // Check if they're clicking in any part of the inventory view that contains our GUI
        // This includes both the top inventory (our GUI) and the bottom inventory (player inventory)
        if (event.getView().getTopInventory().equals(screen.getInventory())) {
            // Cancel the event to prevent item movement for ANY click in this inventory view
            event.setCancelled(true);

            // Only process the click if it's specifically in our GUI inventory (top inventory)
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(screen.getInventory())) {
                screen.handleClick(event.getSlot(), event.isLeftClick(), event.isRightClick(), event.isShiftClick());
            }
        }
    }
    /**
     * Handle inventory close events
     *
     * @param event The close event
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (activeScreens.containsKey(playerId)) {
            ConfigScreen screen = activeScreens.get(playerId);

            // If the screen needs to save changes, do so
            if (screen.needsSaving()) {
                screen.saveChanges();
                MessageUtil.sendMessage(player, "&aConfiguration has been saved!");

                // Reload the configuration
                plugin.getConfigManager().reloadConfig();
                MessageUtil.sendMessage(player, "&aConfiguration has been reloaded!");
            }

            // Remove the screen from active screens
            activeScreens.remove(playerId);
        }
    }

    /**
     * Save all configurations and reload
     */
    public void saveAndReload() {
        plugin.getConfigManager().saveConfig();
        plugin.getConfigManager().reloadConfig();
    }

    /**
     * Enum for the different screen types
     */
    public enum ScreenType {
        MAIN,
        GENERAL,
        ROLES,
        MINI_GAMES,
        ARENA,
        FACTIONS
    }
}