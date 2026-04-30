package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ShiRole extends DuoRole {

    private static final int SAFE_ZONE_COOLDOWN = 20 * 60;
    private long lastSafeZoneUse = 0L;
    private int healthSyncTaskId = -1;
    private int safeZoneTaskId = -1;
    private long safeZoneUntil = 0L;

    public ShiRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player != null && partner != null) {
            MessageUtil.sendMessage(player, "&eKu is: &a" + partner.getName());
            MessageUtil.sendMessage(player, "&bYour health is linked to Ku's.");
        }
        startHealthLink();
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastSafeZoneUse = 0L;
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

    private void startHealthLink() {
        if (healthSyncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(healthSyncTaskId);
        }
        healthSyncTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player self = getPlayer();
            Player partner = getPartnerPlayer();
            if (self == null || partner == null) {
                return;
            }
            if (!plugin.getGameManager().isPlayerAlive(playerId) || !plugin.getGameManager().isPlayerAlive(partner.getUniqueId())) {
                return;
            }

            double sharedHealth = Math.min(self.getHealth(), partner.getHealth());
            if (Math.abs(self.getHealth() - sharedHealth) > 0.1) {
                self.setHealth(Math.max(1.0, sharedHealth));
            }
            if (Math.abs(partner.getHealth() - sharedHealth) > 0.1) {
                partner.setHealth(Math.max(1.0, sharedHealth));
            }
        }, 20L, 20L);
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack core = new ItemBuilder(Material.LIGHT_BLUE_DYE)
                .name("&b&lSafety Core")
                .lore("&7Creates a short no-damage zone.", "&cCooldown: 20 minutes")
                .glow(true)
                .setTag("role_item", "SHI")
                .build();
        player.getInventory().addItem(core);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.LIGHT_BLUE_DYE) {
            return useSafeZone();
        }
        return false;
    }

    private boolean useSafeZone() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastSafeZoneUse < SAFE_ZONE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cCooldown: " + formatTime(SAFE_ZONE_COOLDOWN - (now - lastSafeZoneUse)));
            return false;
        }
        lastSafeZoneUse = now;
        safeZoneUntil = System.currentTimeMillis() + (15_000L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 10, false, false));
        Player partner = getPartnerPlayer();
        if (partner != null) {
            partner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 10, false, false));
        }
        MessageUtil.sendMessage(player, "&aSafety zone active for 15 seconds around you and Ku.");
        return true;
    }

    public boolean isSafeZoneActive() {
        return System.currentTimeMillis() < safeZoneUntil;
    }

    @Override
    public void onDeath(UUID killerId) {
        if (healthSyncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(healthSyncTaskId);
            healthSyncTaskId = -1;
        }
        if (safeZoneTaskId != -1) {
            Bukkit.getScheduler().cancelTask(safeZoneTaskId);
            safeZoneTaskId = -1;
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Shi.",
                "Your goal is to win with Ku.",
                "Your health is permanently linked with Ku's.",
                "A single death can be corrected while the other survives."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive a Safety Core in finale.",
                "It creates a short no-damage window every 20 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Ku.";
    }

    private String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
