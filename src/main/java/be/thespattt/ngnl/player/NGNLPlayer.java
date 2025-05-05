package be.thespattt.ngnl.player;

import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.player.faction.FactionType;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wrapper class for players in the game
 */
public class NGNLPlayer {

    private final UUID playerId;
    private Role role;
    private UUID alliancePartner;
    private double maxHealth = 20.0; // Default max health (10 hearts)
    private int lastDuoMessageEpisode = 0;
    private int miniGamesWon = 0;
    private int miniGamesLost = 0;
    private int heartsLost = 0;

    // Added fields for location and health
    private Location lastLocation;
    private double lastHealth = 20.0;

    // Special abilities usage tracking
    private final Map<String, Long> abilityCooldowns = new HashMap<>();
    private final Map<String, Integer> abilityUses = new HashMap<>();

    /**
     * Constructor
     *
     * @param playerId UUID of the player
     */
    public NGNLPlayer(UUID playerId) {
        this.playerId = playerId;
    }

    /**
     * Get the player's UUID
     *
     * @return Player UUID
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Get the player object
     *
     * @return Player object or null if offline
     */
    public Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    /**
     * Get the player's role
     *
     * @return Role or null if not assigned
     */
    public Role getRole() {
        return role;
    }

    /**
     * Set the player's role
     *
     * @param role Role to assign
     */
    public void setRole(Role role) {
        this.role = role;
    }

    /**
     * Get the player's faction
     *
     * @return FactionType or null if no role assigned
     */
    public FactionType getFaction() {
        return role != null ? role.getRoleType().getFaction() : null;
    }

    /**
     * Get the player's alliance partner
     *
     * @return UUID of alliance partner or null if none
     */
    public UUID getAlliancePartner() {
        return alliancePartner;
    }

    /**
     * Set the player's alliance partner
     *
     * @param partnerId UUID of alliance partner
     */
    public void setAlliancePartner(UUID partnerId) {
        this.alliancePartner = partnerId;
    }

    /**
     * Remove the player's alliance partner
     */
    public void removeAlliancePartner() {
        this.alliancePartner = null;
    }

    /**
     * Check if the player has an alliance partner
     *
     * @return True if player has alliance partner
     */
    public boolean hasAlliancePartner() {
        return alliancePartner != null;
    }

    /**
     * Get the player's max health
     *
     * @return Max health
     */
    public double getMaxHealth() {
        return maxHealth;
    }

    /**
     * Set the player's max health
     *
     * @param maxHealth New max health
     */
    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;

        // Update player's max health
        Player player = getPlayer();
        if (player != null) {
            player.setMaxHealth(maxHealth);
        }
    }

    /**
     * Get the last episode where a duo message was sent
     *
     * @return Episode number
     */
    public int getLastDuoMessageEpisode() {
        return lastDuoMessageEpisode;
    }

    /**
     * Set the last episode where a duo message was sent
     *
     * @param episode Episode number
     */
    public void setLastDuoMessageEpisode(int episode) {
        this.lastDuoMessageEpisode = episode;
    }

    /**
     * Increment mini-games won counter
     */
    public void incrementMiniGamesWon() {
        this.miniGamesWon++;
    }

    /**
     * Increment mini-games lost counter
     */
    public void incrementMiniGamesLost() {
        this.miniGamesLost++;
    }

    /**
     * Get number of mini-games won
     *
     * @return Mini-games won
     */
    public int getMiniGamesWon() {
        return miniGamesWon;
    }

    /**
     * Get number of mini-games lost
     *
     * @return Mini-games lost
     */
    public int getMiniGamesLost() {
        return miniGamesLost;
    }

    /**
     * Record hearts lost
     *
     * @param hearts Number of hearts lost
     */
    public void recordHeartsLost(int hearts) {
        this.heartsLost += hearts;
    }

    /**
     * Get total hearts lost
     *
     * @return Hearts lost
     */
    public int getHeartsLost() {
        return heartsLost;
    }

    /**
     * Check if an ability is on cooldown
     *
     * @param abilityName Name of the ability
     * @param cooldownSeconds Cooldown duration in seconds
     * @return True if ability is on cooldown
     */
    public boolean isAbilityOnCooldown(String abilityName, int cooldownSeconds) {
        if (!abilityCooldowns.containsKey(abilityName)) {
            return false;
        }

        long lastUsed = abilityCooldowns.get(abilityName);
        long currentTime = System.currentTimeMillis() / 1000;

        return (currentTime - lastUsed) < cooldownSeconds;
    }

    /**
     * Get remaining cooldown for an ability
     *
     * @param abilityName Name of the ability
     * @param cooldownSeconds Cooldown duration in seconds
     * @return Remaining cooldown in seconds, or 0 if not on cooldown
     */
    public int getRemainingCooldown(String abilityName, int cooldownSeconds) {
        if (!abilityCooldowns.containsKey(abilityName)) {
            return 0;
        }

        long lastUsed = abilityCooldowns.get(abilityName);
        long currentTime = System.currentTimeMillis() / 1000;
        long elapsed = currentTime - lastUsed;

        return elapsed >= cooldownSeconds ? 0 : (int)(cooldownSeconds - elapsed);
    }

    /**
     * Mark an ability as used
     *
     * @param abilityName Name of the ability
     */
    public void useAbility(String abilityName) {
        abilityCooldowns.put(abilityName, System.currentTimeMillis() / 1000);
        int uses = abilityUses.getOrDefault(abilityName, 0);
        abilityUses.put(abilityName, uses + 1);
    }

    /**
     * Get number of times an ability has been used
     *
     * @param abilityName Name of the ability
     * @return Number of uses
     */
    public int getAbilityUses(String abilityName) {
        return abilityUses.getOrDefault(abilityName, 0);
    }

    /**
     * Check if an ability has limited uses and if it's been exhausted
     *
     * @param abilityName Name of the ability
     * @param maxUses Maximum number of uses
     * @return True if ability has been exhausted
     */
    public boolean isAbilityExhausted(String abilityName, int maxUses) {
        int uses = getAbilityUses(abilityName);
        return uses >= maxUses;
    }

    /**
     * Reset cooldowns for all abilities
     */
    public void resetAllCooldowns() {
        abilityCooldowns.clear();
    }

    /**
     * Get the player's last location
     *
     * @return Last known location
     */
    public Location getLastLocation() {
        return lastLocation;
    }

    /**
     * Set the player's last location
     *
     * @param lastLocation Last known location
     */
    public void setLastLocation(Location lastLocation) {
        this.lastLocation = lastLocation;
    }

    /**
     * Get the player's last health
     *
     * @return Last known health
     */
    public double getLastHealth() {
        return lastHealth;
    }

    /**
     * Set the player's last health
     *
     * @param lastHealth Last known health
     */
    public void setLastHealth(double lastHealth) {
        this.lastHealth = lastHealth;
    }
}