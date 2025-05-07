package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.config.GameConfig;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Role configuration screen
 */
public class RoleConfigScreen extends ConfigScreen {

    // Track which roles are enabled
    private Map<RoleType, Boolean> enabledRoles;

    // Current page of roles
    private int currentPage = 0;
    private final int ROLES_PER_PAGE = 21;
    private List<RoleType> allRoles;

    // Item slots
    private static final int ABILITY_STRENGTH_SLOT = 4;

    // Role ability strength (multiplier for role ability effectiveness)
    private double abilityStrength;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player Player viewing the screen
     */
    public RoleConfigScreen(NoGameNoLife plugin, Player player) {
        super(plugin, player, "§8Role Configuration", 54);
        this.enabledRoles = new HashMap<>();
        this.allRoles = new ArrayList<>();
        loadAllRoles();
        initializeIfNeeded();
    }

    private void loadAllRoles(){
        GameConfig config = getConfig();
        for (RoleType type : RoleType.values()) {
            allRoles.add(type);
            enabledRoles.put(type, config.isRoleEnabled(type));
        }
    }
    @Override
    protected void initialize() {
        Bukkit.getLogger().info("Initializing RoleConfigScreen for player: " + player.getName());
        GameConfig config = getConfig();
        abilityStrength = config.getRoleAbilityStrength();


        // Create and add items
        updateItems();

        // Add navigation buttons
        boolean hasNextPage = (currentPage + 1) * ROLES_PER_PAGE < allRoles.size();
        addNavigationButtons(true, true, hasNextPage);

        // Fill empty slots
        fillEmptySlots();
    }

    /**
     * Update the items in the inventory based on current values
     */
    private void updateItems() {
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, null);
        }

        // Add ability strength item
        ItemStack abilityStrengthItem = new ItemBuilder(Material.BLAZE_POWDER)
                .name("&e&lRole Ability Strength")
                .lore(
                        "&7Current: &f" + abilityStrength + "x",
                        "&7This is a multiplier for all role ability",
                        "&7effectiveness. Higher values make",
                        "&7abilities more powerful.",
                        "",
                        "&eLeft-click &7to increase by 0.1 (max 2.0)",
                        "&eRight-click &7to decrease by 0.1 (min 0.5)"
                )
                .build();
        inventory.setItem(ABILITY_STRENGTH_SLOT, abilityStrengthItem);

        // Display roles for current page
        int startIndex = currentPage * ROLES_PER_PAGE;
        int endIndex = Math.min(startIndex + ROLES_PER_PAGE, allRoles.size());

        int slot = 9;
        for (int i = startIndex; i < endIndex; i++) {
            RoleType roleType = allRoles.get(i);
            boolean enabled = enabledRoles.get(roleType);

            Material material = Material.PLAYER_HEAD;
            // Choose different materials for different role types
            if (roleType.getFaction() != null) {
                switch (roleType.getFaction()) {
                    case IMANITY:
                        material = Material.WHITE_WOOL;
                        break;
                    case FLUGEL:
                        material = Material.LIGHT_BLUE_WOOL;
                        break;
                    case WEREBEASTS:
                        material = Material.ORANGE_WOOL;
                        break;
                    case EX_MACHINA:
                        material = Material.GRAY_WOOL;
                        break;
                    case ELVES:
                        material = Material.GREEN_WOOL;
                        break;
                    case OLD_DEUS:
                        material = Material.PURPLE_WOOL;
                        break;
                    case OTHER:
                        material = Material.YELLOW_WOOL;
                        break;
                }
            }

            ItemStack roleItem = new ItemBuilder(material)
                    .name("&b&l" + roleType.getDisplayName())
                    .lore(
                            "&7Status: " + (enabled ? "&aEnabled" : "&cDisabled"),
                            "&7Faction: &f" + (roleType.getFaction() != null ? roleType.getFaction().name() : "None"),
                            "&7Duo: " + (roleType.isDuo() ? "&aYes" : "&cNo"),
                            roleType.isDuo() ? "&7Partner: &f" + roleType.getPartnerRoleType().getDisplayName() : "",
                            "",
                            "&eClick &7to toggle enabled/disabled"
                    )
                    .build();

            // Skip navigation button slots
            if (slot == BACK_SLOT || slot == SAVE_SLOT || slot == NEXT_SLOT) {
                slot++;
            }

            inventory.setItem(slot++, roleItem);
        }

        // Add page indicator
        int totalPages = (int) Math.ceil((double) allRoles.size() / ROLES_PER_PAGE);
        ItemStack pageItem = new ItemBuilder(Material.PAPER)
                .name("&e&lPage " + (currentPage + 1) + "/" + totalPages)
                .lore(
                        "&7Showing roles " + (startIndex + 1) + "-" + endIndex,
                        "&7out of " + allRoles.size() + " total roles"
                )
                .build();
        inventory.setItem(49, pageItem);
    }

    @Override
    public void handleClick(int slot, boolean isLeftClick, boolean isRightClick, boolean isShiftClick) {
        boolean valueChanged = true;

        if (slot == ABILITY_STRENGTH_SLOT) {
            if (isLeftClick && abilityStrength < 2.0) {
                abilityStrength = Math.min(2.0, abilityStrength + 0.1);
                abilityStrength = Math.round(abilityStrength * 10) / 10.0; // Round to 1 decimal place
            } else if (isRightClick && abilityStrength > 0.5) {
                abilityStrength = Math.max(0.5, abilityStrength - 0.1);
                abilityStrength = Math.round(abilityStrength * 10) / 10.0; // Round to 1 decimal place
            } else {
                valueChanged = false;
            }
        } else if (slot == BACK_SLOT) {
            openScreen(ConfigGUIManager.ScreenType.MAIN);
            return;
        } else if (slot == SAVE_SLOT) {
            saveChanges();
            player.closeInventory();
            return;
        } else if (slot == NEXT_SLOT) {
            int totalPages = (int) Math.ceil((double) allRoles.size() / ROLES_PER_PAGE);
            if (currentPage < totalPages - 1) {
                currentPage++;
                updateItems();
            }
            return;
        } else if (slot < 45) { // Role toggle slots
            int startIndex = currentPage * ROLES_PER_PAGE;
            int endIndex = Math.min(startIndex + ROLES_PER_PAGE, allRoles.size());

            // Calculate which role this slot corresponds to
            int roleIndex = -1;
            int currentSlot = 9;
            for (int i = startIndex; i < endIndex; i++) {
                // Skip navigation button slots
                if (currentSlot == BACK_SLOT || currentSlot == SAVE_SLOT || currentSlot == NEXT_SLOT) {
                    currentSlot++;
                }

                if (currentSlot == slot) {
                    roleIndex = i;
                    break;
                }
                currentSlot++;
            }

            if (roleIndex >= 0 && roleIndex < allRoles.size()) {
                RoleType roleType = allRoles.get(roleIndex);
                enabledRoles.put(roleType, !enabledRoles.get(roleType));

                // If this is a duo role, also toggle the partner
                if (roleType.isDuo() && roleType.getPartnerRoleType() != null) {
                    enabledRoles.put(roleType.getPartnerRoleType(), enabledRoles.get(roleType));
                }
            } else {
                valueChanged = false;
            }
        } else {
            valueChanged = false;
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
        config.setRoleAbilityStrength(abilityStrength);

        // Save role enabled states
        for (Map.Entry<RoleType, Boolean> entry : enabledRoles.entrySet()) {
            config.setRoleEnabled(entry.getKey(), entry.getValue());
        }

        plugin.getConfigManager().saveConfig();
    }
}