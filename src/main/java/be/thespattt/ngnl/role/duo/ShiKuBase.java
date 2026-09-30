package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.RoleType;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.UUID;

/**
 * Shared behaviour of Shi and Ku: their health is one shared pool.
 * Damage taken by one is also taken by the other, and healing one heals both.
 * Both must be eliminated for the duo to lose.
 */
public abstract class ShiKuBase extends DuoRole implements Listener {

    /** Health (HP) a mirrored hit can never reduce the partner under. */
    private static final double MIRROR_FLOOR = 1.0;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Shi or Ku
     */
    protected ShiKuBase(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Mirror the damage taken by this player on his partner.
     *
     * @param event Damage event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Player partner = findMirrorTarget(event.getEntity());
        if (partner == null || event.getFinalDamage() <= 0) {
            return;
        }
        partner.setHealth(Math.max(MIRROR_FLOOR, partner.getHealth() - event.getFinalDamage()));
    }

    /**
     * Mirror the healing received by this player on his partner.
     *
     * @param event Regain event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        Player partner = findMirrorTarget(event.getEntity());
        if (partner == null) {
            return;
        }
        partner.setHealth(Math.min(partner.getMaxHealth(), partner.getHealth() + event.getAmount()));
    }

    /**
     * Find the partner who must receive a mirrored change, if the entity is this role's player.
     *
     * @param entity Entity that was damaged or healed
     * @return The partner, or null if nothing must be mirrored
     */
    private Player findMirrorTarget(org.bukkit.entity.Entity entity) {
        if (!entity.getUniqueId().equals(playerId) || !isAlive() || !isPartnerAlive()) {
            return null;
        }
        Player partner = getPartnerPlayer();
        boolean inMiniGame = plugin.getMiniGameEngine().isPlayerInMiniGame(playerId)
                || (partner != null && plugin.getMiniGameEngine().isPlayerInMiniGame(partner.getUniqueId()));
        return inMiniGame ? null : partner;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        HandlerList.unregisterAll(this);
    }

    @Override
    public void onDeath(UUID killerId) {
        super.onDeath(killerId);
        HandlerList.unregisterAll(this);
    }
}
