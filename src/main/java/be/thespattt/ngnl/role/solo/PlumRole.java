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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PlumRole extends Role {

    private static final int BLIND_COOLDOWN = 15 * 60;
    private int deviceUses = 0;
    private int darknessTaskId = -1;
    private long lastBlindUse = 0L;

    public PlumRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        ItemStack device = new ItemBuilder(Material.SCULK_SENSOR)
                .name("&5&lListening Device")
                .lore("&7Up to 3 uses. Records nearby conversations as player names.")
                .glow(true)
                .setTag("role_item", "PLUM")
                .build();
        player.getInventory().addItem(device);
        startDarknessTask();
    }

    private void startDarknessTask() {
        if (darknessTaskId != -1) {
            Bukkit.getScheduler().cancelTask(darknessTaskId);
        }
        darknessTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            if (player == null || player.getLocation().getBlock().getLightLevel() > 7) {
                return;
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(playerId) || !other.getWorld().equals(player.getWorld())) {
                    continue;
                }
                if (other.getLocation().distance(player.getLocation()) <= 12.0) {
                    other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                }
            }
        }, 20L, 20L * 5);
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastBlindUse = 0L;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack blind = new ItemBuilder(Material.INK_SAC)
                .name("&5&lBlind Spell")
                .lore("&7Blinds nearby enemies.", "&cCooldown: 15 minutes")
                .glow(true)
                .setTag("role_item", "PLUM")
                .build();
        player.getInventory().addItem(blind);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.SCULK_SENSOR) {
            return useListeningDevice();
        }
        if (item.getType() == Material.INK_SAC) {
            return useBlindSpell();
        }
        return false;
    }

    private boolean useListeningDevice() {
        Player player = getPlayer();
        if (player == null || deviceUses >= 3) {
            return false;
        }
        deviceUses++;
        StringBuilder heard = new StringBuilder();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(playerId) || !other.getWorld().equals(player.getWorld())) {
                continue;
            }
            if (other.getLocation().distance(player.getLocation()) <= 8.0) {
                if (!heard.isEmpty()) {
                    heard.append(", ");
                }
                heard.append(other.getName());
                if (ThreadLocalRandom.current().nextInt(100) < 20) {
                    MessageUtil.sendMessage(other, "&cYou think you noticed one of Plum's devices.");
                }
            }
        }
        MessageUtil.sendMessage(player, "&dListening device captured: &f" + (heard.isEmpty() ? "nothing" : heard));
        return true;
    }

    private boolean useBlindSpell() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastBlindUse < BLIND_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cBlind spell cooldown active.");
            return false;
        }
        lastBlindUse = now;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(playerId) || !plugin.getGameManager().isPlayerAlive(other.getUniqueId())) {
                continue;
            }
            if (other.getWorld().equals(player.getWorld()) && other.getLocation().distance(player.getLocation()) <= 12.0) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 8 * 20, 0, false, false));
            }
        }
        MessageUtil.sendMessage(player, "&aYour blind spell hit nearby enemies.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Plum.",
                "Your goal is to win alone or with an alliance.",
                "You thrive in darkness and outline players in the dark.",
                "You can place up to 3 listening devices."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive a blind spell in finale.",
                "It blinds nearby enemies every 15 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
