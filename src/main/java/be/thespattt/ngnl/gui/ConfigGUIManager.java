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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manager class for the configuration GUI system
 */
public class ConfigGUIManager implements Listener {

    private final NoGameNoLife plugin;
    private static final Map<UUID, ConfigScreen> activeScreens = Collections.synchronizedMap(new HashMap<>());
    private static final Map<ScreenType, Inventory> screenInventories = new HashMap<>();

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

        screen.initializeIfNeeded();
        activeScreens.put(player.getUniqueId(), screen);
        screenInventories.put(screenType, screen.getInventory());

        // Open the inventory for the player
        player.openInventory(screen.getInventory());
    }

    public void clearActiveScreen(Player player) {
        synchronized (activeScreens) {
            if (activeScreens.containsKey(player.getUniqueId())) {
                Bukkit.getLogger().warning("Screen for player " + player.getName() + " was manually removed!");
                activeScreens.remove(player.getUniqueId());
            }
        }
    }

    public void closeScreen(Player player) {
        clearActiveScreen(player);
        player.closeInventory();
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

        ConfigScreen screen = activeScreens.get(playerId);

        if (screen == null) return;

        if (event.getView().getTopInventory().equals(screen.getInventory())) {
            event.setCancelled(true);

            if (event.getClickedInventory() != null && event.getClickedInventory().equals(screen.getInventory())) {
                screen.handleClick(event.getSlot(), event.isLeftClick(), event.isRightClick(), event.isShiftClick());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (activeScreens.containsKey(playerId)) {
            ConfigScreen screen = activeScreens.get(playerId);

            if (screen.needsSaving()) {
                screen.saveChanges();
                MessageUtil.sendMessage(player, "&aConfiguration has been saved!");

                plugin.getConfigManager().reloadConfig();
                MessageUtil.sendMessage(player, "&aConfiguration has been reloaded!");
            }
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