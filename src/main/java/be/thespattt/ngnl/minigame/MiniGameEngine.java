package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.event.custom.MiniGameEndEvent;
import be.thespattt.ngnl.event.custom.MiniGameStartEvent;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.minigame.games.*;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.duo.FeelRole;
import be.thespattt.ngnl.role.duo.KuramiRole;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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

        preparePlayersForMiniGame(killer, victim);

        MiniGameBase miniGame;
        boolean killerWonPvP = true;

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
            case SUMO:
                miniGame = new SumoMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case MEMORY_GAME:
                miniGame = new MemoryMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case FLOOR_IS_LAVA:
                miniGame = new FloorIsLavaMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case ANVIL_RAIN:
                miniGame = new AnvilRainMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case ORACLE_CARD:
                miniGame = new OracleCardMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case WORD_CHAIN_BATTLE:
                miniGame = new WordChainBattleMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case LOGICAL_DEDUCTION:
                miniGame = new LogicalDeductionMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            case DES_A_COUDRE:
                miniGame = new DesACoudreMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
            default:
                miniGame = new ChessMiniGame(plugin, killerId, victimId, killerWonPvP);
                break;
        }

        // Register the game
        activeMiniGames.put(gameId, miniGame);

        // Fire mini-game start event
        MiniGameStartEvent event = new MiniGameStartEvent(killerId, victimId, miniGameType, killerWonPvP);
        Bukkit.getPluginManager().callEvent(event);

        // Start the mini-game
        miniGame.startGame();

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

        MiniGameEndEvent endEvent = new MiniGameEndEvent(
                player1UUID, player2UUID, miniGame.getMiniGameType(), player1Winner, player1WonPvP);

        Bukkit.getPluginManager().callEvent(endEvent);

        handleMiniGameResults(player1UUID, player2UUID, player1Winner, player1WonPvP, miniGame.getMiniGameType());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            returnPlayerToGame(player1UUID);
            returnPlayerToGame(player2UUID);
        }, 100L);
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
        NGNLPlayer loserNGNLPlayer = plugin.getPlayerManager().getNGNLPlayer(loserId);
        Role loserRole = loserNGNLPlayer != null ? loserNGNLPlayer.getRole() : null;

        if (loserRole instanceof KuramiRole kuramiRole && kuramiRole.consumeExtraLife(miniGameType)) {
            MessageUtil.broadcast("&aKurami's special extra life negated the mini-game punishment.");
            plugin.getMiniGameStatsTracker().recordWin(winnerId, miniGameType);
            return;
        }

        if (loserRole instanceof FeelRole feelRole && feelRole.consumeExtraLife(miniGameType)) {
            MessageUtil.broadcast("&aFeel's special extra life negated the mini-game punishment.");
            plugin.getMiniGameStatsTracker().recordWin(winnerId, miniGameType);
            return;
        }

        if (recentHeartChange.contains(loserId)) {
            MessageUtil.logWarning("Skipping heart loss for " + loserId + " (already applied recently)");
            return;
        }

        recentHeartChange.add(loserId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentHeartChange.remove(loserId), 20L * 30); // 30s cooldown


        Player loser = Bukkit.getPlayer(loserId);
        if (loser != null) {
            double heartsToLose = winnerWonPvP ? 5.0 : 3;
            if (loserNGNLPlayer != null) {
                loserNGNLPlayer.recordHeartsLost((int) Math.ceil(heartsToLose));

                plugin.getGameManager().removePlayerHearts(loserId, heartsToLose);
            }

            MessageUtil.sendMessage(loser, "&cYou lost " + heartsToLose + " hearts!");
        }

        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null) {
            MessageUtil.sendMessage(winner, "&aYou won the mini-game!");
            if (plugin.getConfigManager().getGameConfig().isMiniGameBookReward()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> giveRandomRewardBook(winnerId), 5L);
            }
        }

        plugin.getGameManager().getGame().setLastMiniGameLostBy(loserId, miniGameType);

        plugin.getMiniGameStatsTracker().recordWin(winnerId, miniGameType);
        plugin.getMiniGameStatsTracker().recordLoss(loserId, miniGameType);

        if (loserRole instanceof KuramiRole kuramiRole) {
            kuramiRole.handleJointLossIfNeeded();
        } else if (loserRole instanceof FeelRole feelRole) {
            feelRole.handleJointLossIfNeeded();
        }
        // Broadcast result
        String winnerName = winner != null ? winner.getName() : "Unknown";
        String loserName = loser != null ? loser.getName() : "Unknown";

        MessageUtil.broadcast("&6Mini-game has ended: &e" + miniGameType.getDisplayName());
        MessageUtil.broadcast("&6Winner: &a" + winnerName + " &7| Loser: &c" + loserName);

        if (loser != null) {
            double maxHealth = loser.getMaxHealth();
            if (maxHealth <= 2.0) {
                MessageUtil.broadcast("&c" + loserName + " has been eliminated due to losing all hearts!");
                plugin.getGameManager().handlePlayerElimination(loserId, winnerId);
                loser.setGameMode(GameMode.SPECTATOR);
            }
        }
    }

    private void giveRandomRewardBook(UUID winnerId) {
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner == null || !winner.isOnline()) {
            return;
        }

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (!(book.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return;
        }

        Enchantment randomEnchant = getRandomEnchantment();
        int level = getWeightedRandomLevel(randomEnchant.getMaxLevel());

        meta.addStoredEnchant(randomEnchant, level, true);
        meta.setDisplayName("§6§lReward Book");
        meta.setLore(Arrays.asList(
                "§7Enchantment: §e" + getEnchantmentName(randomEnchant),
                "§7Level: §b" + level
        ));

        book.setItemMeta(meta);
        winner.getInventory().addItem(book);
        winner.sendMessage("§a§lYou received a reward for winning the minigame book!");
    }

    private Enchantment getRandomEnchantment() {
        List<Enchantment> enchantments = Arrays.asList(
                Enchantment.SHARPNESS,
                Enchantment.PROTECTION,
                Enchantment.POWER,
                Enchantment.EFFICIENCY,
                Enchantment.UNBREAKING,
                Enchantment.KNOCKBACK,
                Enchantment.LOOTING,
                Enchantment.FORTUNE,
                Enchantment.INFINITY,
                Enchantment.MENDING,
                Enchantment.FEATHER_FALLING,
                Enchantment.BLAST_PROTECTION,
                Enchantment.PROJECTILE_PROTECTION,
                Enchantment.RESPIRATION,
                Enchantment.AQUA_AFFINITY,
                Enchantment.THORNS,
                Enchantment.DEPTH_STRIDER,
                Enchantment.FROST_WALKER,
                Enchantment.PUNCH,
                Enchantment.LUCK_OF_THE_SEA
        );

        return enchantments.get(new Random().nextInt(enchantments.size()));
    }

    private int getWeightedRandomLevel(int maxLevel) {
        if (maxLevel == 1) return 1;

        Random random = new Random();
        int roll = random.nextInt(100);

        if (maxLevel == 2) {
            return (roll < 70) ? 1 : 2;
        }

        if (maxLevel == 3) {
            if (roll < 50) return 1;
            if (roll < 85) return 2;
            if (roll < 99) return 3;
            return 4;
        }

        if (maxLevel == 4) {
            if (roll < 40) return 1;
            if (roll < 70) return 2;
            if (roll < 90) return 3;
            if (roll < 99) return 4;
            return 5;
        }

        if (maxLevel >= 5) {
            if (roll < 35) return 1;
            if (roll < 60) return 2;
            if (roll < 80) return 3;
            if (roll < 93) return 4;
            if (roll < 99) return 5;
            return 6;
        }

        return random.nextInt(maxLevel) + 1;
    }

    private String getEnchantmentName(Enchantment enchant) {
        String name = enchant.getKey().getKey();
        return Arrays.stream(name.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(name);
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
            if (isMiniGameWorld(player.getWorld())) {
                return;
            }
            ngnlPlayer.setLastLocation(player.getLocation());
        }
    }

    private boolean isMiniGameWorld(World world) {
        World miniGameWorld = plugin.getWorldManager().getMinigameWorld();
        return world != null && miniGameWorld != null && world.getUID().equals(miniGameWorld.getUID());
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

        GameState gameState = plugin.getGameManager().getGameState();
        if (plugin.getGameManager().isPlayerAlive(playerId)) {
            Location returnLocation = ngnlPlayer.getLastLocation();
            if (returnLocation == null || returnLocation.getWorld() == null) {
                World fallbackWorld = gameState == GameState.ARENA_PHASE
                        ? plugin.getWorldManager().getArenaWorld()
                        : plugin.getWorldManager().getMiningWorld();
                returnLocation = fallbackWorld != null ? getRandomSafeLocation(fallbackWorld, 0, 200) : null;
            }

            if (returnLocation != null) {
                player.setGameMode(GameMode.SURVIVAL);
                player.setFallDistance(0);
                player.setFireTicks(0);
                player.teleport(returnLocation);
                MessageUtil.sendMessage(player, "&aYou have been returned to your previous position.");
            } else {
                MessageUtil.logError("Failed to find a return location for " + player.getName() + ".");
            }
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
