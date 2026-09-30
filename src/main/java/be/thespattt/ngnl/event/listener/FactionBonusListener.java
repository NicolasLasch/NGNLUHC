package be.thespattt.ngnl.event.listener;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.player.faction.FactionType;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Passive faction advantages that are pure rules:
 * Elves get +1 level on the enchantments they apply, Old Deus take less damage from mobs and the environment.
 */
public class FactionBonusListener implements Listener {

    /** Damage multiplier of Old Deus against non-player damage. */
    private static final double OLD_DEUS_DAMAGE_FACTOR = 0.7;

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public FactionBonusListener(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Give the faction of a player.
     *
     * @param player Player to check
     * @return His faction or null
     */
    private FactionType factionOf(Player player) {
        NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        return data != null && plugin.getConfigManager().getGameConfig().areFactionBonusesEnabled() ? data.getFaction() : null;
    }

    /**
     * Elves: every enchantment they obtain is one level higher (when it can be).
     *
     * @param event Enchant event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (factionOf(event.getEnchanter()) != FactionType.ELVES) {
            return;
        }
        Map<Enchantment, Integer> boosted = new HashMap<>(event.getEnchantsToAdd());
        boosted.replaceAll((enchantment, level) -> Math.min(enchantment.getMaxLevel(), level + 1));
        event.getEnchantsToAdd().putAll(boosted);
    }

    /**
     * Old Deus: extra resistance to damage that does not come from a player (PvE, environment).
     *
     * @param event Damage event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || factionOf(player) != FactionType.OLD_DEUS) {
            return;
        }
        boolean fromPlayer = event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player;
        if (!fromPlayer && plugin.getGameManager().isGameRunning()) {
            event.setDamage(event.getDamage() * OLD_DEUS_DAMAGE_FACTOR);
        }
    }
}
