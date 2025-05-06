package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.config.GameConfig;
import be.thespattt.ngnl.player.faction.FactionType;
import be.thespattt.ngnl.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Faction configuration screen
 */
public class FactionConfigScreen extends ConfigScreen {

    // Item slots
    private static final int FACTION_BONUSES_SLOT = 10;
    private static final int FACTION_REVEAL_SLOT = 12;
    private static final int PROXIMITY_BONUS_SLOT = 14;
    private static final int BETRAYAL_PENALTY_SLOT = 16;

    // Faction type slots
    private static final int IMANITY_SLOT = 28;
    private static final int FLUGEL_SLOT = 29;
    private static final int WEREBEASTS_SLOT = 30;
    private static final int EX_MACHINA_SLOT = 31;
    private static final int ELVES_SLOT = 32;
    private static final int OLD_DEUS_SLOT = 33;
    private static final int OTHER_SLOT = 34;

    // Config values
    private boolean factionBonusesEnabled;
    private boolean factionRevealEnabled;
    private boolean proximityBonusEnabled;
    private boolean betrayalPenaltyEnabled;

    // Faction enabled states
    private Map<FactionType, Boolean> enabledFactions = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player Player viewing the screen
     */
    public FactionConfigScreen(NoGameNoLife plugin, Player player) {
        super(plugin, player, "§8Faction Configuration", 54);

        // Initialize collections before calling super constructor
        this.enabledFactions = new HashMap<>();
        // Then call the parent constructor, which will trigger initialize()
    }

