package be.thespattt.ngnl.item;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Manager class for special items in the game
 */
public class ItemManager {

    private final NoGameNoLife plugin;
    private final Map<String, ItemStack> specialItems = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public ItemManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Load special items
     */
    public void loadItems() {
        // Create special items
        createAkaSiAnse();
        createSuniaster();
        createBloodDestructionBomb();
        createElfRunes();
        createImanityCrown();
        createExMachinaCore();
        createOldDeusFragment();

        // Create role-specific items
        createSoraCrown();
        createLoveGun();
        createOracleCard();
        createKnockbackStick();
        createIDHelmet();
        createFlugelBook();
        createTeleportStick();

        MessageUtil.logInfo("Loaded " + specialItems.size() + " special items");
    }

    /**
     * Create the Aka Si Anse item
     */
    private void createAkaSiAnse() {
        ItemStack akaSiAnse = new ItemBuilder(Material.NETHER_STAR)
                .name("&c&lAka Si Anse")
                .lore(
                        "&7A powerful weapon that can reduce a player",
                        "&7to 2 hearts with a single use.",
                        "",
                        "&eRight-click to select a target",
                        "&c&lWARNING: &7Also damages nearby players"
                )
                .glow(true)
                .setTag("special_item", "aka_si_anse")
                .build();

        specialItems.put("aka_si_anse", akaSiAnse);
    }

    /**
     * Create the Suniaster item
     */
    private void createSuniaster() {
        ItemStack suniaster = new ItemBuilder(Material.END_CRYSTAL)
                .name("&e&lSuniaster")
                .lore(
                        "&7A powerful artifact of the Old Deus.",
                        "&7Grants 15 hearts to the first Old Deus who obtains it.",
                        "",
                        "&eRight-click to activate",
                        "&c&lWARNING: &7Non-gods should destroy this item"
                )
                .glow(true)
                .setTag("special_item", "suniaster")
                .build();

        specialItems.put("suniaster", suniaster);
    }

    /**
     * Create the Blood Destruction Bomb item
     */
    private void createBloodDestructionBomb() {
        ItemStack bloodBomb = new ItemBuilder(Material.FIRE_CHARGE)
                .name("&4&lBlood Destruction Bomb")
                .lore(
                        "&7A devastating weapon inspired by Schwi's sacrifice.",
                        "&7Damages all players within 20 blocks.",
                        "&7User only takes half damage.",
                        "",
                        "&eRight-click to activate",
                        "&c&lWARNING: &7Single use only"
                )
                .glow(true)
                .setTag("special_item", "blood_bomb")
                .build();

        specialItems.put("blood_bomb", bloodBomb);
    }

    /**
     * Create the Elf Runes item
     */
    private void createElfRunes() {
        ItemStack elfRunes = new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("&a&lElf Runes")
                .lore(
                        "&7Magical runes that reveal player movements.",
                        "&7Lasts for 3 minutes once placed.",
                        "&7Visible to all players.",
                        "",
                        "&eRight-click to place",
                        "&7Duration: 3 minutes"
                )
                .glow(true)
                .setTag("special_item", "elf_runes")
                .build();

        specialItems.put("elf_runes", elfRunes);
    }

    /**
     * Create the Imanity Crown item
     */
    private void createImanityCrown() {
        ItemStack imanityCrown = new ItemBuilder(Material.GOLDEN_HELMET)
                .name("&6&lImanity Crown")
                .lore(
                        "&7Grants temporary resistance but reveals",
                        "&7your position to all players.",
                        "&7Perfect for bold strategies.",
                        "",
                        "&eRight-click to activate",
                        "&7Duration: 45 seconds",
                        "&7Cooldown: 5 minutes"
                )
                .enchant(Enchantment.BLAST_PROTECTION, 2)
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .setTag("special_item", "imanity_crown")
                .build();

        specialItems.put("imanity_crown", imanityCrown);
    }

    /**
     * Create the Ex-Machina Core item
     */
    private void createExMachinaCore() {
        ItemStack exMachinaCore = new ItemBuilder(Material.STICK)
                .name("&b&lEx-Machina Core")
                .lore(
                        "&7Records a capability used against you",
                        "&7and allows you to use it once.",
                        "",
                        "&eRight-click to activate after recording",
                        "&7Single use only"
                )
                .glow(true)
                .setTag("special_item", "ex_machina_core")
                .build();

        specialItems.put("ex_machina_core", exMachinaCore);
    }

    /**
     * Create the Old Deus Fragment item
     */
    private void createOldDeusFragment() {
        ItemStack oldDeusFragment = new ItemBuilder(Material.PRISMARINE_SHARD)
                .name("&5&lOld Deus Fragment")
                .lore(
                        "&7A fragment of an Old Deus's power.",
                        "&7Can reverse a mini-game defeat.",
                        "&7Costs 3 hearts to activate.",
                        "",
                        "&eRight-click to activate",
                        "&c&lWARNING: &7Single use only"
                )
                .glow(true)
                .setTag("special_item", "old_deus_fragment")
                .build();

        specialItems.put("old_deus_fragment", oldDeusFragment);
    }

    /**
     * Create Sora's Crown item
     */
    private void createSoraCrown() {
        ItemStack soraCrown = new ItemBuilder(Material.GOLDEN_HELMET)
                .name("&6&lSora's Crown")
                .lore(
                        "&7Creates 5 clones around you for 10 seconds.",
                        "",
                        "&eRight-click to activate",
                        "&7Cooldown: 20 minutes"
                )
                .glow(true)
                .setTag("role_item", "SORA")
                .build();

        specialItems.put("sora_crown", soraCrown);
    }

