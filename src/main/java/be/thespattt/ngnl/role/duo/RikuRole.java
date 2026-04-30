package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
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

/**
 * Template for implementing duo roles
 * Use this as a starting point for creating new duo roles
 */
public class RikuRole extends DuoRole {

    // Define constants for ability cooldowns, durations, etc.
    private static final int ABILITY_COOLDOWN = 15 * 60; // 15 minutes in seconds
    private static final int ABILITY_DURATION = 30; // 30 seconds

    // Define partner proximity settings if applicable
    private static final int PARTNER_PROXIMITY_RANGE = 25; // 25 blocks

    // Ability usage tracking
    private long lastAbilityUsage = 0;
    private int abilitiesUsed = 0;
    private static final int MAX_ABILITY_USES = 3; // Maximum uses of ability per game

    // Task IDs for scheduled tasks
    private int proximityCheckTaskId = -1;
    private int schwiReviveTaskId = -1;
    private int arenaSupportTaskId = -1;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type
     */
    public RikuRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    /**
     * For creating specific roles, use a simpler constructor
     * Example for derived class:
     * public SpecificRole(NoGameNoLife plugin, UUID playerId) {
     *     super(plugin, playerId, RoleType.ROLE_NAME);
     * }
     */

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Find partner player
        UUID partnerUUID = getPartnerUUID();
        if (partnerUUID != null) {
            Player partnerPlayer = Bukkit.getPlayer(partnerUUID);
            if (partnerPlayer != null) {
                // Send partner information
                MessageUtil.sendMessage(player, "&eYour partner is: &a" + partnerPlayer.getName());
            }
        }

        MessageUtil.sendMessage(player, "&eYou gain 1 heart whenever you win a mini-game.");
    }

    /**
     * Start the proximity check task
     */
    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        // No passive mini-game effect besides the extra heart on victory.
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        if (isWinner) {
            Player player = getPlayer();
            if (player != null) {
                MessageUtil.sendMessage(player, "&aYour role bonus grants you 1 extra heart.");
            }
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();

        lastAbilityUsage = 0;
        startArenaSupportTask();
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || !partnerId.equals(getPartnerUUID())) {
            return;
        }

        MessageUtil.sendMessage(player, "&cSchwi has been eliminated. Survive 5 minutes to revive her.");
        if (schwiReviveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(schwiReviveTaskId);
        }

        schwiReviveTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            Player currentPlayer = getPlayer();
            if (currentPlayer == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
                return;
            }

            if (plugin.getGameManager().isPlayerAlive(partnerId)) {
                return;
            }

            if (plugin.getGameManager().revivePlayer(partnerId, currentPlayer.getLocation(), 8.0)) {
                plugin.getGameManager().removePlayerHearts(playerId, 3.0);
                plugin.getGameManager().removePlayerHearts(partnerId, 3.0);
                MessageUtil.broadcast("&dRiku survived long enough to revive Schwi.");
            }
        }, 5L * 60L * 20L);
    }

    /**
     * Use the role's primary ability
     *
     * @return True if ability was used successfully
     */
    @Override
    protected void giveArenaPhaseItems(Player player) {
        MessageUtil.sendMessage(player, "&eUse &a/heal &eto transfer 2 HP to Schwi whenever needed.");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Riku Dola.",
                "Your goal is to win with Schwi.",
                "Whenever you win a mini-game, you gain 1 extra heart.",
                "If you survive 5 minutes after Schwi dies, she revives",
                "next to you and both of you lose 3 hearts."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "If Schwi falls below 3 hearts, you periodically gain Strength II.",
                "You can also use /heal to transfer 2 HP to Schwi at will."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Schwi.";
    }

    /**
     * Format seconds into a readable time string
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    protected String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    @Override
    public void onDeath(UUID killerId) {
        super.onDeath(killerId);
        if (schwiReviveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(schwiReviveTaskId);
            schwiReviveTaskId = -1;
        }
        if (arenaSupportTaskId != -1) {
            Bukkit.getScheduler().cancelTask(arenaSupportTaskId);
            arenaSupportTaskId = -1;
        }
    }

    public boolean transferHealthToSchwi() {
        Player player = getPlayer();
        Player schwi = getPartnerPlayer();
        if (player == null || schwi == null) {
            return false;
        }
        if (player.getHealth() <= 2.0) {
            MessageUtil.sendMessage(player, "&cYou need more than 1 heart to transfer health.");
            return false;
        }

        player.setHealth(Math.max(1.0, player.getHealth() - 2.0));
        schwi.setHealth(Math.min(schwi.getMaxHealth(), schwi.getHealth() + 2.0));
        MessageUtil.sendMessage(player, "&aYou transferred 2 HP to Schwi.");
        MessageUtil.sendMessage(schwi, "&aRiku transferred 2 HP to you.");
        return true;
    }

    private void startArenaSupportTask() {
        if (arenaSupportTaskId != -1) {
            Bukkit.getScheduler().cancelTask(arenaSupportTaskId);
        }

        arenaSupportTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            Player schwi = getPartnerPlayer();
            if (player == null || schwi == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
                return;
            }

            if (schwi.getHealth() <= 6.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false));
            }
        }, 20L, 20L);
    }
}
