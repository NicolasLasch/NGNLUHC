package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.event.custom.MiniGameEndEvent;
import be.thespattt.ngnl.event.custom.MiniGameStartEvent;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.minigame.games.*;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Engine for running mini-games between players
 */
public class MiniGameEngine {
    private final NoGameNoLife plugin;
    private final Map<String, MiniGameBase> activeMiniGames = new HashMap<>();
    private final Map<UUID, BukkitTask> returnTasks = new HashMap<>();
    private final Set<UUID> recentHeartChange = new HashSet<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public MiniGameEngine(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Start a mini-game between two players
     *
     * @param miniGameType Type of mini-game
     * @param killer The player who won in PVP
     * @param victim The player who lost in PVP
     * @return True if mini-game started successfully
     */
    public boolean startGame(MiniGameType miniGameType, Player killer, Player victim) {
        if (killer == null || victim == null) {
            return false;
        }

        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();

        // Create a unique game ID
        String gameId = killerId.toString() + "-" + victimId.toString();

        // Check if a game is already active with either player
        if (isPlayerInMiniGame(killerId) || isPlayerInMiniGame(victimId)) {
            MessageUtil.sendMessage(killer, "&cOne of you is already in a mini-game!");
            return false;
        }

        // Prepare players for mini-game
        preparePlayersForMiniGame(killer, victim);

        // Create mini-game instance
        MiniGameBase miniGame;
        boolean killerWonPvP = true; // The killer always won the PVP

        switch (miniGameType) {
            case MENTAL_CHESS:
                miniGame = new ChessMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case SPLEEF:
                miniGame = new SpleefMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case TNT_RUN:
                miniGame = new TNTRunMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case BLOC_PARTY:
                miniGame = new BlockPartyMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case SPLEGG:
                miniGame = new SpleggMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case PARKOUR:
                miniGame = new ParkourMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            // Add more cases for additional minigames
            default:
                // Fallback to a default game if type not recognized
                miniGame = new SpleefMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
        }

        // Register the game
        activeMiniGames.put(gameId, miniGame);

        // Fire mini-game start event
        MiniGameStartEvent event = new MiniGameStartEvent(killerId, victimId, miniGameType, killerWonPvP);
        Bukkit.getPluginManager().callEvent(event);

        // Start the mini-game
        miniGame.startGame();

        // Broadcast mini-game start
        MessageUtil.broadcast("&6A mini-game has started: &e" + miniGameType.getDisplayName());
        MessageUtil.broadcast("&6" + killer.getName() + " vs " + victim.getName());

        return true;
    }

    /**
     * Check if a player is currently in a mini-game
     *
     * @param playerId UUID of the player
     * @return True if the player is in a mini-game
     */
    public boolean isPlayerInMiniGame(UUID playerId) {
        for (MiniGameBase game : activeMiniGames.values()) {
            if (game.hasPlayer(playerId)) {
                return true;
            }
        }
        return false;
    }

    public MiniGameBase getPlayerMiniGame(UUID playerId) {
        for (MiniGameBase game : activeMiniGames.values()) {
            if (game.hasPlayer(playerId)) {
                return game;
            }
        }
        return null;
    }

    /**
     * End a mini-game and handle the result
     *
     * @param gameId ID of the mini-game
     * @param winnerUUID UUID of the winner (null if no winner)
     */
    public void endMiniGame(String gameId, UUID winnerUUID) {
        MiniGameBase miniGame = activeMiniGames.get(gameId);
        if (miniGame == null) {
            return;
        }

        // Remove the game
        activeMiniGames.remove(gameId);

        // Get players
        UUID player1UUID = miniGame.getPlayer1UUID();
        UUID player2UUID = miniGame.getPlayer2UUID();

        boolean player1Winner = player1UUID.equals(winnerUUID);
        boolean player1WonPvP = miniGame.didPlayer1WinPvP();

        // Fire mini-game end event
        MiniGameEndEvent endEvent = new MiniGameEndEvent(
                player1UUID, player2UUID, miniGame.getMiniGameType(), player1Winner, player1WonPvP);

        Bukkit.getPluginManager().callEvent(endEvent);

        // Handle mini-game results
        handleMiniGameResults(player1UUID, player2UUID, player1Winner, player1WonPvP, miniGame.getMiniGameType());

        // Return players to the game world
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            returnPlayerToGame(player1UUID);
            returnPlayerToGame(player2UUID);
        }, 60L); // 3 seconds delay
    }

    /**
     * Handle mini-game results (heart loss, etc.)
     *
     * @param player1UUID UUID of player 1
     * @param player2UUID UUID of player 2
     * @param player1Winner True if player 1 won
     * @param player1WonPvP True if player 1 won the PvP
     * @param miniGameType Type of mini-game
     */
    private void handleMiniGameResults(UUID player1UUID, UUID player2UUID, boolean player1Winner,
                                       boolean player1WonPvP, MiniGameType miniGameType) {
        UUID winnerId = player1Winner ? player1UUID : player2UUID;
        UUID loserId = player1Winner ? player2UUID : player1UUID;

        boolean winnerWonPvP = (player1Winner == player1WonPvP);

        if (recentHeartChange.contains(loserId)) {
            MessageUtil.logWarning("Skipping heart loss for " + loserId + " (already applied recently)");
            return;
        }

        // Début du traitement
        recentHeartChange.add(loserId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentHeartChange.remove(loserId), 20L * 30); // 30s cooldown


        // Apply heart loss based on rules
        Player loser = Bukkit.getPlayer(loserId);
        if (loser != null) {
            double heartsToLose = winnerWonPvP ? 5.0 : 3;

            NGNLPlayer loserNGNLPlayer = plugin.getPlayerManager().getNGNLPlayer(loserId);
            if (loserNGNLPlayer != null) {
                loserNGNLPlayer.recordHeartsLost((int) Math.ceil(heartsToLose));

                plugin.getGameManager().removePlayerHearts(loserId, heartsToLose);
            }

            // Notify player
            MessageUtil.sendMessage(loser, "&cYou lost " + heartsToLose + " hearts!");
        }

        // Give reward to winner
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null) {
            MessageUtil.sendMessage(winner, "&aYou won the mini-game!");
            // Here you could add additional rewards
            if (plugin.getConfigManager().getGameConfig().isMiniGameBookReward()) {
                //giveRandomRewardBook(winner);
            }
        }

        // Record which mini-game the loser lost on
        plugin.getGameManager().getGame().setLastMiniGameLostBy(loserId, miniGameType);

        // Broadcast result
        String winnerName = winner != null ? winner.getName() : "Unknown";
        String loserName = loser != null ? loser.getName() : "Unknown";

        MessageUtil.broadcast("&6Mini-game has ended: &e" + miniGameType.getDisplayName());
        MessageUtil.broadcast("&6Winner: &a" + winnerName + " &7| Loser: &c" + loserName);

        // Check if loser died from health loss - but do this check AFTER heart loss is applied
        if (loser != null) {
            double maxHealth = loser.getMaxHealth();
            if (maxHealth <= 2.0) {
                // Player died from heart loss
                MessageUtil.broadcast("&c" + loserName + " has been eliminated due to losing all hearts!");
                plugin.getGameManager().handlePlayerElimination(loserId, winnerId);
                loser.setGameMode(GameMode.SPECTATOR);
            }
        }
    }

    /**
     * Prepare players for a mini-game
     *
     * @param player1 First player
     * @param player2 Second player
     */
    private void preparePlayersForMiniGame(Player player1, Player player2) {
        // Store player locations for later return
        storePlayerLocation(player1);
        storePlayerLocation(player2);

        // Ensure players are in survival mode
        player1.setGameMode(GameMode.SURVIVAL);
        player2.setGameMode(GameMode.SURVIVAL);

        // Ensure players have full health for the mini-game
        player1.setHealth(player1.getMaxHealth());
        player2.setHealth(player2.getMaxHealth());

        // Cancel any pending return tasks
        cancelReturnTask(player1.getUniqueId());
        cancelReturnTask(player2.getUniqueId());
    }

    /**
     * Store a player's location before teleporting to mini-game
     *
     * @param player Player to store location for
     */
    private void storePlayerLocation(Player player) {
        // Get NGNL player
        be.thespattt.ngnl.player.NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer != null) {
            ngnlPlayer.setLastLocation(player.getLocation());
        }
    }

    /**
     * Return a player to the game world after a mini-game
     *
     * @param playerId UUID of the player
     */
    private void returnPlayerToGame(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        // Get NGNL player
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (ngnlPlayer == null) return;

        // Choisir le monde en fonction de la phase
        World world = Bukkit.getWorld("ngnl_waiting");
        GameState gameState = plugin.getGameManager().getGameState();
        if (gameState == GameState.MINING_PHASE) {
            world = plugin.getWorldManager().getMiningWorld();
        } else if (gameState == GameState.ARENA_PHASE) {
            world = plugin.getWorldManager().getArenaWorld();
        }

        if (world == null) {
            MessageUtil.logWarning("No suitable world found to return player to game.");
            return;
        }

        // Générer une position aléatoire autour du centre (rayon 200)
        Location randomLocation = getRandomSafeLocation(world, 0, 200);
        if (randomLocation != null) {
            player.teleport(randomLocation);
            MessageUtil.sendMessage(player, "&aYou have been returned to the game world.");
        } else {
            MessageUtil.logError("Failed to find safe location for teleportation.");
        }

        // Définir le bon mode de jeu
        if (plugin.getGameManager().isPlayerAlive(playerId)) {
            player.setGameMode(GameMode.SURVIVAL);
        } else {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    /**
     * Génère une position aléatoire sécurisée au sol dans un rayon donné
     */
    private Location getRandomSafeLocation(World world, int center, int radius) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = center + (int) (Math.random() * radius * 2) - radius;
            int z = center + (int) (Math.random() * radius * 2) - radius;
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            if (world.getBlockAt(loc).getType().isAir()) {
                return loc;
            }
        }
        return null;
    }

    /**
     * Cancel a pending return task for a player
     *
     * @param playerId UUID of the player
     */
    private void cancelReturnTask(UUID playerId) {
        BukkitTask task = returnTasks.get(playerId);
        if (task != null) {
            task.cancel();
            returnTasks.remove(playerId);
        }
    }

    /**
     * Clean up all mini-games
     */
    public void cleanup() {
        for (Map.Entry<String, MiniGameBase> entry : new HashMap<>(activeMiniGames).entrySet()) {
            endMiniGame(entry.getKey(), null);
        }
        activeMiniGames.clear();

        for (BukkitTask task : returnTasks.values()) {
            task.cancel();
        }
        returnTasks.clear();
    }
}