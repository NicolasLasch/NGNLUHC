package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.Role;
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

/**
 * Template for implementing solo roles
 * Use this as a starting point for creating new solo roles
 */
public class HolouRole extends Role {

    // Define constants for ability cooldowns, durations, etc.
    private static final int PRIMARY_ABILITY_COOLDOWN = 15 * 60; // 15 minutes in seconds
    private static final int PRIMARY_ABILITY_DURATION = 30; // 30 seconds
    private static final int SECONDARY_ABILITY_COOLDOWN = 20 * 60; // 20 minutes in seconds

    // Ability usage tracking
    private long lastPrimaryAbilityUsage = 0;
    private long lastSecondaryAbilityUsage = 0;
    private int abilitiesUsed = 0;
    private static final int MAX_ABILITY_USES = 3; // Maximum uses of primary ability per game
    private boolean teleportSwapUsed = false;

    // Knowledge tracking
    private UUID knownPlayerId = null;
    private RoleType knownPlayerRole = null;

    // Task IDs for scheduled tasks
    private int periodicTaskId = -1;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type
     */
    public HolouRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
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
        MessageUtil.sendMessage(player, "&eUse /teleport <p1> <p2> once to swap two players.");
    }

    /**
     * Assign initial knowledge to the player
     * (e.g., knowing another player's role)
     */
    private void assignInitialKnowledge() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Implement role-specific knowledge assignment
        // For example, knowing another player's role

        // Example implementation:
        /*
        // Get all players with roles
        List<UUID> playersWithRoles = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID playerId = onlinePlayer.getUniqueId();
            if (plugin.getRoleManager().hasRole(playerId) && !playerId.equals(this.playerId)) {
                playersWithRoles.add(playerId);
            }
        }

        // Pick a random player if there are any
        if (!playersWithRoles.isEmpty()) {
            Random random = new Random();
            knownPlayerId = playersWithRoles.get(random.nextInt(playersWithRoles.size()));
            knownPlayerRole = plugin.getRoleManager().getPlayerRole(knownPlayerId).getRoleType();

            // Inform the player
            Player knownPlayer = Bukkit.getPlayer(knownPlayerId);
            if (knownPlayer != null) {
                MessageUtil.sendMessage(player, "&eYou know that &a" + knownPlayer.getName() +
                    " &eis the role: &a" + knownPlayerRole.getDisplayName());
            }
        }
        */
    }

    /**
     * Start a periodic task for this role
     */
    private void startPeriodicTask() {
        // Cancel existing task if any
        if (periodicTaskId != -1) {
            Bukkit.getScheduler().cancelTask(periodicTaskId);
        }

        // Schedule new task
        periodicTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                this::periodicEffect, 20L, 20L * 30); // Every 30 seconds
    }

    /**
     * Periodic effect for this role
     */
    private void periodicEffect() {
        Player player = getPlayer();
        if (player == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
            // Cancel task if player is offline or dead
            if (periodicTaskId != -1) {
                Bukkit.getScheduler().cancelTask(periodicTaskId);
                periodicTaskId = -1;
            }
            return;
        }

        // Implement periodic effects
        // Example: Apply regeneration effect
        // player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, false));
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Handle mini-game start
        MessageUtil.sendMessage(player, "&6Mini-game started: &e" + miniGameType.name());

        // Add role-specific mini-game start logic here
        // Example: Apply special effects for particular mini-games
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Handle mini-game result
        if (isWinner) {
            MessageUtil.sendMessage(player, "&aYou won the mini-game!");
            // Add any victory bonuses here
        } else {
            MessageUtil.sendMessage(player, "&cYou lost the mini-game!");
            // Add any defeat consequences here
        }

        // Add role-specific mini-game end logic here
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();

        // Reset cooldowns and counters for arena phase
        lastPrimaryAbilityUsage = 0;
        lastSecondaryAbilityUsage = 0;
        abilitiesUsed = 0;

        // Apply arena phase specific adjustments
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Additional arena setup
    }

    @Override
    public void onDeath(UUID killerId) {
        // Clean up any scheduled tasks
        if (periodicTaskId != -1) {
            Bukkit.getScheduler().cancelTask(periodicTaskId);
            periodicTaskId = -1;
        }

        // Handle death
        // Example: Trigger special effects or notifications
        if (killerId != null) {
            Player killer = Bukkit.getPlayer(killerId);
            if (killer != null) {
                // Notify killer of any special effects
                MessageUtil.sendMessage(killer, "&aYou eliminated a player with the role: &e" +
                        roleType.getDisplayName());
            }
        }

        // Add role-specific death handling here
    }

    /**
     * Use the role's primary ability
     *
     * @return True if ability was used successfully
     */
    public boolean usePrimaryAbility() {
        if (!isArenaPhaseActive()) {
            return false;
        }

        Player player = getPlayer();
        if (player == null) {
            return false;
        }

        // Check usage limits
        if (abilitiesUsed >= MAX_ABILITY_USES) {
            MessageUtil.sendMessage(player, "&cYou have already used this ability the maximum number of times!");
            return false;
        }

        // Check cooldown
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime - lastPrimaryAbilityUsage < PRIMARY_ABILITY_COOLDOWN) {
            long remainingCooldown = PRIMARY_ABILITY_COOLDOWN - (currentTime - lastPrimaryAbilityUsage);
            MessageUtil.sendMessage(player, "&cYou must wait " + formatTime(remainingCooldown) + " to use this ability again!");
            return false;
        }

        // Update tracking
        lastPrimaryAbilityUsage = currentTime;
        abilitiesUsed++;

        // Implement ability effect
        MessageUtil.sendMessage(player, "&a&lYou used your primary ability!");

        // Schedule ability end if it has a duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Cleanup after ability duration ends
            MessageUtil.sendMessage(player, "&eYour primary ability effect has ended.");
        }, PRIMARY_ABILITY_DURATION * 20L);

        return true;
    }

    /**
     * Use the role's secondary ability
     *
     * @return True if ability was used successfully
     */
    public boolean useSecondaryAbility() {
        if (!isArenaPhaseActive()) {
            return false;
        }

        Player player = getPlayer();
        if (player == null) {
            return false;
        }

        // Check cooldown
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime - lastSecondaryAbilityUsage < SECONDARY_ABILITY_COOLDOWN) {
            long remainingCooldown = SECONDARY_ABILITY_COOLDOWN - (currentTime - lastSecondaryAbilityUsage);
            MessageUtil.sendMessage(player, "&cYou must wait " + formatTime(remainingCooldown) + " to use this ability again!");
            return false;
        }

        // Update tracking
        lastSecondaryAbilityUsage = currentTime;

        // Implement ability effect
        MessageUtil.sendMessage(player, "&a&lYou used your secondary ability!");

        // Implement secondary ability effect here

        return true;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack tpStick = new ItemBuilder(Material.BLAZE_ROD)
                .name("&5&lTP Stick")
                .lore("&730-block teleport with 5 seconds of blindness.", "&cCooldown: 10 minutes")
                .glow(true)
                .setTag("role_item", "HOLOU")
                .build();
        player.getInventory().addItem(tpStick);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }

        if (item.getType() == Material.DIAMOND) {
            // Primary ability item
            return usePrimaryAbility();
        } else if (item.getType() == Material.EMERALD) {
            // Secondary ability item
            return useSecondaryAbility();
        }

        return false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Holou.",
                "Your goal is to win alone or with your alliance.",
                "Use /teleport <p1> <p2> once to swap two players."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You can use your TP Stick during finale.",
                "It teleports you near a target and blinds you for 5 seconds."
        );
    }

    public boolean swapPlayers(Player first, Player second) {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        if (teleportSwapUsed) {
            MessageUtil.sendMessage(player, "&cYou already used your swap for this game.");
            return false;
        }

        if (!plugin.getGameManager().isPlayerAlive(first.getUniqueId()) || !plugin.getGameManager().isPlayerAlive(second.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cBoth targets must still be alive.");
            return false;
        }

        var firstLocation = first.getLocation().clone();
        var secondLocation = second.getLocation().clone();
        first.teleport(secondLocation);
        second.teleport(firstLocation);
        teleportSwapUsed = true;

        first.getInventory().addItem(new ItemStack(Material.ENCHANTED_BOOK));
        second.getInventory().addItem(new ItemStack(Material.ENCHANTED_BOOK));
        MessageUtil.broadcast("&5Holou swapped " + first.getName() + " and " + second.getName() + "!");
        return true;
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
    protected String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        return String.format("%d:%02d", minutes, remainingSeconds);
    }
}
