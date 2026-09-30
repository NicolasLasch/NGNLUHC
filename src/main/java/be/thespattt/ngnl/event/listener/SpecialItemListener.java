package be.thespattt.ngnl.event.listener;

import be.thespattt.ngnl.NoGameNoLife;

import org.bukkit.Material;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listens to the events related to the hidden items: Nina Clive (Aka Si Anse guardian) and the
 * Suniaster pickup.
 */
public class SpecialItemListener implements Listener {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public SpecialItemListener(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * A god picking up the Suniaster claims it immediately.
     *
     * @param event Pickup event
     */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.getGameManager().isGameRunning()) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!plugin.getSpecialItemManager().getEffects().isSpecialItem(stack, "suniaster")) {
            return;
        }
        event.getItem().remove();
        event.setCancelled(true);
        player.getInventory().addItem(stack);
        plugin.getSpecialItemManager().claimSuniaster(player, stack);
    }

    /**
     * Nina Clive can not kill: a lethal blow teleports the victim and leaves him with 5 hearts.
     *
     * @param event Damage event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNinaHitsPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isDamagedByNina(event)) {
            return;
        }
        if (victim.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        event.setCancelled(true);
        plugin.getSpecialItemManager().punishPlayerKilledByNina(victim);
    }

    /**
     * Check whether the damage comes from Nina Clive directly, from her fireball or her fangs.
     *
     * @param event Damage event
     * @return True if Nina is the source
     */
    private boolean isDamagedByNina(EntityDamageByEntityEvent event) {
        if (plugin.getSpecialItemManager().isNina(event.getDamager())) {
            return true;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return plugin.getSpecialItemManager().isNina(shooter);
        }
        if (event.getDamager() instanceof EvokerFangs fangs && fangs.getOwner() != null) {
            return plugin.getSpecialItemManager().isNina(fangs.getOwner());
        }
        return event.getDamager() instanceof Fireball fireball && fireball.getShooter() instanceof LivingEntity owner
                && plugin.getSpecialItemManager().isNina(owner);
    }

    /**
     * Nina drops the Aka Si Anse when she dies.
     *
     * @param event Death event
     */
    @EventHandler
    public void onNinaDeath(EntityDeathEvent event) {
        if (!plugin.getSpecialItemManager().isNina(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        ItemStack akaSiAnse = plugin.getItemManager().getSpecialItem("aka_si_anse");
        if (akaSiAnse != null) {
            event.getDrops().add(akaSiAnse.clone());
        }
        plugin.getSpecialItemManager().onNinaDefeated();
    }
}
