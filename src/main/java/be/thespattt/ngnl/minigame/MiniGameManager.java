package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.event.custom.MiniGameEndEvent;
import be.thespattt.ngnl.event.custom.MiniGameStartEvent;
import be.thespattt.ngnl.minigame.games.RockPaperScissorsGame;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manager class for mini-games in the game
 */
public class MiniGameManager {

    private final NoGameNoLife plugin;

    // Map of active mini-games (player1Id-player2Id -> MiniGameInstance)
    private final Map<String, MiniGameInstance> activeMiniGames = new HashMap<>();

    // Map of scheduled mini-games (player UUID -> opponent UUID)
    private final Map<UUID, UUID> scheduledMiniGames = new HashMap<>();

    // Map of mini-game types (player1Id-player2Id -> MiniGameType)
    private final Map<String, MiniGameType> miniGameTypes = new HashMap<>();

    // Map of mini-game results (player UUID -> MiniGameResult)
    private final Map<UUID, MiniGameResult> miniGameResults = new HashMap<>();

    // Map to track PvP winners (loser UUID -> killer UUID)
    private final Map<UUID, UUID> pvpKillers = new HashMap<>();
    private RandomMiniGameSelector miniGameSelector;

    private int waitingRoomX = 0;
    private final int waitingRoomY = 72;
    private int waitingRoomZ = 0;
    private static final int ROOM_WIDTH = 12;
    private static final int  ROOM_HEIGHT = 5;
    private static final int  ROOM_DEPTH = 10;

