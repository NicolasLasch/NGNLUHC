package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MikoRole extends Role {

    private static final int EYE_COOLDOWN = 20 * 60;
    private final Map<UUID, Deque<Location>> snapshots = new HashMap<>();
    private int snapshotTaskId = -1;
    private long lastEyeUse = 0L;

    public MikoRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.setMaxHealth(24.0);
        player.setHealth(24.0);
        UUID rikuId = plugin.getRoleManager().getPlayerByRole(RoleType.RIKU);
        Player riku = rikuId != null ? Bukkit.getPlayer(rikuId) : null;
        if (riku != null) {
            MessageUtil.sendMessage(player, "&eRiku is: &a" + riku.getName());
        }
        startSnapshots();
    }

    private void startSnapshots() {
        if (snapshotTaskId != -1) {
            Bukkit.getScheduler().cancelTask(snapshotTaskId);
        }
        snapshotTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!plugin.getGameManager().isPlayerAlive(online.getUniqueId())) {
                    continue;
                }
                Deque<Location> deque = snapshots.computeIfAbsent(online.getUniqueId(), ignored -> new ArrayDeque<>());
                deque.addLast(online.getLocation().clone());
                while (deque.size() > 10) {
                    deque.removeFirst();
                }
            }
        }, 20L, 20L);
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastEyeUse = 0L;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack eye = new ItemBuilder(Material.CLOCK)
                .name("&b&lMechanical Eye")
                .lore("&7Rewinds nearby players by 10 seconds.", "&cCooldown: 20 minutes")
                .glow(true)
                .setTag("role_item", "MIKO")
                .build();
        player.getInventory().addItem(eye);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.CLOCK) {
            return useMechanicalEye();
        }
        return false;
    }

    private boolean useMechanicalEye() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastEyeUse < EYE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cCooldown active.");
            return false;
        }
        lastEyeUse = now;
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (!nearby.getWorld().equals(player.getWorld()) || nearby.getLocation().distance(player.getLocation()) > 20.0) {
                continue;
            }
            Deque<Location> deque = snapshots.get(nearby.getUniqueId());
            if (deque == null || deque.isEmpty()) {
                continue;
            }
            nearby.teleport(deque.getFirst());
        }
        MessageUtil.broadcast("&bMiko activated the Mechanical Eye.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Miko.",
                "Your goal is to win alone or with an alliance.",
                "You know Riku from the start.",
                "You permanently have 12 hearts."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive the Mechanical Eye in finale.",
                "It rewinds nearby players by 10 seconds every 20 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
