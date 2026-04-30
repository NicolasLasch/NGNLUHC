package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
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
import java.util.Set;
import java.util.UUID;

public class AzrielRole extends Role {

    private static final int NO_FLY_COOLDOWN = 20 * 60;
    private long lastNoFlyUse = 0L;
    private int flugelScanTaskId = -1;
    private int copiedEpisode = -1;

    private static final Set<RoleType> FLUGEL_ROLES = Set.of(RoleType.JIBRIL, RoleType.AZRIEL);

    public AzrielRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        startFlugelScan();
        ItemStack copy = new ItemBuilder(Material.PRISMARINE_CRYSTALS)
                .name("&b&lFlugel Mimicry")
                .lore("&7Once per episode, copy a nearby Flugel's power.")
                .glow(true)
                .setTag("role_item", "AZRIEL")
                .build();
        player.getInventory().addItem(copy);
    }

    private void startFlugelScan() {
        if (flugelScanTaskId != -1) {
            Bukkit.getScheduler().cancelTask(flugelScanTaskId);
        }
        flugelScanTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            if (player == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
                return;
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(playerId) || !other.getWorld().equals(player.getWorld())) {
                    continue;
                }
                var ngnl = plugin.getPlayerManager().getNGNLPlayer(other.getUniqueId());
                if (ngnl == null || ngnl.getRole() == null || !FLUGEL_ROLES.contains(ngnl.getRole().getRoleType())) {
                    continue;
                }
                if (other.getLocation().distance(player.getLocation()) <= 50.0) {
                    MessageUtil.sendMessage(player, "&bFlugel movement detected near &f" + other.getName());
                }
            }
        }, 20L, 20L * 15);
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastNoFlyUse = 0L;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack zone = new ItemBuilder(Material.BLAZE_ROD)
                .name("&c&lAnti-Flight Zone")
                .lore("&7Disables flight and gliding nearby for 30 seconds.", "&cCooldown: 20 minutes")
                .glow(true)
                .setTag("role_item", "AZRIEL")
                .build();
        player.getInventory().addItem(zone);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.PRISMARINE_CRYSTALS) {
            return useMimicry();
        }
        if (item.getType() == Material.BLAZE_ROD) {
            return useAntiFlightZone();
        }
        return false;
    }

    private boolean useMimicry() {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        int episode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        if (copiedEpisode == episode) {
            MessageUtil.sendMessage(player, "&cYou already copied a Flugel this episode.");
            return false;
        }
        copiedEpisode = episode;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60 * 20, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 15 * 20, 0, false, false));
        MessageUtil.sendMessage(player, "&aYou copied a fragment of Flugel power.");
        return true;
    }

    private boolean useAntiFlightZone() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastNoFlyUse < NO_FLY_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cAnti-flight zone cooldown active.");
            return false;
        }
        lastNoFlyUse = now;
        for (int i = 0; i < 30; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player self = getPlayer();
                if (self == null) {
                    return;
                }
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getWorld().equals(self.getWorld()) || other.getLocation().distance(self.getLocation()) > 20.0) {
                        continue;
                    }
                    other.setAllowFlight(false);
                    other.setFlying(false);
                    if (other.isGliding()) {
                        other.setGliding(false);
                    }
                }
            }, i * 20L);
        }
        MessageUtil.broadcast("&cAzriel created a no-flight zone.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Azriel.",
                "Your goal is to win alone or with an alliance.",
                "You detect nearby Flugel in a 50-block radius.",
                "Once per episode, you can copy a fragment of Flugel power."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive an anti-flight zone item in finale.",
                "It disables flight and gliding nearby for 30 seconds."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
