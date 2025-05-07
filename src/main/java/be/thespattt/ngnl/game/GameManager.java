package be.thespattt.ngnl.game;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manager class for the game, provides API for other parts of the plugin
 */
public class GameManager {

    private final NoGameNoLife plugin;
    private NGNLGame game;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public GameManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.game = new NGNLGame(plugin);
    }

    /**
     * Start the game
     *
     * @return True if the game started successfully
     */
    public boolean startGame() {
        // Check if enough players are online
        int minPlayers = plugin.getConfigManager().getGameConfig().getMinimumPlayers();
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            MessageUtil.broadcast("&cNot enough players to start the game! Minimum: " + minPlayers);
            return false;
        }
        long currentTime = System.currentTimeMillis();
        game.startGame();
        game.getEpisodeManager().setGameStartTime(currentTime);
        return true;
    }

    /**
     * End the game
     *
     * @param force Force end the game
     */
    public void endGame(boolean force) {
        game.endGame(force);
    }

    /**
     * Force start the arena phase
     *
     * @return True if arena phase started successfully
     */
    public boolean forceArenaPhase() {
        if (game.getGameState() != GameState.MINING_PHASE) {
            return false;
        }

        game.startArenaPhase();
        return true;
    }

    /**
     * Handle player death/elimination
     *
     * @param playerId UUID of the player
     * @param killerId UUID of the killer (can be null)
     */
    public void handlePlayerElimination(UUID playerId, UUID killerId) {
        if (!isGameRunning()) {
            return;
        }

        game.eliminatePlayer(playerId, killerId);
    }

    /**
     * Handle permanent heart loss
     *
     * @param playerId UUID of the player
     * @param hearts Number of hearts to remove
     */
    public void removePlayerHearts(UUID playerId, double hearts) {
        removePlayerHearts(playerId, hearts, null);
    }
    public void removePlayerHearts(UUID playerId, double hearts, UUID killerId) {
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (ngnlPlayer == null) return;
        double currentMaxHealth = ngnlPlayer.getMaxHealth();
        double newMaxHealth = Math.max(2.0, currentMaxHealth - (hearts * 2));
        ngnlPlayer.setMaxHealth(newMaxHealth);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        player.setMaxHealth(newMaxHealth);

        if (player.getHealth() > newMaxHealth) player.setHealth(newMaxHealth);

        if (newMaxHealth <= 0.0) handlePlayerElimination(playerId, killerId);
    }

    public boolean isGameRunning() {
        GameState state = game.getGameState();
        return state == GameState.MINING_PHASE || state == GameState.ARENA_PHASE;
    }

    /**
     * Check if player is alive in the game
     *
     * @param playerId UUID of the player
     * @return True if player is alive
     */
    public boolean isPlayerAlive(UUID playerId) {
        return game.isPlayerAlive(playerId);
    }

    /**
     * Get the current game state
     *
     * @return Current GameState
     */
    public GameState getGameState() {
        return game.getGameState();
    }

    /**
     * Get the NGNLGame instance
     *
     * @return NGNLGame instance
     */
    public NGNLGame getGame() {
        return game;
    }

    /**
     * Reset the game instance
     */
    public void resetGame() {
        if (isGameRunning()) {
            endGame(true);
        }

        this.game = new NGNLGame(plugin);
    }
}