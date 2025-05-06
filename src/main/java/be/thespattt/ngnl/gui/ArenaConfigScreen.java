package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.config.GameConfig;
import be.thespattt.ngnl.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Arena configuration screen
 */
public class ArenaConfigScreen extends ConfigScreen {

    // Item slots
    private static final int ARENA_SIZE_SLOT = 10;
    private static final int ARENA_SHRINK_SLOT = 12;
    private static final int SHRINK_TIME_SLOT = 14;
    private static final int FINAL_SIZE_SLOT = 16;
    private static final int SPECIAL_ITEMS_SLOT = 28;
    private static final int GRACE_PERIOD_SLOT = 30;
    private static final int ABILITY_COOLDOWN_MULTIPLIER_SLOT = 32;
    private static final int ARENA_EVENTS_SLOT = 34;

    // Config values
    private int arenaSize;
    private boolean arenaShrink;
    private int arenaShrinkTime;
    private int arenaFinalSize;
    private boolean specialItems;
    private int arenaGracePeriod;
    private double abilityCooldownMultiplier;
    private boolean arenaEvents;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player Player viewing the screen
     */
    public ArenaConfigScreen(NoGameNoLife plugin, Player player) {
        super(plugin, player, "§8Arena Configuration", 54);
    }

    @Override
    protected void initialize() {
        // Load current values
        GameConfig config = getConfig();
        arenaSize = config.getArenaSize();
        arenaShrink = config.isArenaShrinking();
        arenaShrinkTime = config.getArenaShrinkTime();
        arenaFinalSize = config.getArenaFinalSize();
        specialItems = config.areSpecialItemsEnabled();
        arenaGracePeriod = config.getArenaGracePeriod();
        abilityCooldownMultiplier = config.getAbilityCooldownMultiplier();
        arenaEvents = config.areArenaEventsEnabled();

        // Create and add items
        updateItems();

        // Add information item
        ItemStack infoItem = new ItemBuilder(Material.END_CRYSTAL)
                .name("&d&lArena Phase Configuration")
                .lore(
                        "&7Configure settings for the arena phase",
                        "&7that begins after qualification phase.",
                        "",
                        "&eLeft-click &7to increase values",
                        "&eRight-click &7to decrease values",
                        "&eClick &7to toggle options"
                )
                .build();
        inventory.setItem(4, infoItem);

        // Add navigation buttons
        addNavigationButtons(true, true, false);

        // Fill empty slots
        fillEmptySlots();
    }