    @Override
    protected void initialize() {
        // Load current values
        GameConfig config = getConfig();
        factionBonusesEnabled = config.areFactionBonusesEnabled();
        factionRevealEnabled = config.isFactionRevealEnabled();
        proximityBonusEnabled = config.isProximityBonusEnabled();
        betrayalPenaltyEnabled = config.isBetrayalPenaltyEnabled();

        // Get faction enabled states
        for (FactionType type : FactionType.values()) {
            enabledFactions.put(type, config.isFactionEnabled(type));
        }

        // Create and add items
        updateItems();

        // Add information item
        ItemStack infoItem = new ItemBuilder(Material.SHIELD)
                .name("&6&lFaction Configuration")
                .lore(
                        "&7Configure settings for factions",
                        "&7and their abilities in the game.",
                        "",
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
        ItemStack factionBonusesItem = new ItemBuilder(Material.GOLDEN_APPLE)
                .name("&e&lFaction Bonuses")
                .lore(
                        "&7Current: " + (factionBonusesEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, each faction will have",
                        "&7special bonuses and abilities.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(FACTION_BONUSES_SLOT, getToggleItem(factionBonusesEnabled));

        ItemStack factionRevealItem = new ItemBuilder(Material.NAME_TAG)
                .name("&e&lFaction Reveal")
                .lore(
                        "&7Current: " + (factionRevealEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, player factions will be",
                        "&7revealed to other players at death.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(FACTION_REVEAL_SLOT, getToggleItem(factionRevealEnabled));

        ItemStack proximityBonusItem = new ItemBuilder(Material.ENDER_EYE)
                .name("&e&lProximity Bonus")
                .lore(
                        "&7Current: " + (proximityBonusEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, players will get bonuses",
                        "&7when close to other members of their faction.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(PROXIMITY_BONUS_SLOT, getToggleItem(proximityBonusEnabled));

        ItemStack betrayalPenaltyItem = new ItemBuilder(Material.WITHER_ROSE)
                .name("&e&lBetrayal Penalty")
                .lore(
                        "&7Current: " + (betrayalPenaltyEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, players who betray their",
                        "&7faction will be visibly marked during",
                        "&7the arena phase.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(BETRAYAL_PENALTY_SLOT, getToggleItem(betrayalPenaltyEnabled));

        // Faction items
        addFactionItem(IMANITY_SLOT, FactionType.IMANITY, Material.WHITE_WOOL,
                "Improved negotiation abilities");

        addFactionItem(FLUGEL_SLOT, FactionType.FLUGEL, Material.LIGHT_BLUE_WOOL,
                "Access to special enchanting zones");

        addFactionItem(WEREBEASTS_SLOT, FactionType.WEREBEASTS, Material.ORANGE_WOOL,
                "Enhanced enemy detection");

        addFactionItem(EX_MACHINA_SLOT, FactionType.EX_MACHINA, Material.GRAY_WOOL,
                "More efficient equipment repair");

        addFactionItem(ELVES_SLOT, FactionType.ELVES, Material.GREEN_WOOL,
                "Improved enchantment and potion effects");

        addFactionItem(OLD_DEUS_SLOT, FactionType.OLD_DEUS, Material.PURPLE_WOOL,
                "Resistance to environment damage");

        addFactionItem(OTHER_SLOT, FactionType.OTHER, Material.YELLOW_WOOL,
                "Unique abilities for each role");
    }

    /**
     * Add a faction item to the inventory
     *
     * @param slot Slot to place the item
     * @param factionType Faction type
     * @param material Material for the item
     * @param ability Description of the faction ability
     */
    private void addFactionItem(int slot, FactionType factionType, Material material, String ability) {
        boolean enabled = enabledFactions.get(factionType);

        ItemStack factionItem = new ItemBuilder(material)
                .name("&b&l" + factionType.name())
                .lore(
                        "&7Status: " + (enabled ? "&aEnabled" : "&cDisabled"),
                        "&7Ability: &f" + ability,
                        "",
                        "&eClick &7to toggle enabled/disabled"
                )
                .build();

        inventory.setItem(slot, factionItem);
    }

    @Override
    public void handleClick(int slot, boolean isLeftClick, boolean isRightClick, boolean isShiftClick) {
        boolean valueChanged = true;

        switch (slot) {
            case FACTION_BONUSES_SLOT:
                factionBonusesEnabled = !factionBonusesEnabled;
                break;

            case FACTION_REVEAL_SLOT:
                factionRevealEnabled = !factionRevealEnabled;
                break;

            case PROXIMITY_BONUS_SLOT:
                proximityBonusEnabled = !proximityBonusEnabled;
                break;

            case BETRAYAL_PENALTY_SLOT:
                betrayalPenaltyEnabled = !betrayalPenaltyEnabled;
                break;

            case IMANITY_SLOT:
                toggleFaction(FactionType.IMANITY);
                break;

            case FLUGEL_SLOT:
                toggleFaction(FactionType.FLUGEL);
                break;

            case WEREBEASTS_SLOT:
                toggleFaction(FactionType.WEREBEASTS);
                break;

            case EX_MACHINA_SLOT:
                toggleFaction(FactionType.EX_MACHINA);
                break;

            case ELVES_SLOT:
                toggleFaction(FactionType.ELVES);
                break;

            case OLD_DEUS_SLOT:
                toggleFaction(FactionType.OLD_DEUS);
                break;

            case OTHER_SLOT:
                toggleFaction(FactionType.OTHER);
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

    /**
     * Toggle a faction's enabled state
     *
     * @param factionType The faction type to toggle
     */
    private void toggleFaction(FactionType factionType) {
        enabledFactions.put(factionType, !enabledFactions.get(factionType));
    }

    @Override
    public void saveChanges() {
        if (!needsSaving()) {
            return;
        }

        GameConfig config = getConfig();
        config.setFactionBonusesEnabled(factionBonusesEnabled);
        config.setFactionRevealEnabled(factionRevealEnabled);
        config.setProximityBonusEnabled(proximityBonusEnabled);
        config.setBetrayalPenaltyEnabled(betrayalPenaltyEnabled);

        // Save faction enabled states
        for (Map.Entry<FactionType, Boolean> entry : enabledFactions.entrySet()) {
            config.setFactionEnabled(entry.getKey(), entry.getValue());
        }

        plugin.getConfigManager().saveConfig();
    }
}