package be.thespattt.ngnl.player;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manager class for player data
 */
public class PlayerManager {

    private final NoGameNoLife plugin;

    // Map to track NGNLPlayer instances (UUID -> NGNLPlayer)
    private final Map<UUID, NGNLPlayer> ngnlPlayers = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public PlayerManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Get a player's NGNLPlayer instance
     *
     * @param playerId UUID of the player
     * @return NGNLPlayer instance or null if not found
     */
    public NGNLPlayer getNGNLPlayer(UUID playerId) {
        return ngnlPlayers.get(playerId);
    }

    /**
     * Register a new NGNLPlayer instance
     *
     * @param ngnlPlayer NGNLPlayer instance to register
     */
    public void registerNGNLPlayer(NGNLPlayer ngnlPlayer) {
        ngnlPlayers.put(ngnlPlayer.getPlayerId(), ngnlPlayer);
    }

    /**
     * Create and register a new NGNLPlayer instance
     *
     * @param playerId UUID of the player
     * @return Newly created NGNLPlayer instance
     */
    public NGNLPlayer createNGNLPlayer(UUID playerId) {
        NGNLPlayer ngnlPlayer = new NGNLPlayer(playerId);
        registerNGNLPlayer(ngnlPlayer);
        return ngnlPlayer;
    }

    /**
     * Get a player's NGNLPlayer instance, creating one if it doesn't exist
     *
     * @param playerId UUID of the player
     * @return NGNLPlayer instance
     */
    public NGNLPlayer getOrCreateNGNLPlayer(UUID playerId) {
        NGNLPlayer ngnlPlayer = getNGNLPlayer(playerId);

        if (ngnlPlayer == null) {
            ngnlPlayer = createNGNLPlayer(playerId);
        }

        return ngnlPlayer;
    }

    /**
     * Remove a player's NGNLPlayer instance
     *
     * @param playerId UUID of the player
     */
    public void removeNGNLPlayer(UUID playerId) {
        ngnlPlayers.remove(playerId);
    }

    /**
     * Get all NGNLPlayer instances
     *
     * @return Collection of all NGNLPlayer instances
     */
    public Collection<NGNLPlayer> getAllNGNLPlayers() {
        return ngnlPlayers.values();
    }

    /**
     * Clear all player data
     */
    public void clearAllPlayers() {
        ngnlPlayers.clear();
    }

    /**
     * Apply faction bonuses to players
     */
    public void applyFactionBonuses() {
        for (NGNLPlayer ngnlPlayer : ngnlPlayers.values()) {
            if (ngnlPlayer.getRole() == null || ngnlPlayer.getFaction() == null) {
                continue;
            }

            Player player = ngnlPlayer.getPlayer();
            if (player == null) {
                continue;
            }

            // Apply faction-specific bonuses
            switch (ngnlPlayer.getFaction()) {
                case IMANITY:
                    // Imanity has better negotiation/commerce
                    // This would be implemented through trade mechanics
                    break;

                case FLUGEL:
                    // Flügel has access to special enchantment zones
                    // This would be implemented through world generation
                    break;

                case WEREBEASTS:
                    // Werebeasts have enhanced enemy detection
                    // This would be implemented through a periodic task
                    break;

                case EX_MACHINA:
                    // Ex-Machina has more efficient equipment repair
                    // This would be implemented through repair mechanics
                    break;

                case ELVES:
                    // Elves have better enchantments and potions
                    // This would be implemented through enchanting/brewing events
                    break;

                case OLD_DEUS:
                    // Old Deus has resistance to environmental damage
                    // This is implemented in the damage event handler
                    break;

                case OTHER:
                    // Other factions have unique bonuses based on role
                    // This would be implemented in the role classes
                    break;
            }
        }
    }

    /**
     * Update player data from online players
     */
    public void updatePlayersFromOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            NGNLPlayer ngnlPlayer = getOrCreateNGNLPlayer(player.getUniqueId());

            // Update player data
            ngnlPlayer.setLastLocation(player.getLocation());
            ngnlPlayer.setLastHealth(player.getHealth());
        }
    }

    /**
     * Save player data (if persistent storage is implemented)
     */
    public void savePlayerData() {
        // This would save player data to a persistent storage
        // For now, it's a placeholder for future implementation
        MessageUtil.logInfo("Saving player data...");
    }

    /**
     * Load player data (if persistent storage is implemented)
     */
    public void loadPlayerData() {
        // This would load player data from a persistent storage
        // For now, it's a placeholder for future implementation
        MessageUtil.logInfo("Loading player data...");
    }
}