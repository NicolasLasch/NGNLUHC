package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.config.GameConfig;
import be.thespattt.ngnl.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * General configuration screen
 */
public class GeneralConfigScreen extends ConfigScreen {

    // Item slots
    private static final int EPISODE_LENGTH_SLOT = 10;
    private static final int QUALIFICATION_PLAYERS_SLOT = 12;
    private static final int RANDOM_ROLES_SLOT = 14;
    private static final int PVP_TIMER_SLOT = 16;
    private static final int BORDER_SIZE_SLOT = 28;
    private static final int BORDER_SHRINK_SLOT = 30;
    private static final int ALWAYS_DAY_SLOT = 32;
    private static final int NATURAL_REGEN_SLOT = 34;

    // Config values that can be modified
    private int episodeLength;
    private int qualificationPlayers;
    private boolean randomRoles;
    private int pvpTimer;
    private int borderSize;
    private boolean borderShrink;
    private boolean alwaysDay;
    private boolean naturalRegen;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player Player viewing the screen
     */
    public GeneralConfigScreen(NoGameNoLife plugin, Player player) {
        super(plugin, player, "§8General Configuration", 54);
        initializeIfNeeded();
    }

    @Override
    protected void initialize() {
        // Load current values from config
        GameConfig config = getConfig();
        episodeLength = config.getEpisodeLength();
        qualificationPlayers = config.getQualificationPlayers();
        randomRoles = config.isRandomRoleAssignment();
        pvpTimer = config.getPvpEnabledTime();
        borderSize = config.getInitialBorderSize();
        borderShrink = config.isBorderShrinking();
        alwaysDay = config.isAlwaysDay();
        naturalRegen = config.isNaturalRegenEnabled();

        // Create and add items
        updateItems();

        // Add information item
        ItemStack infoItem = new ItemBuilder(Material.BOOK)
                .name("&a&lGeneral Settings")
                .lore(
                        "&7Configure general game settings",
                        "",
                        "&eLeft-click &7to increase values",
                        "&eRight-click &7to decrease values",
                        "&eShift-click &7to toggle boolean values"
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
        // Episode length item
        ItemStack episodeLengthItem = new ItemBuilder(Material.CLOCK)
                .name("&e&lEpisode Length")
                .lore(
                        "&7Current: &f" + episodeLength + " minutes",
                        "",
                        "&eLeft-click &7to increase (max 60)",
                        "&eRight-click &7to decrease (min 5)"
                )
                .build();
        inventory.setItem(EPISODE_LENGTH_SLOT, episodeLengthItem);

        // Qualification players item
        ItemStack qualificationPlayersItem = new ItemBuilder(Material.PLAYER_HEAD)
                .name("&e&lQualification Players")
                .lore(
                        "&7Current: &f" + qualificationPlayers + " players",
                        "&7This is how many players remain",
                        "&7before entering the arena phase",
                        "",
                        "&eLeft-click &7to increase (max 16)",
                        "&eRight-click &7to decrease (min 2)"
                )
                .build();
        inventory.setItem(QUALIFICATION_PLAYERS_SLOT, qualificationPlayersItem);

        // Random roles item
        ItemStack randomRolesItem = new ItemBuilder(Material.NAME_TAG)
                .name("&e&lRandom Role Assignment")
                .lore(
                        "&7Current: " + (randomRoles ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, roles will be randomly",
                        "&7assigned to players. Otherwise,",
                        "&7they will be assigned according to config.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(RANDOM_ROLES_SLOT, getToggleItem(randomRoles));

        // PvP timer item
        ItemStack pvpTimerItem = new ItemBuilder(Material.IRON_SWORD)
                .name("&e&lPvP Timer")
                .lore(
                        "&7Current: &f" + pvpTimer + " minutes",
                        "&7PvP will be enabled after this",
                        "&7amount of time has passed.",
                        "",
                        "&eLeft-click &7to increase (max 60)",
                        "&eRight-click &7to decrease (min 0)"
                )
                .build();
        inventory.setItem(PVP_TIMER_SLOT, pvpTimerItem);

        // Border size item
        ItemStack borderSizeItem = new ItemBuilder(Material.BARRIER)
                .name("&e&lInitial Border Size")
                .lore(
                        "&7Current: &f" + borderSize + " blocks",
                        "&7The initial size of the world border.",
                        "",
                        "&eLeft-click &7to increase by 100 (max 3000)",
                        "&eRight-click &7to decrease by 100 (min 500)"
                )
                .build();
        inventory.setItem(BORDER_SIZE_SLOT, borderSizeItem);

        // Border shrink item
        ItemStack borderShrinkItem = new ItemBuilder(Material.ARMOR_STAND)
                .name("&e&lBorder Shrinking")
                .lore(
                        "&7Current: " + (borderShrink ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, the border will shrink",
                        "&7over time during the game.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(BORDER_SHRINK_SLOT, getToggleItem(borderShrink));

        // Always day item
        ItemStack alwaysDayItem = new ItemBuilder(Material.SUNFLOWER)
                .name("&e&lAlways Day")
                .lore(
                        "&7Current: " + (alwaysDay ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, it will always be daytime",
                        "&7during the game.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(ALWAYS_DAY_SLOT, getToggleItem(alwaysDay));

        // Natural regeneration item
        ItemStack naturalRegenItem = new ItemBuilder(Material.GOLDEN_APPLE)
                .name("&e&lNatural Regeneration")
                .lore(
                        "&7Current: " + (naturalRegen ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, players will naturally",
                        "&7regenerate health when their hunger",
                        "&7is full.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(NATURAL_REGEN_SLOT, getToggleItem(naturalRegen));
    }

    @Override
    public void handleClick(int slot, boolean isLeftClick, boolean isRightClick, boolean isShiftClick) {
        boolean valueChanged = true;

        switch (slot) {
            case EPISODE_LENGTH_SLOT:
                if (isLeftClick && episodeLength < 60) {
                    episodeLength += 5;
                } else if (isRightClick && episodeLength > 5) {
                    episodeLength -= 5;
                } else {
                    valueChanged = false;
                }
                break;

            case QUALIFICATION_PLAYERS_SLOT:
                if (isLeftClick && qualificationPlayers < 16) {
                    qualificationPlayers++;
                } else if (isRightClick && qualificationPlayers > 2) {
                    qualificationPlayers--;
                } else {
                    valueChanged = false;
                }
                break;

            case RANDOM_ROLES_SLOT:
                randomRoles = !randomRoles;
                break;

            case PVP_TIMER_SLOT:
                if (isLeftClick && pvpTimer < 60) {
                    pvpTimer += 5;
                } else if (isRightClick && pvpTimer > 0) {
                    pvpTimer -= 5;
                } else {
                    valueChanged = false;
                }
                break;

            case BORDER_SIZE_SLOT:
                if (isLeftClick && borderSize < 3000) {
                    borderSize += 100;
                } else if (isRightClick && borderSize > 500) {
                    borderSize -= 100;
                } else {
                    valueChanged = false;
                }
                break;

            case BORDER_SHRINK_SLOT:
                borderShrink = !borderShrink;
                break;

            case ALWAYS_DAY_SLOT:
                alwaysDay = !alwaysDay;
                break;

            case NATURAL_REGEN_SLOT:
                naturalRegen = !naturalRegen;
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
        config.setEpisodeLength(episodeLength);
        config.setQualificationPlayers(qualificationPlayers);
        config.setRandomRoleAssignment(randomRoles);
        config.setPvpEnabledTime(pvpTimer);
        config.setInitialBorderSize(borderSize);
        config.setBorderShrinking(borderShrink);
        config.setAlwaysDay(alwaysDay);
        config.setNaturalRegenEnabled(naturalRegen);

        plugin.getConfigManager().saveConfig();
    }
}