    /**
     * Update the items in the inventory based on current values
     */
    private void updateItems() {
        ItemStack arenaSizeItem = new ItemBuilder(Material.BARRIER)
                .name("&e&lArena Size")
                .lore(
                        "&7Current: &f" + arenaSize + " blocks",
                        "&7The initial size of the arena",
                        "&7when the arena phase begins.",
                        "",
                        "&eLeft-click &7to increase by 50 (max 500)",
                        "&eRight-click &7to decrease by 50 (min 100)"
                )
                .build();
        inventory.setItem(ARENA_SIZE_SLOT, arenaSizeItem);

        ItemStack arenaShrinkItem = new ItemBuilder(Material.COMPASS)
                .name("&e&lArena Shrinking")
                .lore(
                        "&7Current: " + (arenaShrink ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, the arena border will",
                        "&7shrink over time during the arena phase.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(ARENA_SHRINK_SLOT, getToggleItem(arenaShrink));

        ItemStack shrinkTimeItem = new ItemBuilder(Material.CLOCK)
                .name("&e&lShrink Time")
                .lore(
                        "&7Current: &f" + arenaShrinkTime + " minutes",
                        "&7The time it takes for the arena border",
                        "&7to shrink to its final size.",
                        "",
                        "&eLeft-click &7to increase by 5 (max 60)",
                        "&eRight-click &7to decrease by 5 (min 5)"
                )
                .build();
        inventory.setItem(SHRINK_TIME_SLOT, shrinkTimeItem);

        ItemStack finalSizeItem = new ItemBuilder(Material.RED_STAINED_GLASS)
                .name("&e&lFinal Arena Size")
                .lore(
                        "&7Current: &f" + arenaFinalSize + " blocks",
                        "&7The final size of the arena after",
                        "&7the border has finished shrinking.",
                        "",
                        "&eLeft-click &7to increase by 10 (max 200)",
                        "&eRight-click &7to decrease by 10 (min 50)"
                )
                .build();
        inventory.setItem(FINAL_SIZE_SLOT, finalSizeItem);

        ItemStack specialItemsItem = new ItemBuilder(Material.DIAMOND)
                .name("&e&lSpecial Items")
                .lore(
                        "&7Current: " + (specialItems ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, special items will spawn",
                        "&7throughout the arena during the phase.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(SPECIAL_ITEMS_SLOT, getToggleItem(specialItems));

        ItemStack gracePeriodItem = new ItemBuilder(Material.SHIELD)
                .name("&e&lGrace Period")
                .lore(
                        "&7Current: &f" + arenaGracePeriod + " seconds",
                        "&7The period of invulnerability at",
                        "&7the start of the arena phase.",
                        "",
                        "&eLeft-click &7to increase by 10 (max 180)",
                        "&eRight-click &7to decrease by 10 (min 0)"
                )
                .build();
        inventory.setItem(GRACE_PERIOD_SLOT, gracePeriodItem);

        ItemStack cooldownItem = new ItemBuilder(Material.REDSTONE_LAMP)
                .name("&e&lAbility Cooldown Multiplier")
                .lore(
                        "&7Current: &fx" + abilityCooldownMultiplier,
                        "&7Multiplier for all ability cooldowns",
                        "&7during the arena phase. Lower values",
                        "&7mean abilities can be used more often.",
                        "",
                        "&eLeft-click &7to increase by 0.1 (max 2.0)",
                        "&eRight-click &7to decrease by 0.1 (min 0.5)"
                )
                .build();
        inventory.setItem(ABILITY_COOLDOWN_MULTIPLIER_SLOT, cooldownItem);

        ItemStack eventsItem = new ItemBuilder(Material.LIGHTNING_ROD)
                .name("&e&lArena Events")
                .lore(
                        "&7Current: " + (arenaEvents ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, random events will occur",
                        "&7during the arena phase, such as loot",
                        "&7drops, lightning strikes, and more.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(ARENA_EVENTS_SLOT, getToggleItem(arenaEvents));
    }

    @Override
    public void handleClick(int slot, boolean isLeftClick, boolean isRightClick, boolean isShiftClick) {
        boolean valueChanged = true;

        switch (slot) {
            case ARENA_SIZE_SLOT:
                if (isLeftClick && arenaSize < 500) {
                    arenaSize += 50;
                } else if (isRightClick && arenaSize > 100) {
                    arenaSize -= 50;
                } else {
                    valueChanged = false;
                }
                break;

            case ARENA_SHRINK_SLOT:
                arenaShrink = !arenaShrink;
                break;

            case SHRINK_TIME_SLOT:
                if (isLeftClick && arenaShrinkTime < 60) {
                    arenaShrinkTime += 5;
                } else if (isRightClick && arenaShrinkTime > 5) {
                    arenaShrinkTime -= 5;
                } else {
                    valueChanged = false;
                }
                break;

            case FINAL_SIZE_SLOT:
                if (isLeftClick && arenaFinalSize < 200) {
                    arenaFinalSize += 10;
                } else if (isRightClick && arenaFinalSize > 50) {
                    arenaFinalSize -= 10;
                } else {
                    valueChanged = false;
                }
                break;

            case SPECIAL_ITEMS_SLOT:
                specialItems = !specialItems;
                break;

            case GRACE_PERIOD_SLOT:
                if (isLeftClick && arenaGracePeriod < 180) {
                    arenaGracePeriod += 10;
                } else if (isRightClick && arenaGracePeriod > 0) {
                    arenaGracePeriod -= 10;
                } else {
                    valueChanged = false;
                }
                break;

            case ABILITY_COOLDOWN_MULTIPLIER_SLOT:
                if (isLeftClick && abilityCooldownMultiplier < 2.0) {
                    abilityCooldownMultiplier = Math.min(2.0, abilityCooldownMultiplier + 0.1);
                    abilityCooldownMultiplier = Math.round(abilityCooldownMultiplier * 10) / 10.0; // Round to 1 decimal place
                } else if (isRightClick && abilityCooldownMultiplier > 0.5) {
                    abilityCooldownMultiplier = Math.max(0.5, abilityCooldownMultiplier - 0.1);
                    abilityCooldownMultiplier = Math.round(abilityCooldownMultiplier * 10) / 10.0; // Round to 1 decimal place
                } else {
                    valueChanged = false;
                }
                break;

            case ARENA_EVENTS_SLOT:
                arenaEvents = !arenaEvents;
                break;

            case BACK_SLOT:
                openScreen(ConfigGUIManager.ScreenType.MAIN);
                return;

            case SAVE_SLOT:
                saveChanges();
                player.closeInventory();
                return;

            default:
                valueChanged = false;
                break;
        }

        if (valueChanged) {
            markChanged();
            updateItems();
        }
    }

    @Override
    public void saveChanges() {
        if (!needsSaving()) {
            return;
        }

        GameConfig config = getConfig();
        config.setArenaSize(arenaSize);
        config.setArenaShrinking(arenaShrink);
        config.setArenaShrinkTime(arenaShrinkTime);
        config.setArenaFinalSize(arenaFinalSize);
        config.setSpecialItemsEnabled(specialItems);
        config.setArenaGracePeriod(arenaGracePeriod);
        config.setAbilityCooldownMultiplier(abilityCooldownMultiplier);
        config.setArenaEventsEnabled(arenaEvents);

        plugin.getConfigManager().saveConfig();
    }
}