package be.thespattt.ngnl.arena;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Gestionnaire du combat en arène avec armes Quake
 */
public class ArenaCombatManager implements Listener {

    private final NoGameNoLife plugin;
    private final QuakeWeapon quakeWeapon;
    private boolean arenaPhaseActive = false;

    public ArenaCombatManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.quakeWeapon = new QuakeWeapon(plugin);

        // Enregistrer les événements
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Activer le mode combat arène
     */
    public void activateArenaCombat() {
        arenaPhaseActive = true;
        MessageUtil.logInfo("Mode combat arène activé");
    }

    /**
     * Désactiver le mode combat arène
     */
    public void deactivateArenaCombat() {
        arenaPhaseActive = false;
        quakeWeapon.cleanup();
        MessageUtil.logInfo("Mode combat arène désactivé");
    }

    /**
     * Donner l'équipement d'arène à un joueur
     */
    public void giveArenaEquipment(Player player) {
        ItemStack quakeGun = quakeWeapon.createQuakeWeapon();
        player.getInventory().addItem(quakeGun);

        MessageUtil.sendMessage(player, "&5Vous avez reçu votre équipement d'arène !");
        MessageUtil.sendMessage(player, "&eClic droit avec la houe pour tirer !");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!arenaPhaseActive) return;

        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        String arenaWorldName = plugin.getConfigManager().getGameConfig().getArenaWorldName();
        if (!victim.getWorld().getName().equals(arenaWorldName)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (weapon != null && isTraditionalWeapon(weapon.getType())) {
            event.setCancelled(true);
            MessageUtil.sendMessage(attacker, "&cLes armes traditionnelles sont désactivées ! Utilisez votre Love Gun !");
            return;
        }

        // Si c'est un dégât venant de notre système Quake, laisser passer
        // (Les dégâts Quake passent par Player.damage() directement)
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!arenaPhaseActive) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        String arenaWorldName = plugin.getConfigManager().getGameConfig().getArenaWorldName();
        if (!player.getWorld().getName().equals(arenaWorldName)) return;

        if (item != null && (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW)) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, "&cLes arcs sont désactivés ! Utilisez votre Love Gun !");
        }
    }

    /**
     * Vérifier si un matériau est une arme traditionnelle
     */
    private boolean isTraditionalWeapon(Material material) {
        return material.name().contains("SWORD") ||
                material.name().contains("AXE") ||
                material == Material.BOW ||
                material == Material.CROSSBOW ||
                material == Material.TRIDENT;
    }

    /**
     * Vérifier si un joueur est dans le monde arène
     */
    public boolean isPlayerInArena(Player player) {
        String arenaWorldName = plugin.getConfigManager().getGameConfig().getArenaWorldName();
        return player.getWorld().getName().equals(arenaWorldName);
    }

    /**
     * Vérifier si un joueur a l'arme Quake
     */
    public boolean hasQuakeWeapon(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (quakeWeapon.isQuakeWeapon(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Redonner l'arme Quake si le joueur l'a perdue
     */
    public void ensurePlayerHasQuakeWeapon(Player player) {
        if (!hasQuakeWeapon(player)) {
            ItemStack quakeGun = quakeWeapon.createQuakeWeapon();
            player.getInventory().addItem(quakeGun);
            MessageUtil.sendMessage(player, "&aVotre Love Gun a été restaurée !");
        }
    }

    // Getters
    public QuakeWeapon getQuakeWeapon() {
        return quakeWeapon;
    }

    public boolean isArenaPhaseActive() {
        return arenaPhaseActive;
    }
}