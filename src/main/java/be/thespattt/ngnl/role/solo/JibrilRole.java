package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Implementation of the Jibril role
 */
public class JibrilRole extends Role {

    private static final int CATASTROPHE_COOLDOWN = 20 * 60; // 20 minutes in seconds
    private static final int CATASTROPHE_DURATION = 30; // 30 seconds
    private static final int REGEN_INTERVAL = 30 * 20; // 30 seconds in ticks

    private long lastCatastropheUsage = 0;
    private int scheduledTask = -1;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param playerId UUID of the player
     */
    public JibrilRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Schedule regeneration task
        scheduledTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            applyRegeneration();
        }, REGEN_INTERVAL, REGEN_INTERVAL);

        // Send additional role information
        MessageUtil.sendMessage(player, "&eYou have natural regeneration throughout the game.");
        MessageUtil.sendMessage(player, "&eYou can form an alliance with another player later using &6/alliance <player>&e.");
    }

    /**
     * Apply regeneration effect to the player
     */
    private void applyRegeneration() {
        Player player = getPlayer();
        if (player == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
            // Cancel task if player is offline or dead
            if (scheduledTask != -1) {
                Bukkit.getScheduler().cancelTask(scheduledTask);
                scheduledTask = -1;
            }
            return;
        }

        // Apply regeneration effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, false));
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();

        // Reset cooldowns for arena phase
        lastCatastropheUsage = 0;

        // Additional setup for arena phase
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        MessageUtil.sendMessage(player, "&aYour regeneration ability is still active in the arena phase.");
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        // Jibril has no special abilities during mini-games
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        // Handle mini-game result
        if (isWinner) {
            MessageUtil.sendMessage(getPlayer(), "&aYou won the mini-game!");
        } else {
            MessageUtil.sendMessage(getPlayer(), "&cYou lost the mini-game!");
        }
    }

    @Override
    public void onDeath(UUID killerId) {
        // Cancel regeneration task when player dies
        if (scheduledTask != -1) {
            Bukkit.getScheduler().cancelTask(scheduledTask);
            scheduledTask = -1;
        }
    }

    /**
     * Use the catastrophe ability
     *
     * @return True if ability was used successfully
     */
    public boolean useCatastropheAbility() {
        if (!isArenaPhaseActive()) {
            return false;
        }

        Player player = getPlayer();
        if (player == null) {
            return false;
        }

        // Check cooldown
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime - lastCatastropheUsage < CATASTROPHE_COOLDOWN) {
            long remainingCooldown = CATASTROPHE_COOLDOWN - (currentTime - lastCatastropheUsage);
            MessageUtil.sendMessage(player, "&cYou must wait " + formatTime(remainingCooldown) + " to use this ability again!");
            return false;
        }

        // Update cooldown
        lastCatastropheUsage = currentTime;

        // Choose a random catastrophe
        int catastropheType = new Random().nextInt(4);
        String catastropheName;

        switch (catastropheType) {
            case 0:
                catastropheName = "Nuclear Explosion";
                createNuclearExplosion(player.getLocation());
                break;
            case 1:
                catastropheName = "Lava Cascade";
                createLavaCascade(player.getLocation());
                break;
            case 2:
                catastropheName = "Crater Creation";
                createCrater(player.getLocation());
                break;
            case 3:
                catastropheName = "Radiation Zone";
                createRadiationZone(player.getLocation());
                break;
            default:
                catastropheName = "Unknown Catastrophe";
                break;
        }

        // Announce the catastrophe
        MessageUtil.broadcast("&4&l" + player.getName() + " has unleashed: " + catastropheName + "!");
        MessageUtil.sendMessage(player, "&6You are immune to the effects of your own catastrophe.");

        return true;
    }

    /**
     * Create a nuclear explosion effect
     *
     * @param location Center location
     */
    private void createNuclearExplosion(Location location) {
        // This would be implemented with actual explosion effects, particles, and damage
        // For now, just create a simulated explosion
        location.getWorld().createExplosion(location, 0.0F, false); // Visual explosion with no block damage

        // Apply effects to nearby players (except Jibril)
        for (Player nearbyPlayer : location.getWorld().getPlayers()) {
            if (nearbyPlayer.getUniqueId().equals(playerId)) {
                continue; // Skip Jibril
            }

            // Check if player is within range (30 blocks)
            if (nearbyPlayer.getLocation().distance(location) <= 30) {
                // Apply effects
                nearbyPlayer.damage(6.0); // 3 hearts of damage
                nearbyPlayer.setFireTicks(100); // Set on fire briefly
                nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 1));
            }
        }

        // Schedule cleanup
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove any lingering effects, if any
        }, CATASTROPHE_DURATION * 20L);
    }

    /**
     * Create a lava cascade effect
     *
     * @param location Center location
     */
    private void createLavaCascade(Location location) {
        // This would be implemented with actual lava placement and removal
        // For now, just simulate the effect with visuals and damage

        // Apply effects to nearby players (except Jibril)
        for (Player nearbyPlayer : location.getWorld().getPlayers()) {
            if (nearbyPlayer.getUniqueId().equals(playerId)) {
                continue; // Skip Jibril
            }

            // Check if player is within range (20 blocks)
            if (nearbyPlayer.getLocation().distance(location) <= 20) {
                // Apply effects
                nearbyPlayer.damage(4.0); // 2 hearts of damage
                nearbyPlayer.setFireTicks(200); // Set on fire for longer
            }
        }

        // Schedule cleanup
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove any temporary lava blocks, if any were placed
        }, CATASTROPHE_DURATION * 20L);
    }

    /**
     * Create a crater effect
     *
     * @param location Center location
     */
    private void createCrater(Location location) {
        // This would be implemented with actual block removal/replacement
        // For now, just simulate the effect with visuals and knockback

        // Apply effects to nearby players (except Jibril)
        for (Player nearbyPlayer : location.getWorld().getPlayers()) {
            if (nearbyPlayer.getUniqueId().equals(playerId)) {
                continue; // Skip Jibril
            }

            // Check if player is within range (15 blocks)
            if (nearbyPlayer.getLocation().distance(location) <= 15) {
                // Apply knockback effect
                Vector knockback = nearbyPlayer.getLocation().toVector().subtract(location.toVector()).normalize().multiply(2);
                nearbyPlayer.setVelocity(knockback);
                nearbyPlayer.damage(2.0); // 1 heart of damage
            }
        }

        // Schedule cleanup
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Restore any blocks, if any were changed
        }, CATASTROPHE_DURATION * 20L);
    }

    /**
     * Create a radiation zone effect
     *
     * @param location Center location
     */
    private void createRadiationZone(Location location) {
        // This would be implemented with actual particles and persistent effects
        // For now, just simulate with potion effects

        // Start a repeating task to apply radiation effects
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player nearbyPlayer : location.getWorld().getPlayers()) {
                if (nearbyPlayer.getUniqueId().equals(playerId)) {
                    continue; // Skip Jibril
                }

                // Check if player is within range (25 blocks)
                if (nearbyPlayer.getLocation().distance(location) <= 25) {
                    // Apply radiation effects
                    nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                    nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
                }
            }
        }, 20L, 40L); // Every 2 seconds

        // Schedule cleanup
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.getScheduler().cancelTask(taskId);
        }, CATASTROPHE_DURATION * 20L);
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        // Create and give the Book of 18 Wings
        ItemStack book = new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("&4&lBook of 18 Wings")
                .lore(
                        "&7Unleashes one of four random catastrophes:",
                        "&71. Nuclear Explosion",
                        "&72. Lava Cascade",
                        "&73. Crater Creation",
                        "&74. Radiation Zone",
                        "",
                        "&eRight-click to activate",
                        "&cCooldown: 20 minutes",
                        "&aYou are immune to your own catastrophes"
                )
                .glow(true)
                .build();

        // Add to player's inventory
        player.getInventory().addItem(book);

        // Explain how to use
        MessageUtil.sendMessage(player, "&aYou received the &4Book of 18 Wings&a!");
        MessageUtil.sendMessage(player, "&eRight-click to unleash a catastrophe. (Cooldown: 20 minutes)");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.ENCHANTED_BOOK) {
            // Check if this is the Book of 18 Wings (would need better verification in real implementation)
            return useCatastropheAbility();
        }
        return false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Jibril, a powerful Flügel.",
                "Your goal is to win alone or with an alliance.",
                "You don't have any advantages during mini-games,",
                "but you have automatic health regeneration throughout the game."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You now possess the Book of 18 Wings, which allows you",
                "to trigger one of four random catastrophes:",
                "1. Nuclear Explosion: Massive damage in a large radius",
                "2. Lava Cascade: Creates flowing lava that burns enemies",
                "3. Crater Creation: Forms a deep crater, knocking back players",
                "4. Radiation Zone: Creates a zone that poisons players",
                "",
                "You are immune to the effects of your own catastrophes.",
                "This ability can be used every 20 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game alone or with an alliance formed using /alliance.";
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