package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// Working
// TODO : Fix the word check system because atm all worked :p
public class WordChainBattleMiniGame extends MiniGameBase implements Listener {

    private enum GamePhase {
        SETUP,
        COUNTDOWN,
        PLAYING,
        ENDED
    }

    private GamePhase currentPhase = GamePhase.SETUP;
    private UUID currentPlayerTurn;
    private int turnTimeRemaining = 30;
    private BukkitTask turnTimerTask;
    private BukkitTask gameTimerTask;

    private final Map<UUID, Integer> playerErrors = new HashMap<>();
    private final Map<UUID, Integer> playerWordsCount = new HashMap<>();
    private final Set<String> usedWords = new HashSet<>();
    private final List<String> wordChain = new ArrayList<>();

    private Location arenaCenter;
    private boolean arenaReady = false;
    private String currentLastLetter = "";
    private long gameStartTime;
    private final int GAME_DURATION = 3600; // 3 minutes in ticks
    private final int TURN_TIME = 30; // 30 seconds per turn
    private final int MAX_ERRORS = 3;
    private Map<String, Boolean> validWordsCache = new ConcurrentHashMap<>();

    // Basic English dictionary for validation
    private final Set<String> validWords = new HashSet<>();

    public WordChainBattleMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.WORD_CHAIN_BATTLE, player1WonPvP);

        playerErrors.put(player1UUID, 0);
        playerErrors.put(player2UUID, 0);
        playerWordsCount.put(player1UUID, 0);
        playerWordsCount.put(player2UUID, 0);

        initializeDictionary();
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        setupArenaAndStart();
    }

    private void initializeDictionary() {
        // We don't need to load a huge dictionary anymore!
        // Just initialize the cache for API responses
        validWordsCache = new ConcurrentHashMap<>();

        MessageUtil.sendMessage(getPlayer1(), "§aDictionary: Using live API validation");
        MessageUtil.sendMessage(getPlayer2(), "§aDictionary: Using live API validation");
    }

    private void setupArenaAndStart() {
        World world = getOrCreateMinigameWorld();
        if (world == null) {
            MessageUtil.sendMessage(getPlayer1(), "§cError: Could not create minigame world!");
            MessageUtil.sendMessage(getPlayer2(), "§cError: Could not create minigame world!");
            return;
        }

        generateArenaLocation(world);
        loadChunksSync(world, arenaCenter, 15);
        completeArenaSetupAndStart();
    }

    private void generateArenaLocation(World world) {
        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100;
        arenaCenter = new Location(world, x, y, z);
    }

    private void loadChunksSync(World world, Location center, int radius) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(center.getBlockX() / 16 + dx, center.getBlockZ() / 16 + dz);
                chunk.load(true);
            }
        }
    }

    private void completeArenaSetupAndStart() {
        buildArena();
        teleportPlayers();
        arenaReady = true;

        MessageUtil.sendMessage(getPlayer1(), "§aArena ready! Starting Word Chain Battle...");
        MessageUtil.sendMessage(getPlayer2(), "§aArena ready! Starting Word Chain Battle...");

        beginGame();
    }

    private void buildArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        clearArenaArea(world);
        buildWordBattleArena(world);
    }

    private void clearArenaArea(World world) {
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -5; y <= 10; y++) {
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildWordBattleArena(World world) {
        // Build circular platform
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= 8) {
                    // Create a book-themed arena
                    Material floorMaterial;
                    if (distance <= 2) {
                        floorMaterial = Material.BOOKSHELF;
                    } else if ((x + z) % 2 == 0) {
                        floorMaterial = Material.OAK_PLANKS;
                    } else {
                        floorMaterial = Material.SPRUCE_PLANKS;
                    }

                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY(), arenaCenter.getBlockZ() + z).setType(floorMaterial);

                    // Foundation
                    for (int y = -5; y <= -1; y++) {
                        world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.BEDROCK);
                    }
                }
            }
        }

        // Add word-themed decorations
        buildWordDecorations(world);
    }

    private void buildWordDecorations(World world) {
        // Add lecterns at corners for the scholarly atmosphere
        int[] positions = {-6, 6};
        for (int x : positions) {
            for (int z : positions) {
                world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + 1, arenaCenter.getBlockZ() + z).setType(Material.LECTERN);
                // Add glowstone for lighting
                world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + 3, arenaCenter.getBlockZ() + z).setType(Material.GLOWSTONE);
            }
        }

        // Central podium
        world.getBlockAt(arenaCenter.getBlockX(), arenaCenter.getBlockY() + 1, arenaCenter.getBlockZ()).setType(Material.ENCHANTING_TABLE);

        // Add barriers around the arena
        for (int angle = 0; angle < 360; angle += 20) {
            double radians = Math.toRadians(angle);
            int x = (int) (arenaCenter.getX() + 10 * Math.cos(radians));
            int z = (int) (arenaCenter.getZ() + 10 * Math.sin(radians));

            for (int y = 0; y <= 5; y++) {
                world.getBlockAt(x, arenaCenter.getBlockY() + y, z).setType(Material.BARRIER);
            }
        }
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            Location spawn1 = arenaCenter.clone().add(-4, 1, 0);
            spawn1.setYaw(90);
            player1.teleport(spawn1);
        }

        if (player2 != null) {
            Location spawn2 = arenaCenter.clone().add(4, 1, 0);
            spawn2.setYaw(270);
            player2.teleport(spawn2);
        }
    }

    private void beginGame() {
        currentPhase = GamePhase.COUNTDOWN;

        sendGameInstructions();
        startCountdown();
    }

    private void sendGameInstructions() {
        String instructions =
                "§6=== Word Chain Battle Instructions ===\n" +
                        "§fCreate a word chain where each word starts with the last letter of the previous word!\n" +
                        "§fYou have §e30 seconds §fper turn to type a valid word.\n" +
                        "§fMake §c3 errors §fand you're eliminated!\n" +
                        "§fWords must be at least §e3 letters §flong and cannot be repeated.\n" +
                        "§fExample: CAT → TIGER → RABBIT → TABLE\n" +
                        "§fType your word in chat when it's your turn!";

        MessageUtil.sendMessage(getPlayer1(), instructions);
        MessageUtil.sendMessage(getPlayer2(), instructions);
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    MessageUtil.sendMessage(getPlayer1(), "§eGame starting in " + countdown + "...");
                    MessageUtil.sendMessage(getPlayer2(), "§eGame starting in " + countdown + "...");

                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    countdown--;
                } else {
                    MessageUtil.sendMessage(getPlayer1(), "§aSTART! Begin the word chain!");
                    MessageUtil.sendMessage(getPlayer2(), "§aSTART! Begin the word chain!");

                    playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    startActiveGame();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startActiveGame() {
        currentPhase = GamePhase.PLAYING;
        gameStartTime = System.currentTimeMillis();

        // Player 1 starts
        currentPlayerTurn = player1UUID;

        // Start with a random word
        String startWord = getRandomStartWord();
        wordChain.add(startWord);
        usedWords.add(startWord.toLowerCase());
        currentLastLetter = startWord.substring(startWord.length() - 1).toLowerCase();

        MessageUtil.sendMessage(getPlayer1(), "§6Starting word: §e" + startWord.toUpperCase());
        MessageUtil.sendMessage(getPlayer2(), "§6Starting word: §e" + startWord.toUpperCase());
        MessageUtil.sendMessage(getPlayer1(), "§aYour turn! Type a word starting with §e'" + currentLastLetter.toUpperCase() + "'");
        MessageUtil.sendMessage(getPlayer2(), "§7Wait for your turn...");

        startTurnTimer();
        startGameTimer();
    }

    private String getRandomStartWord() {
        String[] startWords = {"WORD", "GAME", "BATTLE", "MAGIC", "POWER", "LIGHT", "WATER", "FIRE"};
        return startWords[ThreadLocalRandom.current().nextInt(startWords.length)];
    }

    private void startTurnTimer() {
        turnTimeRemaining = TURN_TIME;

        if (turnTimerTask != null) {
            turnTimerTask.cancel();
        }

        turnTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                turnTimeRemaining--;

                if (turnTimeRemaining <= 0) {
                    handleTimeOut();
                    this.cancel();
                } else if (turnTimeRemaining <= 5) {
                    Player currentPlayer = Bukkit.getPlayer(currentPlayerTurn);
                    if (currentPlayer != null) {
                        MessageUtil.sendMessage(currentPlayer, "§c" + turnTimeRemaining + " seconds left!");
                        currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 2.0f);
                    }
                } else if (turnTimeRemaining == 15) {
                    Player currentPlayer = Bukkit.getPlayer(currentPlayerTurn);
                    if (currentPlayer != null) {
                        MessageUtil.sendMessage(currentPlayer, "§e15 seconds remaining!");
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startGameTimer() {
        gameTimerTask = new BukkitRunnable() {
            int timeLeft = GAME_DURATION / 20;

            @Override
            public void run() {
                if (currentPhase != GamePhase.PLAYING) {
                    this.cancel();
                    return;
                }

                timeLeft--;

                if (timeLeft <= 0) {
                    this.cancel();
                    endGameByTime();
                } else if (timeLeft % 30 == 0 || timeLeft <= 10) {
                    MessageUtil.sendMessage(getPlayer1(), "§eGame time remaining: " + timeLeft + " seconds");
                    MessageUtil.sendMessage(getPlayer2(), "§eGame time remaining: " + timeLeft + " seconds");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void handleTimeOut() {
        Player currentPlayer = Bukkit.getPlayer(currentPlayerTurn);
        UUID playerUUID = currentPlayerTurn;

        // Add error for timeout
        int errors = playerErrors.get(playerUUID) + 1;
        playerErrors.put(playerUUID, errors);

        if (currentPlayer != null) {
            MessageUtil.sendMessage(currentPlayer, "§cTime's up! Error " + errors + "/" + MAX_ERRORS);
            currentPlayer.playSound(currentPlayer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
        }

        // Check if player is eliminated
        if (errors >= MAX_ERRORS) {
            eliminatePlayer(playerUUID);
        } else {
            switchTurns();
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (currentPhase != GamePhase.PLAYING) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!isParticipant(player)) return;
        if (!playerUUID.equals(currentPlayerTurn)) return;

        event.setCancelled(true); // Prevent chat from showing normally

        String word = event.getMessage().trim().toLowerCase();

        // Process the word on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> processPlayerWord(playerUUID, word));
    }

    private void processPlayerWord(UUID playerUUID, String word) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return;

        // Use async validation instead of sync
        validateWord(playerUUID, word.toLowerCase());
    }

    private void validateWord(UUID playerUUID, String word) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return;

        // Basic validations first (no API needed)
        if (word.length() < 3) {
            handleWordError(playerUUID, word, "Word must be at least 3 letters long!");
            return;
        }

        if (!word.startsWith(currentLastLetter)) {
            handleWordError(playerUUID, word, "Word must start with '" + currentLastLetter.toUpperCase() + "'!");
            return;
        }

        if (usedWords.contains(word)) {
            handleWordError(playerUUID, word, "Word already used!");
            return;
        }

        if (!word.matches("[a-zàâäéèêëïîôöùûüÿç]+")) {
            handleWordError(playerUUID, word, "Word must contain only letters!");
            return;
        }

        // Check cache first
        if (validWordsCache.containsKey(word)) {
            if (validWordsCache.get(word)) {
                acceptWord(playerUUID, word);
            } else {
                handleWordError(playerUUID, word, "Invalid word! Not in dictionary.");
            }
            return;
        }

        // Show "checking..." message
        MessageUtil.sendMessage(player, "§eChecking word: " + word.toUpperCase() + "...");

        // Check API asynchronously
        checkWordWithAPI(word).thenAccept(isValid -> {
            // This runs on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Cache the result
                validWordsCache.put(word, isValid);

                if (isValid) {
                    acceptWord(playerUUID, word);
                } else {
                    handleWordError(playerUUID, word, "Invalid word! Not in dictionary.");
                }
            });
        }).exceptionally(throwable -> {
            // Handle API error
            Bukkit.getScheduler().runTask(plugin, () -> {
                MessageUtil.sendMessage(player, "§cAPI Error! Word accepted by default.");
                acceptWord(playerUUID, word);
            });
            return null;
        });
    }

    private CompletableFuture<Boolean> checkWordWithAPI(String word) {
        return CompletableFuture.supplyAsync(() -> {
            try {
//                if (checkEnglishWord(word)) {
//                    return true;
//                }

                if (checkFrenchWord(word)) {
                    return true;
                }

                return false;

            } catch (Exception e) {
                plugin.getLogger().warning("Dictionary API error for word '" + word + "': " + e.getMessage());
                return true;
            }
        });
    }

    private boolean checkEnglishWord(String word) throws Exception {
        String url = "https://api.dictionaryapi.dev/api/v2/entries/en/" + word;

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(3000); // 3 second timeout
        connection.setReadTimeout(3000);
        connection.setRequestProperty("User-Agent", "MinecraftWordGame/1.0");

        int responseCode = connection.getResponseCode();

        if (responseCode == 200) {
            // Word found in English dictionary
            return true;
        } else if (responseCode == 404) {
            // Word not found
            return false;
        } else {
            throw new Exception("API returned status: " + responseCode);
        }
    }

    private boolean checkFrenchWord(String word) throws Exception {
        String url = "https://www.cnrtl.fr/definition/" + word;

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestProperty("User-Agent", "MinecraftWordGame/1.0");

        int responseCode = connection.getResponseCode();

        if (responseCode == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String responseText = response.toString();

            if (responseText.contains("Aucun résultat") ||
                    responseText.contains("Page introuvable") ||
                    responseText.contains("Cette forme est introuvable !") ||
                    responseText.contains("Aucune entrée") ||
                    responseText.contains("n'a pas été trouvé")) {
                return false;
            }

            return responseText.contains("class=\"definition\"") ||
                    responseText.contains("class=\"tlf_cdefinition\"") ||
                    responseText.contains("class=\"zone-contenu\"");
        }

        return false;
    }

    private void handleWordError(UUID playerUUID, String word, String errorMessage) {
        Player player = Bukkit.getPlayer(playerUUID);
        int errors = playerErrors.get(playerUUID) + 1;
        playerErrors.put(playerUUID, errors);

        if (player != null) {
            MessageUtil.sendMessage(player, "§c✗ Invalid: " + word.toUpperCase());
            MessageUtil.sendMessage(player, "§c" + errorMessage);
            MessageUtil.sendMessage(player, "§cError " + errors + "/" + MAX_ERRORS);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }

        // Announce to opponent
        Player opponent = Bukkit.getPlayer(playerUUID.equals(player1UUID) ? player2UUID : player1UUID);
        if (opponent != null) {
            MessageUtil.sendMessage(opponent, "§7" + (player != null ? player.getName() : "Opponent") +
                    " made an error: " + word.toUpperCase());
        }

        // Check elimination
        if (errors >= MAX_ERRORS) {
            eliminatePlayer(playerUUID);
        } else {
            switchTurns();
        }
    }

    private void acceptWord(UUID playerUUID, String word) {
        Player player = Bukkit.getPlayer(playerUUID);

        wordChain.add(word.toUpperCase());
        usedWords.add(word);
        currentLastLetter = word.substring(word.length() - 1);

        // Update word count
        int wordCount = playerWordsCount.get(playerUUID) + 1;
        playerWordsCount.put(playerUUID, wordCount);

        if (player != null) {
            MessageUtil.sendMessage(player, "§a✓ Accepted: " + word.toUpperCase());
            MessageUtil.sendMessage(player, "§aWords: " + wordCount);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        }

        // Announce to both players
        MessageUtil.sendMessage(getPlayer1(), "§6Chain: " + String.join(" → ", wordChain));
        MessageUtil.sendMessage(getPlayer2(), "§6Chain: " + String.join(" → ", wordChain));

        switchTurns();
    }

    private void switchTurns() {
        if (turnTimerTask != null) {
            turnTimerTask.cancel();
        }

        // Switch player
        currentPlayerTurn = currentPlayerTurn.equals(player1UUID) ? player2UUID : player1UUID;

        Player currentPlayer = Bukkit.getPlayer(currentPlayerTurn);
        Player waitingPlayer = Bukkit.getPlayer(currentPlayerTurn.equals(player1UUID) ? player2UUID : player1UUID);

        if (currentPlayer != null) {
            MessageUtil.sendMessage(currentPlayer, "§aYour turn! Word starting with §e'" +
                    currentLastLetter.toUpperCase() + "'");
            currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }

        if (waitingPlayer != null) {
            MessageUtil.sendMessage(waitingPlayer, "§7Wait for your turn...");
        }

        startTurnTimer();
    }

    private void eliminatePlayer(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        UUID winnerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;

        if (player != null) {
            MessageUtil.sendMessage(player, "§cYou've been eliminated! 3 errors reached.");
        }

        Player winner = Bukkit.getPlayer(winnerUUID);
        if (winner != null) {
            MessageUtil.sendMessage(winner, "§aYou win! Your opponent was eliminated!");
        }

        endGame(winnerUUID);
    }

    private void endGameByTime() {
        // Determine winner by word count
        int player1Words = playerWordsCount.get(player1UUID);
        int player2Words = playerWordsCount.get(player2UUID);

        UUID winnerUUID;
        if (player1Words > player2Words) {
            winnerUUID = player1UUID;
        } else if (player2Words > player1Words) {
            winnerUUID = player2UUID;
        } else {
            // Tie - check errors (fewer errors wins)
            int player1Errors = playerErrors.get(player1UUID);
            int player2Errors = playerErrors.get(player2UUID);

            if (player1Errors < player2Errors) {
                winnerUUID = player1UUID;
            } else if (player2Errors < player1Errors) {
                winnerUUID = player2UUID;
            } else {
                // Complete tie - random winner
                winnerUUID = ThreadLocalRandom.current().nextBoolean() ? player1UUID : player2UUID;
            }
        }

        MessageUtil.sendMessage(getPlayer1(), "§6Time's up! Final results:");
        MessageUtil.sendMessage(getPlayer2(), "§6Time's up! Final results:");
        MessageUtil.sendMessage(getPlayer1(), "§ePlayer 1: " + player1Words + " words, " + playerErrors.get(player1UUID) + " errors");
        MessageUtil.sendMessage(getPlayer2(), "§ePlayer 2: " + player2Words + " words, " + playerErrors.get(player2UUID) + " errors");

        endGame(winnerUUID);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (currentPhase != GamePhase.PLAYING) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerUUID.equals(player1UUID) || playerUUID.equals(player2UUID)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null && (from.getBlockX() != to.getBlockX() ||
                    from.getBlockY() != to.getBlockY() ||
                    from.getBlockZ() != to.getBlockZ())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (currentPhase != GamePhase.PLAYING) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerUUID.equals(player1UUID) || playerUUID.equals(player2UUID)) {
            UUID winnerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;

            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                MessageUtil.sendMessage(winner, "§aYour opponent has left the game! You win!");
            }

            endGame(winnerUUID);
        }
    }

    private void playSound(Sound sound, float volume, float pitch) {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            player1.playSound(player1.getLocation(), sound, volume, pitch);
        }
        if (player2 != null) {
            player2.playSound(player2.getLocation(), sound, volume, pitch);
        }
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
    }

    @Override
    public void endGame(UUID winnerUUID) {
        if (currentPhase == GamePhase.ENDED) return;

        currentPhase = GamePhase.ENDED;

        // Cancel timers
        if (turnTimerTask != null) {
            turnTimerTask.cancel();
            turnTimerTask = null;
        }
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player2 != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                MessageUtil.sendMessage(player1, "§6Word Chain Battle has ended! §aWinner: §e" + winner.getName());
                MessageUtil.sendMessage(player2, "§6Word Chain Battle has ended! §aWinner: §e" + winner.getName());

                // Show final stats
                int player1Words = playerWordsCount.getOrDefault(player1UUID, 0);
                int player2Words = playerWordsCount.getOrDefault(player2UUID, 0);
                int player1Errors = playerErrors.getOrDefault(player1UUID, 0);
                int player2Errors = playerErrors.getOrDefault(player2UUID, 0);

                MessageUtil.sendMessage(player1, "§eFinal chain: " + String.join(" → ", wordChain));
                MessageUtil.sendMessage(player2, "§eFinal chain: " + String.join(" → ", wordChain));
                MessageUtil.sendMessage(player1, "§eYour stats: " + player1Words + " words, " + player1Errors + " errors");
                MessageUtil.sendMessage(player2, "§eYour stats: " + player2Words + " words, " + player2Errors + " errors");
            }
        }

        HandlerList.unregisterAll(this);
        super.endGame(winnerUUID);
    }
}