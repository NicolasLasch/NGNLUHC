package be.thespattt.ngnl.event.listener;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.role.solo.KainasRole;
import be.thespattt.ngnl.role.solo.OkeinRole;
import be.thespattt.ngnl.role.solo.PlumRole;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Global rules created by specific roles: only Okein may use fire, Okein's anvils, Kainas's forest
 * inventory and Plum's listening devices.
 */
public class RoleRulesListener implements Listener {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public RoleRulesListener(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Okein: the only one using fire

    /**
     * Whether the "only Okein uses fire" rule applies to a player right now.
     *
     * @param player Player trying to use fire
     * @return True if he must be stopped
     */
    private boolean mustNotUseFire(Player player) {
        if (!plugin.getGameManager().isGameRunning() || plugin.getRoleManager().getPlayerByRole(RoleType.OKEIN) == null) {
            return false;
        }
        return !player.getUniqueId().equals(plugin.getRoleManager().getPlayerByRole(RoleType.OKEIN));
    }

    /**
     * Forbid flint and steel / fire charges to everybody but Okein (plugin items excepted).
     *
     * @param event Interact event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFireItemUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR;
        if (item == null || !rightClick || !mustNotUseFire(event.getPlayer())) {
            return;
        }
        boolean fireItem = item.getType() == Material.FLINT_AND_STEEL || item.getType() == Material.FIRE_CHARGE;
        boolean pluginItem = item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(plugin.getNamespacedKey("special_item"), PersistentDataType.STRING);
        if (fireItem && !pluginItem) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cSeul Ōkein peut utiliser le feu.");
        }
    }

    /**
     * Forbid lava buckets to everybody but Okein.
     *
     * @param event Bucket event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLavaBucket(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == Material.LAVA_BUCKET && mustNotUseFire(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cSeul Ōkein peut utiliser le feu.");
        }
    }

    /**
     * Forbid starting fires to everybody but Okein.
     *
     * @param event Ignite event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onIgnite(BlockIgniteEvent event) {
        Player igniter = event.getPlayer();
        if (igniter != null && mustNotUseFire(igniter)) {
            event.setCancelled(true);
        }
    }

    /**
     * Flame arrows shot by anybody but Okein do not burn.
     *
     * @param event Bow event
     */
    @EventHandler
    public void onShootFlameArrow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter) || !mustNotUseFire(shooter)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow != null && bow.containsEnchantment(Enchantment.FLAME) && event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.setFireTicks(0);
            arrow.setVisualFire(false);
        }
    }

    // ------------------------------------------------------------------ Okein: anvils

    /**
     * An anvil of the Hammer of Destruction hurt a player: Okein remembers it (for the faster reload on a kill).
     *
     * @param event Damage event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHammerAnvilHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof FallingBlock anvil) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Role owner = findAnvilOwner(anvil);
        if (owner instanceof OkeinRole okein) {
            okein.registerAnvilHit(victim.getUniqueId());
        }
    }

    /**
     * Anvils of the hammer vanish when they land instead of leaving a block in the arena.
     *
     * @param event Block change event
     */
    @EventHandler
    public void onHammerAnvilLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock anvil && findAnvilOwner(anvil) != null) {
            event.setCancelled(true);
            anvil.getWorld().spawnParticle(Particle.BLOCK, anvil.getLocation(), 12, 0.3, 0.3, 0.3, Material.ANVIL.createBlockData());
            anvil.remove();
        }
    }

    /**
     * Find the Okein who dropped an anvil.
     *
     * @param anvil Falling block to inspect
     * @return His role, or null if the anvil does not come from the hammer
     */
    private Role findAnvilOwner(FallingBlock anvil) {
        String ownerId = anvil.getPersistentDataContainer().get(new NamespacedKey(plugin, OkeinRole.ANVIL_TAG), PersistentDataType.STRING);
        return ownerId != null ? plugin.getRoleManager().getPlayerRole(UUID.fromString(ownerId)) : null;
    }

    // ------------------------------------------------------------------ Kainas: forest inventory

    /**
     * Clicking a vegetal item of /forest gives a full stack (the inventory is endless).
     *
     * @param event Click event
     */
    @EventHandler
    public void onForestClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KainasRole.ForestHolder)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        Role role = plugin.getRoleManager().getPlayerRole(event.getWhoClicked().getUniqueId());
        if (role instanceof KainasRole kainas) {
            kainas.giveForestStack(clicked.getType());
        }
    }

    // ------------------------------------------------------------------ Plum: listening devices

    /**
     * Forward the chat messages to Plum's listening devices.
     *
     * @param event Chat event (asynchronous)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player speaker = event.getPlayer();
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Role role : plugin.getRoleManager().getAllRoles()) {
                if (role instanceof PlumRole plum) {
                    plum.recordConversation(speaker, message);
                }
            }
        });
    }
}
