package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CoroneRole extends Role {

    private int regenTaskId = -1;
    private int ghostTrackTaskId = -1;

    public CoroneRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        UUID rikuId = plugin.getRoleManager().getPlayerByRole(RoleType.RIKU);
        Player riku = rikuId != null ? Bukkit.getPlayer(rikuId) : null;
        if (riku != null) {
            MessageUtil.sendMessage(player, "&eRiku is: &a" + riku.getName());
        }
        startRegenTask();
    }

    private void startRegenTask() {
        if (regenTaskId != -1) {
            Bukkit.getScheduler().cancelTask(regenTaskId);
        }
        regenTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            if (player == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
                return;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, false));
        }, 20L * 30, 20L * 30);
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        startGhostTracking();
    }

    private void startGhostTracking() {
        if (ghostTrackTaskId != -1) {
            Bukkit.getScheduler().cancelTask(ghostTrackTaskId);
        }
        ghostTrackTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            UUID ghostId = plugin.getRoleManager().getPlayerByRole(RoleType.GHOST);
            Player ghost = ghostId != null ? Bukkit.getPlayer(ghostId) : null;
            if (player == null || ghost == null || !plugin.getGameManager().isPlayerAlive(ghostId)) {
                return;
            }
            player.setCompassTarget(ghost.getLocation());
            if (ghost.getWorld().equals(player.getWorld()) && ghost.getLocation().distance(player.getLocation()) <= 35.0) {
                MessageUtil.sendMessage(player, "&7Your senses pick up 179 Ghost nearby.");
            }
        }, 20L, 20L * 10);
    }

    public void grantGhostVisionCompanion() {
        Player player = getPlayer();
        if (player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
            MessageUtil.sendMessage(player, "&aNonna joined your side. You gain permanent Speed I.");
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Corone Dola.",
                "Your goal is to win alone or with an alliance.",
                "You know Riku from the start.",
                "You regenerate naturally during the game."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You can track 179 Ghost even while it is hidden.",
                "If Ivan dies, Nonna can later join your camp."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