    /**
     * Create the Love Gun item
     */
    private void createLoveGun() {
        ItemStack loveGun = new ItemBuilder(Material.BOW)
                .name("&d&lLove Gun")
                .lore(
                        "&7Reduces the hit player to 6 hearts for 30 seconds.",
                        "",
                        "&eShoot a player to activate",
                        "&7Cooldown: 10 minutes"
                )
                .enchant(Enchantment.INFINITY, 1)
                .enchant(Enchantment.KNOCKBACK, 1)
                .setTag("role_item", "STEPHANIE")
                .build();

        specialItems.put("love_gun", loveGun);
    }

    /**
     * Create the Oracle Card item
     */
    private void createOracleCard() {
        ItemStack oracleCard = new ItemBuilder(Material.PAPER)
                .name("&5&lOracle Card")
                .lore(
                        "&7Teleports 2 players to each other.",
                        "&7Has a 20% chance to teleport you instead.",
                        "",
                        "&eRight-click to activate",
                        "&7Cooldown: 20 minutes"
                )
                .glow(true)
                .setTag("role_item", "KURAMI")
                .build();

        specialItems.put("oracle_card", oracleCard);
    }

    private void createKnockbackStick() {
        ItemStack stick = new ItemBuilder(Material.STICK)
                .name("&6&lSumo Stick")
                .lore(
                        "&7Knockback I.",
                        "&7One use per round for Ivan and Nonna."
                )
                .enchant(Enchantment.KNOCKBACK, 1)
                .setTag("sumo_stick", "1")
                .build();

        specialItems.put("knockback_stick", stick);
    }

    /**
     * Create the ID Helmet item
     */
    private void createIDHelmet() {
        ItemStack idHelmet = new ItemBuilder(Material.IRON_HELMET)
                .name("&7&lID Helmet")
                .lore(
                        "&7Allows you to see footprints of other players.",
                        "&7Footprints disappear after 10 seconds.",
                        "",
                        "&eEquip to activate"
                )
                .enchant(Enchantment.PROTECTION, 1)
                .setTag("role_item", "IVAN")
                .build();

        specialItems.put("id_helmet", idHelmet);
    }

    /**
     * Create the Flügel Book item
     */
    private void createFlugelBook() {
        ItemStack flugelBook = new ItemBuilder(Material.BOOK)
                .name("&d&lBook of 18 Wings")
                .lore(
                        "&7Triggers one of 4 random catastrophes:",
                        "&71. Nuclear Explosion",
                        "&72. Lava Cascade",
                        "&73. Crater Creation",
                        "&74. Radioactive Zone",
                        "",
                        "&eRight-click to activate",
                        "&7Cooldown: 20 minutes"
                )
                .glow(true)
                .setTag("role_item", "JIBRIL")
                .build();

        specialItems.put("flugel_book", flugelBook);
    }

    /**
     * Create the Teleport Stick item
     */
    private void createTeleportStick() {
        ItemStack teleportStick = new ItemBuilder(Material.BLAZE_ROD)
                .name("&5&lTP Stick")
                .lore(
                        "&7Teleports you 30 blocks towards a player.",
                        "&7Gives you Blindness for 10 seconds.",
                        "",
                        "&eRight-click to activate",
                        "&7Cooldown: 10 minutes"
                )
                .glow(true)
                .setTag("role_item", "HOLOU")
                .build();

        specialItems.put("teleport_stick", teleportStick);
    }

    /**
     * Get a special item by name
     *
     * @param itemName Name of the item
     * @return ItemStack or null if not found
     */
    public ItemStack getSpecialItem(String itemName) {
        return specialItems.getOrDefault(itemName, null);
    }

    /**
     * Give a special item to a player
     *
     * @param player Player to receive the item
     * @param itemName Name of the item
     * @return True if the item was given
     */
    public boolean giveSpecialItem(Player player, String itemName) {
        ItemStack item = getSpecialItem(itemName);

        if (item == null || player == null) {
            return false;
        }

        player.getInventory().addItem(item.clone());
        MessageUtil.sendMessage(player, "&aYou received: &e" + item.getItemMeta().getDisplayName());

        return true;
    }

    /**
     * Check if an item is a special item
     *
     * @param item Item to check
     * @return True if the item is a special item
     */
    public boolean isSpecialItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        return item.getItemMeta().getDisplayName().contains("&l");
    }

    /**
     * Check whether an item is the Sumo knockback stick
     *
     * @param item Item to check
     * @return True if the item is the knockback stick
     */
    public boolean isKnockbackStick(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(plugin.getNamespacedKey("sumo_stick"), org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * Get role-specific items for a role
     *
     * @param roleType Role type
     * @return ItemStack array of role items or null if none found
     */
    public ItemStack[] getRoleItems(String roleType) {
        if (roleType == null) {
            return new ItemStack[0];
        }

        // Find all items for this role
        return specialItems.values().stream()
                .filter(item -> {
                    if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer() != null) {
                        return item.getItemMeta().getPersistentDataContainer()
                                .has(plugin.getNamespacedKey("role_item"), org.bukkit.persistence.PersistentDataType.STRING) &&
                                item.getItemMeta().getPersistentDataContainer()
                                        .get(plugin.getNamespacedKey("role_item"), org.bukkit.persistence.PersistentDataType.STRING)
                                        .equals(roleType);
                    }
                    return false;
                })
                .toArray(ItemStack[]::new);
    }


}
