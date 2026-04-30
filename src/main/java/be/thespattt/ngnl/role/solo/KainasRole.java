package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class KainasRole extends Role {

    private static final int CAGE_COOLDOWN = 15 * 60;
    private long lastCageUse = 0L;
    private int regenScanTaskId = -1;
    private boolean regenInfoKnown = false;

    public KainasRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&eUse /forest to receive a new vegetal supply whenever you need.");
        }
        startRegenScan();
    }

    public void openForestSupply() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.getInventory().addItem(
                new ItemStack(Material.OAK_LOG, 32),
                new ItemStack(Material.OAK_LEAVES, 32),
                new ItemStack(Material.VINE, 16),
                new ItemStack(Material.APPLE, 4),
                new ItemStack(Material.WHEAT_SEEDS, 16),
                new ItemStack(Material.SUGAR_CANE, 16)
        );
        MessageUtil.sendMessage(player, "&aThe forest answers your call.");
    }

    private void startRegenScan() {
        if (regenScanTaskId != -1) {
            Bukkit.getScheduler().cancelTask(regenScanTaskId);
        }
        regenScanTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (regenInfoKnown) {
                return;
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.hasPotionEffect(PotionEffectType.REGENERATION)) {
                    var ngnl = plugin.getPlayerManager().getNGNLPlayer(other.getUniqueId());
                    if (ngnl != null && ngnl.getRole() != null) {
                        regenInfoKnown = true;
                        MessageUtil.sendMessage(getPlayer(), "&aFirst regeneration holder detected: &f" + ngnl.getRole().getDisplayName());
                        return;
                    }
                }
            }
        }, 20L, 20L * 5);
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack cage = new ItemBuilder(Material.OAK_LEAVES)
                .name("&a&lVegetal Cage")
                .lore("&7Creates a leaf cage with buffs inside.", "&cCooldown: 15 minutes")
                .glow(true)
                .setTag("role_item", "KAINAS")
                .build();
        player.getInventory().addItem(cage);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.OAK_LEAVES && useCage();
    }

    private boolean useCage() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastCageUse < CAGE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cVegetal Cage cooldown active.");
            return false;
        }
        lastCageUse = now;
        Location center = player.getLocation().getBlock().getLocation();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 3; y++) {
                    boolean border = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 3 || y == 0;
                    if (!border) {
                        continue;
                    }
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.AIR) {
                        block.setType(Material.OAK_LEAVES);
                    }
                }
            }
        }
        for (int i = 0; i < 60; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyCageEffects(center), i * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> clearCage(center), 60 * 20L);
        return true;
    }

    private void applyCageEffects(Location center) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getWorld().equals(center.getWorld())) {
                continue;
            }
            if (other.getLocation().distance(center) > 4.0) {
                continue;
            }
            if (other.getUniqueId().equals(playerId)) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false));
                other.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
            } else {
                other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
            }
        }
    }

    private void clearCage(Location center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 3; y++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.OAK_LEAVES) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Kainas.",
                "Your goal is to win alone or with an alliance.",
                "You learn who first gains Regeneration.",
                "You can use /forest for endless vegetal supplies."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive the Vegetal Cage in finale.",
                "It buffs you and slows enemies inside for 1 minute."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
