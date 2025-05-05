package be.thespattt.ngnl.game;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.event.custom.PhaseChangeEvent;
import be.thespattt.ngnl.game.episode.EpisodeManager;
import be.thespattt.ngnl.game.scoreboard.NGNLScoreboardManager;
import be.thespattt.ngnl.game.scoreboard.NGNLScoreboardManager;
import be.thespattt.ngnl.game.world.WorldManager;
import be.thespattt.ngnl.game.world.WorldType;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Core game class that handles the game state and flow
 */
public class NGNLGame {

    private final NoGameNoLife plugin;

    // Game state
    private GameState gameState;
    private int remainingPlayersForArena;
    private boolean pledgesActive;

    // Managers
    private final EpisodeManager episodeManager;
    private final NGNLScoreboardManager scoreboardManager;

    // Player tracking
    private final List<UUID> alivePlayers;
    private final List<UUID> eliminatedPlayers;

    private final Map<UUID, MiniGameType> lastMiniGameLostBy = new HashMap<>();
    private final Map<UUID, UUID> scheduledMiniGames = new HashMap<>();
    private final Map<String, MiniGameType> scheduledMiniGameTypes = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public NGNLGame(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.gameState = GameState.WAITING;
        this.alivePlayers = new ArrayList<>();
        this.eliminatedPlayers = new ArrayList<>();
        this.pledgesActive = true;

        // Initialize managers
        this.episodeManager = new EpisodeManager(plugin);
        this.scoreboardManager = new NGNLScoreboardManager(plugin);

        // Load config values
        loadConfigValues();
    }

    /**
     * Load configuration values
     */
    private void loadConfigValues() {
        // Load from config
        this.remainingPlayersForArena = plugin.getConfigManager().getGameConfig().getArenaPlayerThreshold();
    }

    /**
     * Start the game
     */
    public void startGame() {
        if (gameState != GameState.WAITING) {
            MessageUtil.broadcast("&cThe game is already running!");
            return;
        }

        // Change game state
        gameState = GameState.STARTING;

        // Initialize alive players
        initializePlayers();

        // Assign roles and factions
        plugin.getRoleManager().assignRoles();

        // Teleport players to starting positions
        teleportPlayersToMiningWorld();

        // Start episode timer
        episodeManager.startEpisodeTimer();

        // Update game state
        gameState = GameState.MINING_PHASE;

        // Broadcast game start
        MessageUtil.broadcast("&6&lNo Game No Life UHC has begun!");
        MessageUtil.broadcast("&eGood luck and remember: In this world, games decide everything!");
    }

    /**
     * End the game
     *
     * @param force Force end even if players remain
     */
    public void endGame(boolean force) {
        if (gameState == GameState.WAITING || gameState == GameState.ENDED) {
            return;
        }

        // Stop timers
        episodeManager.stopEpisodeTimer();

        // Determine winner if game wasn't force-ended
        if (!force && alivePlayers.size() == 1) {
            UUID winnerId = alivePlayers.get(0);
            NGNLPlayer winner = plugin.getPlayerManager().getNGNLPlayer(winnerId);

            if (winner != null) {
                String roleName = winner.getRole().getDisplayName();
                String playerName = Bukkit.getPlayer(winnerId) != null ?
                        Bukkit.getPlayer(winnerId).getName() : "Unknown";

                MessageUtil.broadcast("&6&l" + playerName + " has won the game as " + roleName + "!");
            }
        } else if (!force && alivePlayers.isEmpty()) {
            MessageUtil.broadcast("&6&lThe game has ended in a draw!");
        } else {
            MessageUtil.broadcast("&c&lThe game has been forcefully ended by an administrator.");
        }

        // Reset players
        resetPlayers();

        // Change game state
        gameState = GameState.ENDED;

        // Clean up
        cleanup();

        // Return to waiting state
        gameState = GameState.WAITING;
    }

    /**
     * Start the arena phase
     */
    public void startArenaPhase() {
        if (gameState != GameState.MINING_PHASE) {
            return;
        }

        // Fire event
        PhaseChangeEvent event = new PhaseChangeEvent(GameState.MINING_PHASE, GameState.ARENA_PHASE);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        // Broadcast phase change
        MessageUtil.broadcast("&c&lQualifications complete! Arena phase has begun!");
        MessageUtil.broadcast("&eAll Pledges are now void. The final battle begins!");

        // Deactivate pledges
        pledgesActive = false;

        // Teleport remaining players to arena
        teleportPlayersToArenaWorld();

        // Update roles for arena phase
        plugin.getRoleManager().activateArenaPhaseAbilities();

        // Update game state
        gameState = GameState.ARENA_PHASE;

        // Update scoreboard
        scoreboardManager.updateScoreboardsForAllPlayers();
    }