    private final int STARTX = waitingRoomX - (ROOM_WIDTH / 2);
    private final int STARTY = waitingRoomY;
    private final int STARTZ = waitingRoomZ - (ROOM_DEPTH / 2);

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public MiniGameManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.miniGameSelector = new RandomMiniGameSelector(plugin);
    }

    /**
     * Load available mini-games
     */
    public void loadMiniGames() {
        // Nothing to load, mini-games are defined in MiniGameType enum
        MessageUtil.logInfo("Loaded " + MiniGameType.values().length + " mini-games");
    }

    /**
     * Start a mini-game between two players
     *
     * @param winnerUUID UUID of the PvP winner
     * @param loserUUID UUID of the PvP loser
     * @return True if mini-game started successfully
     */
    public boolean startMiniGame(UUID winnerUUID, UUID loserUUID) {
        Player winner = Bukkit.getPlayer(winnerUUID);
        Player loser = Bukkit.getPlayer(loserUUID);

        if (winner == null || loser == null) {
            return false;
        }

        // Record PvP winner/loser
        pvpKillers.put(loserUUID, winnerUUID);

        // Schedule the mini-game
        scheduleGame(winnerUUID, loserUUID);

        // Notify players
        MessageUtil.sendMessage(winner, "&6A mini-game will start soon. You defeated " + loser.getName() + " in PvP.");
        MessageUtil.sendMessage(loser, "&6A mini-game will start soon. You were defeated by " + winner.getName() + " in PvP.");

        // Ask the winner to choose a mini-game
        showMiniGameSelectionMenu(winner, true);

        return true;
    }

    /**
     * Schedule a mini-game between two players
     *
     * @param player1UUID UUID of player 1
     * @param player2UUID UUID of player 2
     */
    private void scheduleGame(UUID player1UUID, UUID player2UUID) {
        // Store scheduled game
        scheduledMiniGames.put(player1UUID, player2UUID);
        scheduledMiniGames.put(player2UUID, player1UUID);

        // Default to a random mini-game
        String gameId = player1UUID.toString() + "-" + player2UUID.toString();
        miniGameTypes.put(gameId, MiniGameType.getRandom());
    }

    /**
     * Start a scheduled mini-game
     *
     * @param player1UUID UUID of player 1
     * @param player2UUID UUID of player 2
     * @return True if mini-game started successfully
     */
    public boolean startScheduledMiniGame(UUID player1UUID, UUID player2UUID) {
        // Check if game is scheduled
        if (!scheduledMiniGames.containsKey(player1UUID) ||
                !scheduledMiniGames.get(player1UUID).equals(player2UUID)) {
            return false;
        }

        Player player1 = Bukkit.getPlayer(player1UUID);
        Player player2 = Bukkit.getPlayer(player2UUID);

        if (player1 == null || player2 == null) {
            return false;
        }

        // Get mini-game type
        String gameId = player1UUID.toString() + "-" + player2UUID.toString();
        MiniGameType miniGameType = miniGameTypes.getOrDefault(gameId, MiniGameType.getRandom());

        // Clear scheduled game
        scheduledMiniGames.remove(player1UUID);
        scheduledMiniGames.remove(player2UUID);

        // Determine who won the PvP
        boolean player1WonPvP = wasPlayerKilledBy(player2UUID, player1UUID);

        // Create mini-game instance
        MiniGameInstance instance = createMiniGameInstance(player1UUID, player2UUID, miniGameType, player1WonPvP);

        if (instance == null) {
            return false;
        }

        // Store active mini-game
        activeMiniGames.put(gameId, instance);

        // Fire mini-game start event
        MiniGameStartEvent event = new MiniGameStartEvent(player1UUID, player2UUID, miniGameType, player1WonPvP);
        Bukkit.getPluginManager().callEvent(event);

        // Start the mini-game
        instance.start();

        // Broadcast mini-game start
        MessageUtil.broadcast("&6A mini-game has started: &e" + miniGameType.getDisplayName());
        MessageUtil.broadcast("&6" + player1.getName() + " vs " + player2.getName());

        return true;
    }

    /**
     * Check if a player was killed by another player
     *
     * @param victimUUID UUID of the victim
     * @param killerUUID UUID of the potential killer
     * @return True if the victim was killed by the killer
     */
    private boolean wasPlayerKilledBy(UUID victimUUID, UUID killerUUID) {
        // Check if the victim was killed by the killer
        UUID recordedKiller = pvpKillers.get(victimUUID);
        return recordedKiller != null && recordedKiller.equals(killerUUID);
    }

    /**
     * Create a mini-game instance based on type
     *
     * @param player1UUID UUID of player 1
     * @param player2UUID UUID of player 2
     * @param miniGameType Type of mini-game
     * @param player1WonPvP True if player 1 won the PvP
     * @return MiniGameInstance or null if creation failed
     */
    private MiniGameInstance createMiniGameInstance(UUID player1UUID, UUID player2UUID,
                                                    MiniGameType miniGameType, boolean player1WonPvP) {
        Player player1 = Bukkit.getPlayer(player1UUID);
        Player player2 = Bukkit.getPlayer(player2UUID);

        if (player1 == null || player2 == null) {
            return null;
        }

        switch (miniGameType) {
            case MATERIALIZATION_SHIRITORI:
            case LOGICAL_DEDUCTION:
            case MENTAL_CHESS:
            case MEMORY_GAME:
            case WORD_CHAIN_BATTLE:
                // These would have their own implementations
                MessageUtil.broadcast("&cWARNING: " + miniGameType.getDisplayName() + " is not fully implemented.");
                MessageUtil.broadcast("&cFalling back to Rock-Paper-Scissors.");
                // Fall through to default for now

            default:
                // Default to rock-paper-scissors for now
                return new MiniGameInstance(plugin, player1UUID, player2UUID, miniGameType, player1WonPvP) {
                    private RockPaperScissorsGame rpsGame;
                    private BukkitTask timeoutTask;

                    @Override
                    public void start() {
                        // Create RPS game
                        rpsGame = new RockPaperScissorsGame(plugin, player1, player2, "hearts");

                        // Register game with command
                        plugin.getCommandManager().getRpsCommand().registerGame(
                                player1UUID.toString() + "-" + player2UUID.toString(), rpsGame);

                        // Start the game
                        rpsGame.start();

                        // Set timeout
                        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            endGame(false, null); // No winner if timed out
                        }, 20L * 60); // 60 seconds timeout
                    }

                    @Override
                    public void endGame(boolean completed, UUID winnerUUID) {
                        if (timeoutTask != null) {
                            timeoutTask.cancel();
                        }

                        // Cancel the game if not completed
                        if (!completed) {
                            MessageUtil.broadcast("&cThe mini-game has timed out!");

                            // Default to PvP winner as the winner
                            winnerUUID = player1WonPvP ? player1UUID : player2UUID;
                        }

                        // Determine winner
                        boolean player1Winner = player1UUID.equals(winnerUUID);

                        // Store result
                        MiniGameResult result = new MiniGameResult(
                                player1UUID, player2UUID, miniGameType, player1Winner, player1WonPvP);

                        miniGameResults.put(player1UUID, result);
                        miniGameResults.put(player2UUID, result);

                        // Fire mini-game end event
                        MiniGameEndEvent endEvent = new MiniGameEndEvent(
                                player1UUID, player2UUID, miniGameType, player1Winner, player1WonPvP);

                        Bukkit.getPluginManager().callEvent(endEvent);

                        // Remove from active games
                        activeMiniGames.remove(player1UUID.toString() + "-" + player2UUID.toString());
                    }
                };
        }
    }

    /**
     * Show mini-game selection menu to a player
     *
     * @param player Player to show menu to
     * @param pvpWinner True if the player won the PvP
     */
    private void showMiniGameSelectionMenu(Player player, boolean pvpWinner) {
        // This would create an inventory GUI for mini-game selection
        // For now, just let the player know they can choose a mini-game
        if (pvpWinner) {
            MessageUtil.sendMessage(player, "&6You can choose a mini-game with &e/minigame select <type>&6.");
            MessageUtil.sendMessage(player, "&6Available mini-games: &eSPEED_BEDWARS, SPLEEF, TNT_RUN, PARKOUR, BLOC_PARTY, SUMO");
        } else {
            MessageUtil.sendMessage(player, "&6The winner will choose a mini-game.");
        }
    }

    /**
     * Set the mini-game type for scheduled game
     *
     * @param player1UUID UUID of player 1
     * @param player2UUID UUID of player 2
     * @param miniGameType Type of mini-game
     * @return True if type was set successfully
     */
    public boolean setMiniGameType(UUID player1UUID, UUID player2UUID, MiniGameType miniGameType) {
        // Check if game is scheduled
        if (!scheduledMiniGames.containsKey(player1UUID) ||
                !scheduledMiniGames.get(player1UUID).equals(player2UUID)) {
            return false;
        }

        // Check if mini-game is enabled
        if (!plugin.getConfigManager().getGameConfig().isMiniGameEnabled(miniGameType.name())) {
            return false;
        }

        // Set mini-game type
        String gameId = player1UUID.toString() + "-" + player2UUID.toString();
        miniGameTypes.put(gameId, miniGameType);

        return true;
    }

    /**
     * Get a player's most recent mini-game result
     *
     * @param playerUUID UUID of the player
     * @return MiniGameResult or null if not found
     */
    public MiniGameResult getPlayerMiniGameResult(UUID playerUUID) {
        return miniGameResults.get(playerUUID);
    }

    /**
     * Check if a player has an active mini-game
     *
     * @param playerUUID UUID of the player
     * @return True if player has an active mini-game
     */
    public boolean hasActiveMiniGame(UUID playerUUID) {
        for (String gameId : activeMiniGames.keySet()) {
            if (gameId.contains(playerUUID.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * End all active mini-games
     */
    public void endAllMiniGames() {
        for (MiniGameInstance game : activeMiniGames.values()) {
            game.endGame(false, null);
        }
        activeMiniGames.clear();
        scheduledMiniGames.clear();
        miniGameTypes.clear();
    }

    /**
     * Class representing a mini-game result
     */
    public class MiniGameResult {
        private final UUID player1UUID;
        private final UUID player2UUID;
        private final MiniGameType miniGameType;
        private final boolean player1Winner;
        private final boolean player1WonPvP;

        /**
         * Constructor
         *
         * @param player1UUID UUID of player 1
         * @param player2UUID UUID of player 2
         * @param miniGameType Type of mini-game
         * @param player1Winner True if player 1 won the mini-game
         * @param player1WonPvP True if player 1 won the PvP
         */
        public MiniGameResult(UUID player1UUID, UUID player2UUID, MiniGameType miniGameType,
                              boolean player1Winner, boolean player1WonPvP) {
            this.player1UUID = player1UUID;
            this.player2UUID = player2UUID;
            this.miniGameType = miniGameType;
            this.player1Winner = player1Winner;
            this.player1WonPvP = player1WonPvP;
        }

        /**
         * Get UUID of player 1
         *
         * @return UUID of player 1
         */
        public UUID getPlayer1UUID() {
            return player1UUID;
        }

        /**
         * Get UUID of player 2
         *
         * @return UUID of player 2
         */
        public UUID getPlayer2UUID() {
            return player2UUID;
        }

        /**
         * Get mini-game type
         *
         * @return MiniGameType
         */
        public MiniGameType getMiniGameType() {
            return miniGameType;
        }

        /**
         * Check if player 1 won the mini-game
         *
         * @return True if player 1 won
         */
        public boolean isPlayer1Winner() {
            return player1Winner;
        }

        /**
         * Check if player 1 won the PvP
         *
         * @return True if player 1 won the PvP
         */
        public boolean isPlayer1WonPvP() {
            return player1WonPvP;
        }

        /**
         * Get the winner UUID
         *
         * @return UUID of the winner
         */
        public UUID getWinnerUUID() {
            return player1Winner ? player1UUID : player2UUID;
        }

        /**
         * Get the loser UUID
         *
         * @return UUID of the loser
         */
        public UUID getLoserUUID() {
            return player1Winner ? player2UUID : player1UUID;
        }
    }

    /**
     * Abstract class for mini-game instances
     */
    public abstract class MiniGameInstance {
        protected final NoGameNoLife plugin;
        protected final UUID player1UUID;
        protected final UUID player2UUID;
        protected final MiniGameType miniGameType;
        protected final boolean player1WonPvP;

        /**
         * Constructor
         *
         * @param plugin Plugin instance
         * @param player1UUID UUID of player 1
         * @param player2UUID UUID of player 2
         * @param miniGameType Type of mini-game
         * @param player1WonPvP True if player 1 won the PvP
         */
        public MiniGameInstance(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID,
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
        public abstract void start();

        /**
         * End the mini-game
         *
         * @param completed True if the game completed normally
         * @param winnerUUID UUID of the winner (null if no winner)
         */
        public abstract void endGame(boolean completed, UUID winnerUUID);
    }

    public void startMiniGameDuel(Player killer, Player victim) {
        teleportToMiniGameRoom(killer, victim);

        plugin.getMiniGameSessionManager().registerPendingSession(killer.getUniqueId(), victim.getUniqueId());

        MessageUtil.sendMessage(killer, "&eChoose a mini-game to challenge &c" + victim.getName());
        openMiniGameSelectionGUI(killer, victim);
    }

    public void openMiniGameSelectionGUI(Player killer, Player victim) {
        plugin.getMiniGameSessionManager().registerPendingSession(killer.getUniqueId(), victim.getUniqueId());
        miniGameSelector.openMiniGameSelectionGUI(killer, victim);
    }


    public void teleportToMiniGameRoom(Player killer, Player victim) {

        World world = Bukkit.getWorld("ngnl_minigame");
        if (world == null) {
            MessageUtil.sendMessage(killer, "&c[Error] Mini-game world not found.");
            return;
        }

        Location center = new Location(plugin.getWorldManager().getMinigameWorld(), waitingRoomX, waitingRoomY, waitingRoomZ);
        preloadChunksAndThen(plugin.getWorldManager().getMinigameWorld(), center, 32, () -> {
            setupWaitingRoom();
            teleportPlayersToWaitingRoom(killer, victim);
        });
        setupWaitingRoom();

        teleportPlayersToWaitingRoom(killer, victim);
    }

    private void setupWaitingRoom() {
        World gameWorld = plugin.getWorldManager().getMinigameWorld();
        if (gameWorld == null) {
            return;
        }
        clearAreaWithAir();
        buildArenaFloor();
        buildArenaCeiling();
        buildArenaWalls();
    }
    private void clearAreaWithAir(){
        World gameWorld = plugin.getWorldManager().getMinigameWorld();
        for (int x = 0; x < ROOM_WIDTH + 2; x++) {
            for (int y = 0; y < ROOM_HEIGHT + 2; y++) {
                for (int z = 0; z < ROOM_DEPTH + 2; z++) {
                    Block block = gameWorld.getBlockAt(STARTX - 1 + x, STARTY - 1 + y, STARTZ - 1 + z);
                    block.setType(Material.AIR);
                }
            }
        }
    }
    private void buildArenaWalls(){
        World gameWorld = plugin.getWorldManager().getMinigameWorld();
        for (int x = 0; x < ROOM_WIDTH; x++) {
            for (int y = 1; y < ROOM_HEIGHT - 1; y++) {
                // North wall
                Block northBlock = gameWorld.getBlockAt(STARTX + x, STARTY + y, STARTZ);
                northBlock.setType(Material.PURPLE_STAINED_GLASS);

                // South wall
                Block southBlock = gameWorld.getBlockAt(STARTX + x, STARTY + y, STARTZ + ROOM_DEPTH - 1);
                southBlock.setType(Material.PURPLE_STAINED_GLASS);
            }
        }
        for (int z = 0; z < ROOM_DEPTH; z++) {
            for (int y = 1; y < ROOM_HEIGHT - 1; y++) {
                Block eastBlock = gameWorld.getBlockAt(STARTX, STARTY + y, STARTZ + z);
                eastBlock.setType(Material.PURPLE_STAINED_GLASS);

                Block westBlock = gameWorld.getBlockAt(STARTX + ROOM_WIDTH - 1, STARTX + y, STARTZ + z);
                westBlock.setType(Material.PURPLE_STAINED_GLASS);
            }
        }
    }
    private void buildArenaCeiling(){
        World gameWorld = plugin.getWorldManager().getMinigameWorld();
        for (int x = 0; x < ROOM_WIDTH; x++) {
            for (int z = 0; z < ROOM_DEPTH; z++) {
                Block block = gameWorld.getBlockAt(STARTX + x, STARTY + ROOM_HEIGHT - 1, STARTZ + z);
                block.setType(Material.PURPLE_STAINED_GLASS);
            }
        }
    }
    private void buildArenaFloor(){
        World gameWorld = plugin.getWorldManager().getMinigameWorld();
        for (int x = 0; x < ROOM_WIDTH; x++) {
            for (int z = 0; z < ROOM_DEPTH; z++) {
                Block block = gameWorld.getBlockAt(STARTX + x, STARTY, STARTZ + z);
                block.setType(Material.PURPLE_STAINED_GLASS);
            }
        }
    }
    private void teleportPlayersToWaitingRoom(Player player1, Player player2) {

        World miniGameWorld = plugin.getWorldManager().getMinigameWorld();
        if (miniGameWorld == null) {
            miniGameWorld = Bukkit.getWorlds().get(0);
        }

        if (player1 != null) {
            Location whiteLocation = new Location(miniGameWorld,
                    waitingRoomX + 2, waitingRoomY + 1, waitingRoomZ + 2, 45, 0); // White position
            player1.teleport(whiteLocation);
        }

        if (player2 != null) {
            Location blackLocation = new Location(miniGameWorld,
                    waitingRoomX - 2, waitingRoomY + 1, waitingRoomZ - 2, 225, 0); // Black position
            player2.teleport(blackLocation);
        }
    }

    public void preloadChunksAndThen(World world, Location center, int radius, Runnable onLoaded) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);

        Set<Chunk> chunksToLoad = loadingChunks(center, world, chunkRadius);

        new BukkitRunnable() {
            @Override
            public void run() {
                if(chunksToLoad.stream().anyMatch(chunk -> !chunk.isLoaded())) return;
                cancel();
                onLoaded.run();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }
    private Set<Chunk> loadingChunks(Location center, World world, int chunkRadius){
        Set<Chunk> chunksToLoad = new HashSet<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(center.getBlockX() / 16 + dx, center.getBlockZ() / 16 + dz);
                chunksToLoad.add(chunk);
                chunk.load(true);
            }
        }
        return chunksToLoad;
    }
}