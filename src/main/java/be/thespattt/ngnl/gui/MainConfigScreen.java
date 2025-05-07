package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Main configuration screen
 */
public class MainConfigScreen extends ConfigScreen {

    // Item slots
    private static final int GENERAL_SLOT = 11;
    private static final int ROLES_SLOT = 13;
    private static final int MINI_GAMES_SLOT = 15;
    private static final int ARENA_SLOT = 29;
    private static final int FACTIONS_SLOT = 31;
    private static final int RELOAD_SLOT = 33;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player Player viewing the screen
     */
    public MainConfigScreen(NoGameNoLife plugin, Player player) {
        super(plugin, player, "§8No Game No Life - Configuration", 54);
        initializeIfNeeded();
    }

    @Override
    protected void initialize() {
        // Add category items
        ItemStack generalItem = new ItemBuilder(Material.COMPASS)
                .name("&e&lGeneral Settings")
                .lore(
                        "&7Configure general game settings",
                        "&7such as episode length, world borders,",
                        "&7and game phases."
                )
                .build();
        inventory.setItem(GENERAL_SLOT, generalItem);

        ItemStack rolesItem = new ItemBuilder(Material.PLAYER_HEAD)
                .name("&b&lRole Configuration")
                .lore(
                        "&7Configure role settings",
                        "&7including role abilities,",
                        "&7and role assignments."
                )
                .build();
        inventory.setItem(ROLES_SLOT, rolesItem);

        ItemStack miniGamesItem = new ItemBuilder(Material.DIAMOND_SWORD)
                .name("&c&lMini-Game Settings")
                .lore(
                        "&7Configure mini-game settings",
                        "&7including available games,",
                        "&7health penalties, and rewards."
                )
                .build();
        inventory.setItem(MINI_GAMES_SLOT, miniGamesItem);

        ItemStack arenaItem = new ItemBuilder(Material.END_CRYSTAL)
                .name("&d&lArena Phase Settings")
                .lore(
                        "&7Configure arena phase settings",
                        "&7including arena size, special items,",
                        "&7and arena events."
                )
                .build();
        inventory.setItem(ARENA_SLOT, arenaItem);

        ItemStack factionsItem = new ItemBuilder(Material.SHIELD)
                .name("&6&lFaction Settings")
                .lore(
                        "&7Configure faction settings",
                        "&7including faction bonuses",
                        "&7and special abilities."
                )
                .build();
        inventory.setItem(FACTIONS_SLOT, factionsItem);

        ItemStack reloadItem = new ItemBuilder(Material.REDSTONE)
                .name("&4&lReload Configuration")
                .lore(
                        "&7Reload the configuration from disk",
                        "&7Warning: This will discard any unsaved changes!"
                )
                .build();
        inventory.setItem(RELOAD_SLOT, reloadItem);

        // Add information item
        ItemStack infoItem = new ItemBuilder(Material.BOOK)
                .name("&a&lNo Game No Life UHC")
                .lore(
                        "&7Version: " + plugin.getDescription().getVersion(),
                        "&7By: TheSpatt",
                        "",
                        "&7Select a category to configure"
                )
                .build();
        inventory.setItem(4, infoItem);

        // Add navigation buttons (no back button needed on main screen)
        addNavigationButtons(false, true, false);

        // Fill empty slots
        fillEmptySlots();
    }

    @Override
    public void handleClick(int slot, boolean isLeftClick, boolean isRightClick, boolean isShiftClick) {
        switch (slot) {
            case GENERAL_SLOT:
                openScreen(ConfigGUIManager.ScreenType.GENERAL);
                break;
            case ROLES_SLOT:
                openScreen(ConfigGUIManager.ScreenType.ROLES);
                break;
            case MINI_GAMES_SLOT:
                openScreen(ConfigGUIManager.ScreenType.MINI_GAMES);
                break;
            case ARENA_SLOT:
                openScreen(ConfigGUIManager.ScreenType.ARENA);
                break;
            case FACTIONS_SLOT:
                openScreen(ConfigGUIManager.ScreenType.FACTIONS);
                break;
            case RELOAD_SLOT:
                // Reload configuration from disk
                plugin.getConfigManager().reloadConfig();
                plugin.getConfigGUIManager().closeScreen(player);
                break;
            case SAVE_SLOT:
                // Save configuration
                plugin.getConfigManager().saveConfig();
                plugin.getConfigGUIManager().closeScreen(player);
                break;
        }
    }

    @Override
    public void saveChanges() {
        // No direct changes to save on this screen
        plugin.getConfigManager().saveConfig();
    }
}