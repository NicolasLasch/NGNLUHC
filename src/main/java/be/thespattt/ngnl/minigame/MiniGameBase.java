package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Base class for all mini-games
 */
public abstract class MiniGameBase {

    protected final NoGameNoLife plugin;
    protected final UUID player1UUID;
    protected final UUID player2UUID;
    protected final MiniGameType miniGameType;
    protected final boolean player1WonPvP;

    // Room coordinates
    protected Location player1Location;
    protected Location player2Location;
    protected World gameWorld;

    protected BukkitTask timeoutTask;
    protected boolean isActive = false;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player1UUID UUID of player 1
     * @param player2UUID UUID of player 2
     * @param miniGameType Type of mini-game
     * @param player1WonPvP True if player 1 won the PvP
     */
    public MiniGameBase(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID,
                        MiniGameType miniGameType, boolean player1WonPvP) {
        this.plugin = plugin;
        this.player1UUID = player1UUID;
        this.player2UUID = player2UUID;
        this.miniGameType = miniGameType;
        this.player1WonPvP = player1WonPvP;
    }

    /**
     * Start the mini-game
     */
    public void startGame() {
        isActive = true;

        // Create the game room and teleport players
        if (!createGameRoom()) {
            // If room creation fails, end the game
            MessageUtil.logError("Failed to create game room for " + miniGameType.name());
            endGame(false, player1WonPvP ? player1UUID : player2UUID);
            return;
        }

        // Set up timeout task (2 minutes)
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isActive) {
                timeoutGame();
            }
        }, 20L * 300); // 120 seconds = 2 minutes

        // Implement specific game start logic in subclasses
        onGameStart();
    }

    /**
     * Create the game-specific room
     * This method should be overridden by each game type to create its specific room
     * @return True if room creation and teleportation were successful
     */
    protected boolean createGameRoom() {
        // Default implementation creates a standard 5x5x5 wooden room
        // This is used if the specific game doesn't override this method

        // Get or create the minigame world
        gameWorld = getOrCreateMinigameWorld();
        if (gameWorld == null) {
            return false;
        }

        // Each minigame should use a different area in the world
        // By default, we'll use the enum ordinal * 100 for the X coordinate
        int baseX = miniGameType.ordinal() * 100;
        int baseY = 70; // Standard Y height
        int baseZ = 0;  // Standard Z

        // Clear the area (7x7x7)
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = gameWorld.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    block.setType(Material.AIR);
                }
            }
        }

        // Create a wooden floor (5x5)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = gameWorld.getBlockAt(baseX + x, baseY, baseZ + z);
                block.setType(Material.OAK_PLANKS);
            }
        }

        // Create walls
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                // North and south walls
                gameWorld.getBlockAt(baseX + x, baseY + y, baseZ - 2).setType(Material.OAK_PLANKS);
                gameWorld.getBlockAt(baseX + x, baseY + y, baseZ + 2).setType(Material.OAK_PLANKS);
            }
        }

        for (int z = -2; z <= 2; z++) {
            for (int y = 1; y <= 3; y++) {
                // East and west walls
                gameWorld.getBlockAt(baseX - 2, baseY + y, baseZ + z).setType(Material.OAK_PLANKS);
                gameWorld.getBlockAt(baseX + 2, baseY + y, baseZ + z).setType(Material.OAK_PLANKS);
            }
        }

        // Create a ceiling
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = gameWorld.getBlockAt(baseX + x, baseY + 4, baseZ + z);
                block.setType(Material.OAK_PLANKS);
            }
        }

        // Create a fence barrier in the middle
        for (int z = -1; z <= 1; z++) {
            gameWorld.getBlockAt(baseX, baseY + 1, baseZ + z).setType(Material.OAK_FENCE);
            gameWorld.getBlockAt(baseX, baseY + 2, baseZ + z).setType(Material.OAK_FENCE);
        }

        // Set player locations
        player1Location = new Location(gameWorld, baseX - 1.5, baseY + 1, baseZ, 90, 0);
        player2Location = new Location(gameWorld, baseX + 1.5, baseY + 1, baseZ, -90, 0);

        // Teleport players
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            player1.teleport(player1Location);
            MessageUtil.sendMessage(player1, "&aYou've been teleported to the minigame room!");
        }

        if (player2 != null) {
            player2.teleport(player2Location);
            MessageUtil.sendMessage(player2, "&aYou've been teleported to the minigame room!");
        }

        return true;
    }

    /**
     * Get or create the minigame world
     * @return The minigame world or null if it couldn't be created
     */
    protected World getOrCreateMinigameWorld() {
        String worldName = "ngnl_minigame";
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            try {
                world = plugin.getWorldManager().getMinigameWorld();
            } catch (Exception e) {
                MessageUtil.logError("Failed to create mini-game world", e);
                return null;
            }
        }

        return world;
    }

    /**
     * Called after the game room is created and players are teleported
     * Implement specific game start logic in this method
     */
    protected abstract void onGameStart();

    /**
     * End the mini-game with a winner
     *
     * @param winnerUUID UUID of the winner
     */
    public void endGame(UUID winnerUUID) {
        if (!isActive) {
            return;
        }

        isActive = false;

        // Cancel timeout task
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        // Get the game ID
        String gameId = player1UUID.toString() + "-" + player2UUID.toString();

        // End the game in the engine
        plugin.getMiniGameEngine().endMiniGame(gameId, winnerUUID);
    }

    /**
     * End the game with completion status and winner
     *
     * @param completed Whether the game completed normally
     * @param winnerUUID UUID of the winner
     */
    public void endGame(boolean completed, UUID winnerUUID) {
        endGame(winnerUUID);
    }

    /**
     * Handle game timeout (no winner)
     */
    public void timeoutGame() {
        if (!isActive) {
            return;
        }

        isActive = false;

        // Default to the PvP winner as the mini-game winner
        UUID winnerUUID = player1WonPvP ? player1UUID : player2UUID;

        // Notify players
        Player winner = Bukkit.getPlayer(winnerUUID);
        Player loser = Bukkit.getPlayer(winnerUUID.equals(player1UUID) ? player2UUID : player1UUID);

        if (winner != null) {
            MessageUtil.sendMessage(winner, "&aThe game has timed out. You win by default!");
        }

        if (loser != null) {
            MessageUtil.sendMessage(loser, "&cThe game has timed out. You lose by default!");
        }

        // End the game
        endGame(winnerUUID);
    }

    /**
     * Check if a player is in this mini-game
     *
     * @param playerId UUID of the player
     * @return True if the player is in this game
     */
    public boolean hasPlayer(UUID playerId) {
        return playerId.equals(player1UUID) || playerId.equals(player2UUID);
    }

    /**
     * Get player 1's UUID
     *
     * @return Player 1's UUID
     */
    public UUID getPlayer1UUID() {
        return player1UUID;
    }

    /**
     * Get player 2's UUID
     *
     * @return Player 2's UUID
     */
    public UUID getPlayer2UUID() {
        return player2UUID;
    }

    /**
     * Get player 1
     *
     * @return Player 1 or null if offline
     */
    public Player getPlayer1() {
        return Bukkit.getPlayer(player1UUID);
    }

    /**
     * Get player 2
     *
     * @return Player 2 or null if offline
     */
    public Player getPlayer2() {
        return Bukkit.getPlayer(player2UUID);
    }

    /**
     * Get the mini-game type
     *
     * @return MiniGameType
     */
    public MiniGameType getMiniGameType() {
        return miniGameType;
    }

    /**
     * Check if player 1 won the PvP
     *
     * @return True if player 1 won the PvP
     */
    public boolean didPlayer1WinPvP() {
        return player1WonPvP;
    }

    /**
     * Check if the mini-game is active
     *
     * @return True if active
     */
    public boolean isActive() {
        return isActive;
    }
}