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
public class IvanRole extends DuoRole {

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

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type
     */
    public IvanRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
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

        // Set up role-specific initial abilities or effects
        // Example: Apply initial potion effects
        // player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));

        // Start proximity check if needed
        startProximityCheck();

        // Schedule any other periodic tasks
        // schedulePeriodicTask();
    }

    /**
     * Start the proximity check task
     */
    private void startProximityCheck() {
        // Cancel existing task if any
        if (proximityCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(proximityCheckTaskId);
        }

        // Schedule new task
        proximityCheckTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                this::checkPartnerProximity, 20L, 20L); // Check every second
    }

    /**
     * Check proximity to partner and apply effects
     */
    private void checkPartnerProximity() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        Player partner = Bukkit.getPlayer(getPartnerUUID());
        if (partner == null || !plugin.getGameManager().isPlayerAlive(getPartnerUUID())) {
            // Apply negative effects if partner is offline or dead
            applyPartnerAbsentEffects(player);
            return;
        }

        // Check distance to partner
        double distance = player.getLocation().distance(partner.getLocation());

        if (distance <= PARTNER_PROXIMITY_RANGE) {
            // Apply positive effects when close to partner
            applyPartnerNearbyEffects(player);
        } else {
            // Apply negative effects when far from partner
            applyPartnerDistantEffects(player);
        }
    }

    /**
     * Apply effects when partner is offline or dead
     *
     * @param player The player
     */
    protected void applyPartnerAbsentEffects(Player player) {
        // Remove positive effects
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);

        // Apply negative effects
        if (!player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
        }
    }

    /**
     * Apply effects when partner is nearby
     *
     * @param player The player
     */
    protected void applyPartnerNearbyEffects(Player player) {
        // Remove negative effects
        player.removePotionEffect(PotionEffectType.WEAKNESS);

        // Apply positive effects
        if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }

        if (!player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
    }

    /**
     * Apply effects when partner is distant
     *
     * @param player The player
     */
    protected void applyPartnerDistantEffects(Player player) {
        // Remove positive effects
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);

        // Apply negative or reduced effects
        if (!player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
        }
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Handle mini-game start
        // For example, offer ability to use specific powers or request partner help
        MessageUtil.sendMessage(player, "&6Mini-game started: &e" + miniGameType.name());

        // Add role-specific mini-game start logic here
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
        lastAbilityUsage = 0;
        abilitiesUsed = 0;

        // Apply arena phase specific adjustments
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Additional arena setup
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Handle partner death
        MessageUtil.sendMessage(player, "&c&lYour partner has been eliminated!");

        // Implement consequences of partner death
        // Example: Permanent weakness effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));

        // Add additional partner death effects here
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
        if (currentTime - lastAbilityUsage < ABILITY_COOLDOWN) {
            long remainingCooldown = ABILITY_COOLDOWN - (currentTime - lastAbilityUsage);
            MessageUtil.sendMessage(player, "&cYou must wait " + formatTime(remainingCooldown) + " to use this ability again!");
            return false;
        }

        // Update tracking
        lastAbilityUsage = currentTime;
        abilitiesUsed++;

        // Implement ability effect
        MessageUtil.sendMessage(player, "&a&lYou used your primary ability!");

        // Schedule ability end if it has a duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Cleanup after ability duration ends
            MessageUtil.sendMessage(player, "&eYour ability effect has ended.");
        }, ABILITY_DURATION * 20L);

        return true;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        // Create and give role-specific items
        ItemStack primaryAbilityItem = new ItemBuilder(Material.GOLD_INGOT)
                .name("&6&lSpecial Ability Item")
                .lore(
                        "&7Activates your special ability",
                        "",
                        "&eRight-click to activate",
                        "&cCooldown: " + (ABILITY_COOLDOWN / 60) + " minutes",
                        "&aMaximum uses: " + MAX_ABILITY_USES
                )
                .glow(true)
                .build();

        // Add to player's inventory
        player.getInventory().addItem(primaryAbilityItem);

        // Explain how to use
        MessageUtil.sendMessage(player, "&aYou received your &6Special Ability Item&a!");
        MessageUtil.sendMessage(player, "&eRight-click to activate your ability. (Cooldown: " + (ABILITY_COOLDOWN / 60) + " minutes)");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.GOLD_INGOT) {
            // Verify this is our special item (would need better verification in real implementation)
            return usePrimaryAbility();
        }
        return false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are a Duo Role template.",
                "Your goal is to win with your partner.",
                "Customize this description for each specific role.",
                "Add details about role-specific abilities here."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "During the arena phase, you gain access to special abilities.",
                "You have a special item that activates your primary ability.",
                "Customize this description for each specific role.",
                "Add details about arena-specific abilities here."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with your partner.";
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

        // Clean up any scheduled tasks
        if (proximityCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(proximityCheckTaskId);
            proximityCheckTaskId = -1;
        }

        // Add any other cleanup needed
    }
}