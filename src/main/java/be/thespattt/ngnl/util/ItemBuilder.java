package be.thespattt.ngnl.util;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import be.thespattt.ngnl.NoGameNoLife;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for building custom items easily
 */
public class ItemBuilder {

    private ItemStack item;
    private ItemMeta meta;

    /**
     * Constructor with Material
     *
     * @param material Material to use
     */
    public ItemBuilder(Material material) {
        this(material, 1);
    }

    /**
     * Constructor with Material and amount
     *
     * @param material Material to use
     * @param amount Amount of items
     */
    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    /**
     * Constructor with an existing ItemStack
     *
     * @param item ItemStack to modify
     */
    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    /**
     * Set the display name
     *
     * @param name Display name (supports color codes with &)
     * @return This builder instance
     */
    public ItemBuilder name(String name) {
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        return this;
    }

    /**
     * Set the lore (item description)
     *
     * @param lore List of lore lines
     * @return This builder instance
     */
    public ItemBuilder lore(List<String> lore) {
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        return this;
    }

    /**
     * Set the lore (item description)
     *
     * @param lore Array of lore lines
     * @return This builder instance
     */
    public ItemBuilder lore(String... lore) {
        return lore(Arrays.asList(lore));
    }

    /**
     * Add enchantment to the item
     *
     * @param enchantment Enchantment to add
     * @param level Enchantment level
     * @return This builder instance
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    /**
     * Set item as unbreakable
     *
     * @param unbreakable Whether the item is unbreakable
     * @return This builder instance
     */
    public ItemBuilder unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return this;
    }

    /**
     * Set item durability (damage)
     *
     * @param damage Damage value
     * @return This builder instance
     */
    public ItemBuilder durability(int damage) {
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(damage);
        }
        return this;
    }

    /**
     * Add item flags
     *
     * @param flags ItemFlags to add
     * @return This builder instance
     */
    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    /**
     * Hide attributes
     *
     * @return This builder instance
     */
    public ItemBuilder hideAttributes() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }

    /**
     * Hide enchantments
     *
     * @return This builder instance
     */
    public ItemBuilder hideEnchants() {
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    /**
     * Add a glow effect (adds a dummy enchantment and hides it)
     *
     * @param glow Whether to add glow effect
     * @return This builder instance
     */
    public ItemBuilder glow(boolean glow) {
        if (glow) {
            meta.addEnchant(Enchantment.DENSITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Set leather armor color
     *
     * @param color Color to set
     * @return This builder instance
     */
    public ItemBuilder color(Color color) {
        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(color);
        }
        return this;
    }

    /**
     * Add a custom potion effect to a potion
     *
     * @param effect PotionEffect to add
     * @return This builder instance
     */
    public ItemBuilder potionEffect(PotionEffect effect) {
        if (meta instanceof PotionMeta) {
            ((PotionMeta) meta).addCustomEffect(effect, true);
        }
        return this;
    }

    /**
     * Add a custom potion effect to a potion
     *
     * @param type PotionEffectType
     * @param duration Duration in ticks
     * @param amplifier Effect amplifier
     * @param ambient Whether the effect is ambient
     * @return This builder instance
     */
    public ItemBuilder potionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient) {
        return potionEffect(new PotionEffect(type, duration, amplifier, ambient, true));
    }

    /**
     * Set item amount
     *
     * @param amount Amount of items
     * @return This builder instance
     */
    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Set a string tag in the item's persistent data container
     *
     * @param key Key name
     * @param value String value
     * @return This builder instance
     */
    public ItemBuilder setTag(String key, String value) {
        NamespacedKey namespacedKey = new NamespacedKey(NoGameNoLife.getInstance(), key);
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(namespacedKey, PersistentDataType.STRING, value);
        return this;
    }

    /**
     * Set an integer tag in the item's persistent data container
     *
     * @param key Key name
     * @param value Integer value
     * @return This builder instance
     */
    public ItemBuilder setTag(String key, int value) {
        NamespacedKey namespacedKey = new NamespacedKey(NoGameNoLife.getInstance(), key);
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(namespacedKey, PersistentDataType.INTEGER, value);
        return this;
    }

    /**
     * Set a unique role identifier
     *
     * @param roleId Role identifier string
     * @return This builder instance
     */
    public ItemBuilder setRoleItem(String roleId) {
        return setTag("role_item", roleId);
    }

    /**
     * Set a unique ability identifier
     *
     * @param abilityId Ability identifier string
     * @return This builder instance
     */
    public ItemBuilder setAbilityItem(String abilityId) {
        return setTag("ability_item", abilityId);
    }

    /**
     * Build the final ItemStack
     *
     * @return Built ItemStack
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}