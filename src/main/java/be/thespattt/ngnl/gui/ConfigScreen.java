package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.config.GameConfig;
import be.thespattt.ngnl.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Abstract base class for configuration screens
 */
public abstract class ConfigScreen {

    protected final NoGameNoLife plugin;
    protected final Player player;
    protected final Inventory inventory;
    protected boolean changed = false;

    // Navigation item slots
    protected static final int BACK_SLOT = 45;
    protected static final int SAVE_SLOT = 49;
    protected static final int NEXT_SLOT = 53;

    // Common items
    protected static final ItemStack FILLER = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
            .name(" ")
            .build();

    protected static final ItemStack BACK_BUTTON = new ItemBuilder(Material.ARROW)
            .name("&c&lBack")
            .lore("&7Return to the previous screen")
            .build();

    protected static final ItemStack SAVE_BUTTON = new ItemBuilder(Material.EMERALD)
            .name("&a&lSave Changes")
            .lore("&7Save all changes to the configuration")
            .build();

    protected static final ItemStack NEXT_BUTTON = new ItemBuilder(Material.ARROW)
            .name("&a&lNext Page")
            .lore("&7Go to the next page")
            .build();

    protected static final ItemStack ENABLED = new ItemBuilder(Material.LIME_DYE)
            .name("&a&lEnabled")
            .lore("&7Click to disable")
            .build();

    protected static final ItemStack DISABLED = new ItemBuilder(Material.RED_DYE)
            .name("&c&lDisabled")
            .lore("&7Click to enable")
            .build();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player Player viewing the screen
     * @param title Title of the inventory
     * @param size Size of the inventory (must be a multiple of 9)
     */
    public ConfigScreen(NoGameNoLife plugin, Player player, String title, int size) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = plugin.getServer().createInventory(null, size, title);

        initialize();
    }

    /**
     * Get the game configuration
     *
     * @return Game configuration
     */
    protected GameConfig getConfig() {
        return plugin.getConfigManager().getGameConfig();
    }

    /**
     * Initialize the screen with items
     */
    protected abstract void initialize();

    /**
     * Handle a click on the screen
     *
     * @param slot Slot that was clicked
     * @param isLeftClick Whether it was a left click
     * @param isRightClick Whether it was a right click
     * @param isShiftClick Whether shift was held
     */
    public abstract void handleClick(int slot, boolean isLeftClick, boolean isRightClick, boolean isShiftClick);

    /**
     * Check if the screen needs saving
     *
     * @return True if changes need to be saved
     */
    public boolean needsSaving() {
        return changed;
    }

    /**
     * Save changes to the configuration
     */
    public abstract void saveChanges();

    /**
     * Get the inventory for this screen
     *
     * @return The inventory
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Mark the configuration as changed
     */
    protected void markChanged() {
        changed = true;
    }

    /**
     * Fill empty slots with the filler item
     */
    protected void fillEmptySlots() {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, FILLER);
            }
        }
    }

    /**
     * Add navigation buttons to the inventory
     *
     * @param hasBack Whether to add a back button
     * @param hasSave Whether to add a save button
     * @param hasNext Whether to add a next button
     */
    protected void addNavigationButtons(boolean hasBack, boolean hasSave, boolean hasNext) {
        if (hasBack) {
            inventory.setItem(BACK_SLOT, BACK_BUTTON);
        }

        if (hasSave) {
            inventory.setItem(SAVE_SLOT, SAVE_BUTTON);
        }

        if (hasNext) {
            inventory.setItem(NEXT_SLOT, NEXT_BUTTON);
        }
    }

    /**
     * Open a new screen for the player
     *
     * @param screenType The type of screen to open
     */
    protected void openScreen(ConfigGUIManager.ScreenType screenType) {
        plugin.getConfigGUIManager().openScreen(player, screenType);
    }

    /**
     * Get an enabled/disabled item based on a boolean value
     *
     * @param enabled Whether the feature is enabled
     * @return The appropriate item stack
     */
    protected ItemStack getToggleItem(boolean enabled) {
        return enabled ? ENABLED : DISABLED;
    }
}