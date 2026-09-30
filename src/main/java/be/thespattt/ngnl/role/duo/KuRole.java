package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Ku: shares his health pool with Shi, passively detects players carrying special role items
 * and repairs all of Shi's equipment in the finale.
 */
public class KuRole extends ShiKuBase {

    /** Cooldown of the repair pulse in seconds. */
    private static final int REPAIR_COOLDOWN = 20 * 60;
    /** Radius (blocks) of the special item scanner. */
    private static final double SCAN_RADIUS = 30.0;
    /** Ticks between two scans. */
    private static final long SCAN_PERIOD = 20L * 15;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (KU)
     */
    public KuRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Player shi = getPartnerPlayer();
        if (shi != null) {
            MessageUtil.sendMessage(player, "&eShi est : &a" + shi.getName());
        }
        MessageUtil.sendMessage(player, "&bTa santé est partagée avec Shi : vous devez tous les deux être éliminés pour perdre.");
        runRepeating(this::scanForSpecialItems, 20L, SCAN_PERIOD);
    }

    /**
     * Warn Ku about players carrying special role items within the scanner radius.
     */
    private void scanForSpecialItems() {
        Player player = getPlayer();
        if (player == null || !isAlive()) {
            return;
        }
        for (Player other : nearbyAlivePlayers(player.getLocation(), SCAN_RADIUS)) {
            if (carriesSpecialItem(other)) {
                MessageUtil.sendMessage(player, "&bScanner : équipement spécial détecté près de &f" + other.getName());
            }
        }
    }

    /**
     * Check whether a player carries a role item or a special item.
     *
     * @param other Player to inspect
     * @return True if he carries one
     */
    private boolean carriesSpecialItem(Player other) {
        for (ItemStack item : other.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            var container = item.getItemMeta().getPersistentDataContainer();
            if (container.has(plugin.getNamespacedKey("role_item"), PersistentDataType.STRING)
                    || container.has(plugin.getNamespacedKey("special_item"), PersistentDataType.STRING)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("repair_pulse");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.IRON_INGOT, "&b&lImpulsion de réparation",
                "&7Répare instantanément tout l'équipement de Shi.", "",
                "&eClic droit pour activer", "&cRecharge : 20 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.IRON_INGOT && useRepairPulse();
    }

    /**
     * Repair every damageable item of Shi.
     *
     * @return True if the pulse was used
     */
    private boolean useRepairPulse() {
        Player player = getPlayer();
        Player shi = getPartnerPlayer();
        if (player == null || shi == null || !isArenaPhaseActive() || !isPartnerAlive()) {
            return false;
        }
        if (!tryUseCooldown("repair_pulse", REPAIR_COOLDOWN)) {
            return false;
        }
        for (ItemStack armor : shi.getInventory().getArmorContents()) {
            repair(armor);
        }
        for (ItemStack content : shi.getInventory().getContents()) {
            repair(content);
        }
        MessageUtil.sendMessage(player, "&aL'équipement de Shi a été entièrement réparé.");
        MessageUtil.sendMessage(shi, "&aKu a réparé tout ton équipement.");
        return true;
    }

    /**
     * Set the damage of an item to zero when it is damageable.
     *
     * @param item Item to repair (can be null)
     */
    private void repair(ItemStack item) {
        if (item != null && item.getItemMeta() instanceof Damageable meta && meta.hasDamage()) {
            meta.setDamage(0);
            item.setItemMeta(meta);
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Ku.",
                "Your goal is to win with Shi.",
                "Your health is shared with Shi: you must both be eliminated to lose.",
                "You periodically detect players carrying special items nearby."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Repair Pulse: instantly repairs all of Shi's equipment (every 20 minutes)."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Shi.";
    }
}
