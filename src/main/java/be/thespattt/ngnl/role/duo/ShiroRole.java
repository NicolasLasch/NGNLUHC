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
 * Implementation of the Shiro role
 */
public class ShiroRole extends DuoRole {

    private static final int CLONE_COOLDOWN = 20 * 60; // 20 minutes in seconds
    private static final int CLONE_DURATION = 10; // 10 seconds
    private static final int PARTNER_PROXIMITY_RANGE = 30; // 30 blocks
    private static final int MAX_BONUS_USES = 2; // Maximum times Shiro can give a bonus to Sora

    private long lastCloneUsage = 0;
    private int bonusUsesRemaining = MAX_BONUS_USES;
    private boolean substitutionRequested = false;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param playerId UUID of the player
     */
    public ShiroRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Send additional role-specific information
        MessageUtil.sendMessage(player, "&eYou know Sora's identity from the start.");

        // Find Sora's player
        UUID partnerUUID = getPartnerUUID();
        if (partnerUUID != null) {
            Player partnerPlayer = Bukkit.getPlayer(partnerUUID);
            if (partnerPlayer != null) {
                MessageUtil.sendMessage(player, "&eSora is: &a" + partnerPlayer.getName());
            }
        }

        // Schedule proximity check task
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkPartnerProximity, 20L, 20L);
    }

    /**
     * Accept a substitution request from Sora
     *
     * @return True if substitution was accepted
     */
    public boolean acceptSubstitution() {
        if (!substitutionRequested) {
            MessageUtil.sendMessage(getPlayer(), "&cThere is no pending substitution request.");
            return false;
        }

        Player player = getPlayer();
        Player partner = getPartnerPlayer();

        if (player == null || partner == null) {
            return false;
        }

        // Get Sora's role and complete the substitution
        SoraRole soraRole = (SoraRole) plugin.getRoleManager().getPlayerRole(getPartnerUUID());
        if (soraRole != null && soraRole.completeSubstitution()) {
            MessageUtil.sendMessage(player, "&aYou have accepted to substitute for Sora in the mini-game.");
            MessageUtil.sendMessage(partner, "&aShiro has accepted your substitution request.");
            substitutionRequested = false;
            return true;
        }

        return false;
    }

    /**
     * Set substitution request status
     *
     * @param requested New status
     */
    public void setSubstitutionRequested(boolean requested) {
        this.substitutionRequested = requested;
    }

    /**
     * Give a bonus to Sora for a mini-game
     *
     * @return True if bonus was given successfully
     */
    public boolean giveBonusToSora() {
        if (bonusUsesRemaining <= 0) {
            MessageUtil.sendMessage(getPlayer(), "&cYou have already used all your bonuses for this game.");
            return false;
        }

        Player partner = getPartnerPlayer();
        if (partner == null) {
            MessageUtil.sendMessage(getPlayer(), "&cSora is not online.");
            return false;
        }

        // Apply bonus to Sora (in real implementation, this would depend on the mini-game)
        // For now, let's give a generic advantage like speed boost
        partner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, false, false));

        bonusUsesRemaining--;

        MessageUtil.sendMessage(getPlayer(), "&aYou gave a bonus to Sora! Bonuses remaining: " + bonusUsesRemaining);
        MessageUtil.sendMessage(partner, "&aShiro gave you a bonus for your mini-game!");

        return true;
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        // Check if this was a substitution for Sora
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (substitutionRequested) {
            MessageUtil.sendMessage(player, "&6You are substituting for Sora in this mini-game.");
            // Any special handling for substitution
        }
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        // Handle mini-game result
        if (isWinner) {
            MessageUtil.sendMessage(getPlayer(), "&aYou won the mini-game!");
        } else {
            MessageUtil.sendMessage(getPlayer(), "&cYou lost the mini-game!");
        }

        // Reset substitution status if applicable
        substitutionRequested = false;
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();

        // Reset cooldowns for arena phase
        lastCloneUsage = 0;
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        MessageUtil.sendMessage(player, "&c&lSora has been eliminated!");
        MessageUtil.sendMessage(player, "&cYou feel significantly weaker without your partner...");

        // Apply permanent weakness effect since partner is dead
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
    }

    /**
     * Use the clone ability
     *
     * @return True if ability was used successfully
     */
    public boolean useCloneAbility() {
        if (!isArenaPhaseActive()) {
            return false;
        }

        Player player = getPlayer();
        if (player == null) {
            return false;
        }

        // Check cooldown
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime - lastCloneUsage < CLONE_COOLDOWN) {
            long remainingCooldown = CLONE_COOLDOWN - (currentTime - lastCloneUsage);
            MessageUtil.sendMessage(player, "&cYou must wait " + formatTime(remainingCooldown) + " to use this ability again!");
            return false;
        }

        // Update cooldown
        lastCloneUsage = currentTime;

        // Spawn 5 clones around the player (would be implemented with entities or particles)
        MessageUtil.sendMessage(player, "&a&lYou summoned 5 clones around yourself!");
        MessageUtil.broadcastNearby(player.getLocation(), 30, "&c" + player.getName() + " has summoned clones!");

        // TODO : This would be implemented with actual clone entities
        // spawnClones(player, 5, CLONE_DURATION);

        // Schedule cleanup after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            MessageUtil.sendMessage(player, "&eYour clones have disappeared.");
        }, CLONE_DURATION * 20L);

        return true;
    }

    /**
     * Check partner proximity and apply effects
     */
    private void checkPartnerProximity() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        Player partner = Bukkit.getPlayer(getPartnerUUID());
        if (partner == null || !plugin.getGameManager().isPlayerAlive(getPartnerUUID())) {
            // Apply weakness effect if partner is not present
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.RESISTANCE);

            if (!player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
            }
            return;
        }

        // Check distance to partner
        double distance = player.getLocation().distance(partner.getLocation());

        if (distance <= PARTNER_PROXIMITY_RANGE) {
            // Within range - apply positive effects
            player.removePotionEffect(PotionEffectType.WEAKNESS);

            if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
            }

            if (!player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            }
        } else {
            // Out of range - apply negative effects
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.RESISTANCE);

            if (!player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
            }
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        // Create and give crown item
        ItemStack crown = new ItemBuilder(Material.GOLDEN_HELMET)
                .name("&6&lShiro's Crown")
                .lore(
                        "&7Allows you to spawn 5 clones",
                        "&7around you for 10 seconds.",
                        "",
                        "&eRight-click to activate",
                        "&cCooldown: 20 minutes"
                )
                .glow(true)
                .build();

        // Add to player's inventory
        player.getInventory().addItem(crown);

        // Explain how to use
        MessageUtil.sendMessage(player, "&aYou received &6Shiro's Crown&a!");
        MessageUtil.sendMessage(player, "&eRight-click to create clones. (Cooldown: 20 minutes)");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.GOLDEN_HELMET) {
            // Check if this is Shiro's crown (would need better verification in real implementation)
            return useCloneAbility();
        }
        return false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Shiro.",
                "Your goal is to win with Sora.",
                "For this, during the launch of a mini-game, you can 2 times",
                "give a bonus to Sora.",
                "You know Sora's identity and position from the start."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You now have a crown that allows you to spawn 5 clones",
                "around you for 10 seconds. This ability can be used",
                "every 20 minutes.",
                "",
                "If you are close to Sora (within 30 blocks), you will have",
                "the Resistance and Speed effects. However, if you are",
                "more than 30 blocks away, you will have the Weakness effect."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Sora.";
    }

    /**
     * Format seconds into a readable time string
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        return String.format("%d:%02d", minutes, remainingSeconds);
    }
}