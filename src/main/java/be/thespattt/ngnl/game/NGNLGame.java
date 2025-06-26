package be.thespattt.ngnl.game;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.arena.ArenaBorderShrinkTask;
import be.thespattt.ngnl.arena.ArenaCombatManager;
import be.thespattt.ngnl.arena.ArenaWorldHandler;
import be.thespattt.ngnl.event.custom.PhaseChangeEvent;
import be.thespattt.ngnl.game.episode.EpisodeManager;
import be.thespattt.ngnl.game.scoreboard.NGNLScoreboardManager;
import be.thespattt.ngnl.game.scoreboard.NGNLScoreboardManager;
import be.thespattt.ngnl.game.world.WorldManager;
import be.thespattt.ngnl.game.world.WorldType;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
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

    private ArenaCombatManager arenaCombatManager;
    private ArenaWorldHandler arenaWorldHandler;
    private ArenaBorderShrinkTask borderShrinkTask;

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

        this.arenaCombatManager = new ArenaCombatManager(plugin);
        this.arenaWorldHandler = new ArenaWorldHandler(plugin);

        // Load config values
        loadConfigValues();
    }

    /**
     * Load configuration values
     */
    private void loadConfigValues() {
        // Load from config
        this.remainingPlayersForArena = plugin.getConfigManager().getGameConfig().getArenaPlayerThreshold();
        if (plugin.getConfigManager().getGameConfig().getArenaPlayerThreshold() == 0) this.remainingPlayersForArena = 2;
        else this.remainingPlayersForArena = plugin.getConfigManager().getGameConfig().getArenaPlayerThreshold();
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

        // Teleport players to starting positions
        teleportPlayersToMiningWorld();

        // Start episode timer
        episodeManager.startEpisodeTimer();

        // Update game state
        gameState = GameState.MINING_PHASE;

        // Broadcast game start
        MessageUtil.broadcast("&fNo Game No Life UHC has begun!");
        MessageUtil.broadcast("&fGood luck and remember: In this world, &5games &fdecide everything!");
    }

    /**
     * End the game
     *
     * @param force Force end even if players remain
     */
    public void endGame(boolean force) {
        if (gameState == GameState.WAITING || gameState == GameState.ENDED) return;

        episodeManager.stopEpisodeTimer();
        MessageUtil.broadcast("&5&m                    ");

        List<UUID> allPlayers = plugin.getPlayerManager().getAllNGNLPlayers().stream()
                .map(NGNLPlayer::getPlayerId)
                .toList();

        // Gagnants (si pas force)
        if (!force && alivePlayers.size() == 1) {
            UUID winnerId = alivePlayers.get(0);
            Player winnerPlayer = Bukkit.getPlayer(winnerId);
            String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";
            String winnerRole = plugin.getPlayerManager().getNGNLPlayer(winnerId).getRole().getDisplayName();

            MessageUtil.broadcast("&fWinner: &e" + winnerName);
            MessageUtil.broadcast("&7   (" + winnerRole + ")");

        } else if (!force && alivePlayers.size() == 2) {
            UUID p1 = alivePlayers.get(0);
            UUID p2 = alivePlayers.get(1);

            NGNLPlayer ngnl1 = plugin.getPlayerManager().getNGNLPlayer(p1);
            NGNLPlayer ngnl2 = plugin.getPlayerManager().getNGNLPlayer(p2);
            Role r1 = ngnl1.getRole();
            Role r2 = ngnl2.getRole();

            boolean areDuo = r1 != null && r1.isDuo() && p2.equals(r1.getPartnerUUID());
            boolean areAlliance = ngnl1.hasAlliancePartner() && p2.equals(ngnl1.getAlliancePartner());

            if (areDuo || areAlliance) {
                String name1 = Bukkit.getPlayer(p1) != null ? Bukkit.getPlayer(p1).getName() : "Player1";
                String name2 = Bukkit.getPlayer(p2) != null ? Bukkit.getPlayer(p2).getName() : "Player2";
                String role1 = r1 != null ? r1.getDisplayName() : "Unknown";
                String role2 = r2 != null ? r2.getDisplayName() : "Unknown";

                MessageUtil.broadcast("&fWinners: &e" + name1 + "&f and &e" + name2);
                MessageUtil.broadcast("&5   (" + role1 + " / " + role2 + ")");
            } else {
                MessageUtil.broadcast("&fThe game has ended in a &edraw!");
            }

        } else if (!force && alivePlayers.isEmpty()) {
            MessageUtil.broadcast("&fThe game has ended in a &edraw!");
        } else if (force) {
            MessageUtil.broadcast("&fThe game has been forcefully ended by an &3administrator.");
        }

        // Afficher TOUS les joueurs avec leur rôle, en mettant les gagnants en vert
        MessageUtil.broadcast("&fPlayers and Roles:");
        for (UUID playerId : allPlayers) {
            NGNLPlayer ngnl = plugin.getPlayerManager().getNGNLPlayer(playerId);
            Player player = Bukkit.getPlayer(playerId);
            String name = player != null ? player.getName() : "Unknown";
            String role = ngnl.getRole() != null ? ngnl.getRole().getDisplayName() : "No Role";

            if (alivePlayers.contains(playerId)) {
                MessageUtil.broadcast("&2✔ &f" + name + " &5(" + role + ")");
            } else {
                MessageUtil.broadcast("&4✘ &f" + name + " &5(" + role + ")");
            }
        }

        MessageUtil.broadcast("&5&m                    ");

        resetPlayers();
        gameState = GameState.ENDED;
        cleanup();
        gameState = GameState.WAITING;
    }

    /**
     * Start the arena phase
     */
    public void startArenaPhase() {
        if (gameState != GameState.MINING_PHASE) {
            MessageUtil.logWarning("Cannot start arena phase from state: " + gameState);
            return;
        }

        MessageUtil.logInfo("Starting arena phase...");

        // Fire event
        PhaseChangeEvent event = new PhaseChangeEvent(GameState.MINING_PHASE, GameState.ARENA_PHASE);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            MessageUtil.logWarning("Arena phase start was cancelled by event");
            return;
        }

        // Initialiser le monde arène
        MessageUtil.logInfo("Initializing arena world...");
        arenaWorldHandler.initializeArenaWorld();

        // Nettoyer les mobs
        arenaWorldHandler.clearHostileMobs();

        // Activer le système de combat
        MessageUtil.logInfo("Activating arena combat...");
        arenaCombatManager.activateArenaCombat();

        // Broadcast phase change
        MessageUtil.broadcast("&5&m═══════════════════════════════════════════════");
        MessageUtil.broadcast("&5&l            LOVE FIGHT COMMENCÉ");
        MessageUtil.broadcast("&f Les qualifications sont terminées !");
        MessageUtil.broadcast("&f Bienvenue dans l'arène finale !");
        MessageUtil.broadcast("&c Les armes traditionnelles sont désactivées !");
        MessageUtil.broadcast("&6 Utilisez votre Love Gun pour combattre !");
        MessageUtil.broadcast("&5&m═══════════════════════════════════════════════");

        // Deactivate pledges
        pledgesActive = false;

        // Téléporter les joueurs vers l'arène
        List<Player> alivePlayers = new ArrayList<>();
        for (UUID playerId : this.alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                alivePlayers.add(player);
                MessageUtil.logInfo("Adding player to arena: " + player.getName());
            }
        }

        MessageUtil.logInfo("Teleporting " + alivePlayers.size() + " players to arena");
        arenaWorldHandler.teleportPlayersToArena(alivePlayers);

        // Donner l'équipement d'arène avec un délai
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : alivePlayers) {
                if (player.isOnline()) {
                    arenaCombatManager.giveArenaEquipment(player);
                    MessageUtil.logInfo("Gave arena equipment to: " + player.getName());
                }
            }
        }, 40L); // 2 secondes après téléportation

        // Update roles for arena phase
        plugin.getRoleManager().activateArenaPhaseAbilities();

        // Démarrer la tâche de rétrécissement de bordure avec un délai
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (borderShrinkTask != null) {
                borderShrinkTask.cancel();
            }
            borderShrinkTask = new ArenaBorderShrinkTask(plugin, arenaWorldHandler.getArenaWorld());
            borderShrinkTask.runTaskTimer(plugin, 20L, 20L); // Chaque seconde
            MessageUtil.logInfo("Started border shrink task");
        }, 100L); // 5 secondes après téléportation

        // Update game state
        gameState = GameState.ARENA_PHASE;
        MessageUtil.logInfo("Arena phase started successfully!");

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
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(player.getMaxHealth());
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

        alivePlayers.remove(playerId);
        eliminatedPlayers.add(playerId);

        Player player = Bukkit.getPlayer(playerId);
        Player killerPlayer = killer != null ? Bukkit.getPlayer(killer) : null;

        if (player != null) {
            player.setGameMode(GameMode.SPECTATOR);

            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
            if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
                ngnlPlayer.getRole().onDeath(killer);
            }
        }

        //    if (killer != null && gameState == GameState.MINING_PHASE) {
        //        plugin.getMiniGameManager().startMiniGame(killer, playerId);
        //    }

        MessageUtil.logInfo("Player eliminated. Alive players: " + alivePlayers.size() + "/" + remainingPlayersForArena);
        MessageUtil.logInfo("Current game state: " + gameState);

        checkArenaPhase();

        checkGameEnd();
    }

    /**
     * Check if arena phase should start
     */
    private void checkArenaPhase() {
        MessageUtil.logInfo("Checking arena phase: " + alivePlayers.size() + " players alive, threshold: " + remainingPlayersForArena);

        if (gameState == GameState.MINING_PHASE && alivePlayers.size() <= remainingPlayersForArena) {
            MessageUtil.logInfo("Arena phase triggered!");
            startArenaPhase();
        }
    }

    /**
     * Check if the game should end
     */
    private void checkGameEnd() {
        if (alivePlayers.size() <= 1) {
            endGame(false);
            return;
        }

        if (alivePlayers.size() == 2) {
            UUID p1 = alivePlayers.get(0);
            UUID p2 = alivePlayers.get(1);

            NGNLPlayer ngnl1 = plugin.getPlayerManager().getNGNLPlayer(p1);
            NGNLPlayer ngnl2 = plugin.getPlayerManager().getNGNLPlayer(p2);

            if (ngnl1 != null && ngnl2 != null) {
                Role r1 = ngnl1.getRole();
                Role r2 = ngnl2.getRole();

                boolean areDuo = r1 != null && r1.isDuo() && p2.equals(r1.getPartnerUUID());
                boolean areAlliance = ngnl1.hasAlliancePartner() && p2.equals(ngnl1.getAlliancePartner());

                if (areDuo || areAlliance) {
                    endGame(false);
                }
            }
        }
    }

    /**
     * Clean up resources
     */
    private void cleanup() {
        // Cleanup existant
        episodeManager.stopEpisodeTimer();

        // Nouveau nettoyage arena
        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
            borderShrinkTask = null;
            MessageUtil.logInfo("Cancelled border shrink task");
        }

        if (arenaCombatManager != null) {
            arenaCombatManager.deactivateArenaCombat();
            MessageUtil.logInfo("Deactivated arena combat");
        }
    }

    public void forceArenaPhaseForTesting() {
        MessageUtil.logInfo("FORCING ARENA PHASE FOR TESTING");
        startArenaPhase();
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