    /**
     * Initialize players for the game
     */
    private void initializePlayers() {
        alivePlayers.clear();
        eliminatedPlayers.clear();

        // Setup all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            // Add to alive players
            alivePlayers.add(player.getUniqueId());

            // Set up player data
            NGNLPlayer ngnlPlayer = new NGNLPlayer(player.getUniqueId());
            plugin.getPlayerManager().registerNGNLPlayer(ngnlPlayer);

            // Set up player state
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0);
        }
    }

    /**
     * Reset all players
     */
    private void resetPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Reset player state
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0);

            // Teleport to lobby
            player.teleport(plugin.getWorldManager().getSpawnLocation(WorldType.WAITING));
        }

        // Clear player data
        plugin.getPlayerManager().clearAllPlayers();
    }

    /**
     * Teleport players to the mining world
     */
    private void teleportPlayersToMiningWorld() {
        WorldManager worldManager = plugin.getWorldManager();

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.teleport(worldManager.getRandomSpawnLocation(WorldType.MINING));
            }
        }
    }

    /**
     * Teleport players to the arena world
     */
    private void teleportPlayersToArenaWorld() {
        WorldManager worldManager = plugin.getWorldManager();

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.teleport(worldManager.getRandomSpawnLocation(WorldType.ARENA));
            }
        }
    }

    /**
     * Eliminate a player from the game
     *
     * @param playerId UUID of the player to eliminate
     * @param killer UUID of the killer (can be null)
     */
    public void eliminatePlayer(UUID playerId, UUID killer) {
        if (!alivePlayers.contains(playerId)) {
            return;
        }

        // Remove from alive players
        alivePlayers.remove(playerId);
        eliminatedPlayers.add(playerId);

        // Get player objects
        Player player = Bukkit.getPlayer(playerId);
        Player killerPlayer = killer != null ? Bukkit.getPlayer(killer) : null;

        // Set player to spectator mode
        if (player != null) {
            player.setGameMode(GameMode.SPECTATOR);

            // Handle role-specific elimination logic
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
            if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
                ngnlPlayer.getRole().onDeath(killer);
            }
        }

        // Start mini-game if applicable
        if (killer != null && gameState == GameState.MINING_PHASE) {
            plugin.getMiniGameManager().startMiniGame(killer, playerId);
        }

        // Check if arena phase should start
        checkArenaPhase();

        // Check if game is over
        checkGameEnd();
    }

    /**
     * Check if arena phase should start
     */
    private void checkArenaPhase() {
        if (gameState == GameState.MINING_PHASE && alivePlayers.size() <= remainingPlayersForArena) {
            startArenaPhase();
        }
    }

    /**
     * Check if the game should end
     */
    private void checkGameEnd() {
        if (alivePlayers.size() <= 1) {
            endGame(false);
        }
    }

    /**
     * Clean up resources
     */
    private void cleanup() {
        // Any additional cleanup
        episodeManager.stopEpisodeTimer();
    }

    /**
     * Get the current game state
     *
     * @return Current GameState
     */
    public GameState getGameState() {
        return gameState;
    }

    /**
     * Check if player is alive
     *
     * @param playerId UUID of player to check
     * @return True if player is alive
     */
    public boolean isPlayerAlive(UUID playerId) {
        return alivePlayers.contains(playerId);
    }

    /**
     * Get the list of alive players
     *
     * @return List of alive player UUIDs
     */
    public List<UUID> getAlivePlayers() {
        return new ArrayList<>(alivePlayers);
    }

    /**
     * Get the list of eliminated players
     *
     * @return List of eliminated player UUIDs
     */
    public List<UUID> getEliminatedPlayers() {
        return new ArrayList<>(eliminatedPlayers);
    }

    /**
     * Check if pledges are active
     *
     * @return True if pledges are active
     */
    public boolean arePledgesActive() {
        return pledgesActive;
    }

    /**
     * Get the episode manager
     *
     * @return EpisodeManager
     */
    public EpisodeManager getEpisodeManager() {
        return episodeManager;
    }

    /**
     * Get the scoreboard manager
     *
     * @return ScoreboardManager
     */
    public NGNLScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    /**
     * Get the last mini-game a player lost on
     *
     * @param playerId UUID of the player
     * @return MiniGameType or null if not found
     */
    public MiniGameType getLastMiniGameLostBy(UUID playerId) {
        return lastMiniGameLostBy.get(playerId);
    }

    /**
     * Set the last mini-game a player lost on
     *
     * @param playerId UUID of the player
     * @param miniGameType Mini-game type
     */
    public void setLastMiniGameLostBy(UUID playerId, MiniGameType miniGameType) {
        lastMiniGameLostBy.put(playerId, miniGameType);
    }

    /**
     * Get the scheduled mini-game opponent for a player
     *
     * @param playerId UUID of the player
     * @return UUID of opponent or null if not found
     */
    public UUID getScheduledMiniGameOpponent(UUID playerId) {
        return scheduledMiniGames.get(playerId);
    }

    /**
     * Schedule a mini-game between two players
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     */
    public void scheduleMinigame(UUID player1Id, UUID player2Id) {
        scheduledMiniGames.put(player1Id, player2Id);
        scheduledMiniGames.put(player2Id, player1Id);
    }

    /**
     * Set the mini-game type for a scheduled mini-game
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     * @param miniGameType Mini-game type
     */
    public void setScheduledMiniGameType(UUID player1Id, UUID player2Id, MiniGameType miniGameType) {
        String gameId = player1Id.toString() + "-" + player2Id.toString();
        scheduledMiniGameTypes.put(gameId, miniGameType);
    }

    /**
     * Get the mini-game type for a scheduled mini-game
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     * @return MiniGameType or null if not found
     */
    public MiniGameType getScheduledMiniGameType(UUID player1Id, UUID player2Id) {
        String gameId = player1Id.toString() + "-" + player2Id.toString();
        return scheduledMiniGameTypes.get(gameId);
    }

    /**
     * Clear a scheduled mini-game
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     */
    public void clearScheduledMiniGame(UUID player1Id, UUID player2Id) {
        scheduledMiniGames.remove(player1Id);
        scheduledMiniGames.remove(player2Id);

        String gameId = player1Id.toString() + "-" + player2Id.toString();
        String reverseGameId = player2Id.toString() + "-" + player1Id.toString();

        scheduledMiniGameTypes.remove(gameId);
        scheduledMiniGameTypes.remove(reverseGameId);
    }
}