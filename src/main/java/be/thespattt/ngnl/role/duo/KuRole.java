package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class KuRole extends DuoRole {

    private static final int REPAIR_COOLDOWN = 20 * 60;
    private long lastRepairUse = 0L;
    private int scannerTaskId = -1;

    public KuRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player != null && partner != null) {
            MessageUtil.sendMessage(player, "&eShi is: &a" + partner.getName());
            MessageUtil.sendMessage(player, "&bYour health is linked with Shi's.");
        }
        startScannerTask();
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastRepairUse = 0L;
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || !plugin.getGameManager().isPlayerAlive(playerId) || !partnerId.equals(getPartnerUUID())) {
            return;
        }
        if (plugin.getGameManager().revivePlayer(partnerId, player.getLocation(), Math.min(player.getHealth(), 8.0))) {
            MessageUtil.broadcast("&bShi and Ku's shared core restored one of them.");
        }
    }

    private void startScannerTask() {
        if (scannerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scannerTaskId);
        }
        scannerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            if (player == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
                return;
            }

            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(playerId) || !other.getWorld().equals(player.getWorld())) {
                    continue;
                }
                if (!plugin.getGameManager().isPlayerAlive(other.getUniqueId()) || other.getLocation().distance(player.getLocation()) > 30.0) {
                    continue;
                }
                boolean hasSpecial = Arrays.stream(other.getInventory().getContents())
                        .filter(item -> item != null && item.hasItemMeta() && item.getItemMeta() != null)
                        .anyMatch(item -> item.getItemMeta().getPersistentDataContainer().has(
                                plugin.getNamespacedKey("role_item"),
                                org.bukkit.persistence.PersistentDataType.STRING
                        ) || item.getItemMeta().getPersistentDataContainer().has(
                                plugin.getNamespacedKey("special_item"),
                                org.bukkit.persistence.PersistentDataType.STRING
                        ));
                if (hasSpecial) {
                    MessageUtil.sendMessage(player, "&bScanner: special equipment detected near &f" + other.getName());
                }
            }
        }, 20L, 20L * 15);
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack repair = new ItemBuilder(Material.IRON_INGOT)
                .name("&b&lRepair Pulse")
                .lore("&7Instantly repairs Shi's equipment.", "&cCooldown: 20 minutes")
                .glow(true)
                .setTag("role_item", "KU")
                .build();
        player.getInventory().addItem(repair);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.IRON_INGOT) {
            return useRepairPulse();
        }
        return false;
    }

    private boolean useRepairPulse() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player == null || partner == null || !isArenaPhaseActive()) {
            return false;
        }

        long now = System.currentTimeMillis() / 1000;
        if (now - lastRepairUse < REPAIR_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cCooldown: " + formatTime(REPAIR_COOLDOWN - (now - lastRepairUse)));
            return false;
        }

        lastRepairUse = now;
        for (ItemStack armor : partner.getInventory().getArmorContents()) {
            if (armor != null) {
                armor.setDurability((short) 0);
            }
        }
        for (ItemStack content : partner.getInventory().getContents()) {
            if (content != null && content.getType().getMaxDurability() > 0) {
                content.setDurability((short) 0);
            }
        }
        MessageUtil.sendMessage(player, "&aShi's equipment has been fully repaired.");
        MessageUtil.sendMessage(partner, "&aKu repaired all of your equipment.");
        return true;
    }

    @Override
    public void onDeath(UUID killerId) {
        if (scannerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scannerTaskId);
            scannerTaskId = -1;
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Ku.",
                "Your goal is to win with Shi.",
                "Your health is permanently linked with Shi's.",
                "You periodically detect nearby special items."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive a Repair Pulse in finale.",
                "It instantly repairs Shi's equipment every 20 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Shi.";
    }

    private String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
