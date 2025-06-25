package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DesACoudreMiniGame extends MiniGameBase implements Listener {

    private enum GamePhase {
        SETUP,
        COUNTDOWN,
        PLAYING,
        TURN_WAITING,
        ENDED
    }

    private GamePhase currentPhase = GamePhase.SETUP;
    private UUID currentPlayerTurn;
    private int turnTimeRemaining = 30;
    private BukkitTask turnTimerTask;
    private BukkitTask gameTimerTask;

    private final Map<UUID, Integer> playerLives = new HashMap<>();
    private final Map<UUID, Boolean> playerAlive = new HashMap<>();
    private final Map<UUID, Boolean> playerHasJumped = new HashMap<>();
    private final Map<UUID, Location> playerJumpStart = new HashMap<>();

    private Location arenaCenter;
    private Location poolCenter;
    private boolean arenaReady = false;
    private boolean useFrench = false;
    private int poolRadius = 6;
    private int poolDepth = 1;
    private int jumpHeight = 15;

    private final Set<Location> waterBlocks = new HashSet<>();
    private final Set<Location> placedBlocks = new HashSet<>();
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, GameMode> playerGameModes = new HashMap<>();

    private final int GAME_DURATION = 6000;
    private final int TURN_TIME = 30;
    private final int STARTING_LIVES = 1;

    public DesACoudreMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.DES_A_COUDRE, player1WonPvP);

        playerLives.put(player1UUID, STARTING_LIVES);
        playerLives.put(player2UUID, STARTING_LIVES);
        playerAlive.put(player1UUID, true);
        playerAlive.put(player2UUID, true);
        playerHasJumped.put(player1UUID, false);
        playerHasJumped.put(player2UUID, false);
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        storePlayerInventories();
        setupArenaAndStart();
    }

    private void storePlayerInventories() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) saveInventory(player1);
        if (player2 != null) saveInventory(player2);
    }

    private void saveInventory(Player player) {
        playerInventories.put(player.getUniqueId(), player.getInventory().getContents());
        playerGameModes.put(player.getUniqueId(), player.getGameMode());
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
    }

    private void setupArenaAndStart() {
        World world = getOrCreateMinigameWorld();
        if (world == null) {
            MessageUtil.sendMessage(getPlayer1(), "&cError: Could not create minigame world!");
            MessageUtil.sendMessage(getPlayer2(), "&cError: Could not create minigame world!");
            return;
        }

        generateArenaLocation(world);
        loadChunksSync(world, arenaCenter, 25);
        completeArenaSetupAndStart();
    }

    private void generateArenaLocation(World world) {
        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 120;
        arenaCenter = new Location(world, x, y, z);
        poolCenter = arenaCenter.clone().subtract(0, jumpHeight, 0);
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

        MessageUtil.sendMessage(getPlayer1(), "&aArena ready! Starting Dés à Coudre...");
        MessageUtil.sendMessage(getPlayer2(), "&aArena ready! Starting Dés à Coudre...");

        beginGame();
    }

    private void buildArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        clearArenaArea(world);
        buildJumpingPlatform(world);
        buildPool(world);
        buildArenaBarriers(world);
    }

    private void clearArenaArea(World world) {
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                for (int y = -5; y <= 25; y++) {
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildJumpingPlatform(World world) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY(), arenaCenter.getBlockZ() + z).setType(Material.GLASS);

                for (int y = -5; y <= -1; y++) {
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.BEDROCK);
                }
            }
        }
    }

    private void buildPool(World world) {
        buildSquarePool(world);
        buildPoolWalls(world);
    }

    private void buildSquarePool(World world) {
        for (int x = -poolRadius; x <= poolRadius; x++) {
            for (int z = -poolRadius; z <= poolRadius; z++) {
                buildPoolColumn(world, x, z);
            }
        }
    }

    private void buildPoolWalls(World world) {
        for (int x = -poolRadius - 1; x <= poolRadius + 1; x++) {
            for (int z = -poolRadius - 1; z <= poolRadius + 1; z++) {
                if (Math.abs(x) == poolRadius + 1 || Math.abs(z) == poolRadius + 1) {
                    int baseY = poolCenter.getBlockY();
                    world.getBlockAt(poolCenter.getBlockX() + x, baseY + 1, poolCenter.getBlockZ() + z).setType(Material.STONE_BRICKS);
                    world.getBlockAt(poolCenter.getBlockX() + x, baseY, poolCenter.getBlockZ() + z).setType(Material.STONE_BRICKS);
                    world.getBlockAt(poolCenter.getBlockX() + x, baseY - 1, poolCenter.getBlockZ() + z).setType(Material.STONE_BRICKS);
                }
            }
        }
    }

    private void buildPoolColumn(World world, int x, int z) {
        int baseY = poolCenter.getBlockY();

        world.getBlockAt(poolCenter.getBlockX() + x, baseY - 1, poolCenter.getBlockZ() + z).setType(Material.STONE);

        world.getBlockAt(poolCenter.getBlockX() + x, baseY, poolCenter.getBlockZ() + z).setType(Material.WATER);
        waterBlocks.add(new Location(world, poolCenter.getBlockX() + x, baseY, poolCenter.getBlockZ() + z));
    }

    private void buildArenaBarriers(World world) {
        int barrierRadius = 20;
        for (int angle = 0; angle < 360; angle += 10) {
            double radians = Math.toRadians(angle);
            int x = (int) (arenaCenter.getX() + barrierRadius * Math.cos(radians));
            int z = (int) (arenaCenter.getZ() + barrierRadius * Math.sin(radians));

            for (int y = -10; y <= 10; y++) {
                world.getBlockAt(x, arenaCenter.getBlockY() + y, z).setType(Material.BARRIER);
            }
        }
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            Location spawn1 = arenaCenter.clone().add(-2, 2, 0);
            spawn1.setYaw(180);
            player1.teleport(spawn1);
        }

        if (player2 != null) {
            Location spawn2 = arenaCenter.clone().add(2, 2, 0);
            spawn2.setYaw(180);
            player2.teleport(spawn2);
        }
    }

    private void beginGame() {
        currentPhase = GamePhase.COUNTDOWN;
        sendGameInstructions();
        startCountdown();
    }

    private void sendGameInstructions() {
        String instructions = useFrench ? getFrenchInstructions() : getEnglishInstructions();
        MessageUtil.sendMessage(getPlayer1(), instructions);
        MessageUtil.sendMessage(getPlayer2(), instructions);
    }

    private String getFrenchInstructions() {
        return "&6=== Dés à Coudre - Instructions ===\n" +
                "&fSautez à tour de rôle dans la piscine!\n" +
                "&fUn bloc sera placé là où vous atterrissez.\n" +
                "&fVous perdez une vie si vous ratez l'eau.\n" +
                "&fBonus: +1 vie si pas d'eau adjacente où vous tombez!\n" +
                "&fVous avez " + STARTING_LIVES + " vies chacun.\n" +
                "&fLe dernier en vie gagne!\n" +
                "&fVous avez 30 secondes par tour.";
    }

    private String getEnglishInstructions() {
        return "&6=== Dés à Coudre - Instructions ===\n" +
                "&fTake turns jumping into the pool!\n" +
                "&fA block will be placed where you land.\n" +
                "&fYou lose a life if you miss the water.\n" +
                "&fBonus: +1 life if no water adjacent to where you land!\n" +
                "&fYou each have " + STARTING_LIVES + " lives.\n" +
                "&fLast one alive wins!\n" +
                "&fYou have 30 seconds per turn.";
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    String message = useFrench ?
                            "&eJeu commence dans " + countdown + "..." :
                            "&eGame starting in " + countdown + "...";

                    MessageUtil.sendMessage(getPlayer1(), message);
                    MessageUtil.sendMessage(getPlayer2(), message);
                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    countdown--;
                } else {
                    String message = useFrench ? "&aCOMMENCEZ!" : "&aSTART!";
                    MessageUtil.sendMessage(getPlayer1(), message);
                    MessageUtil.sendMessage(getPlayer2(), message);
                    playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    startActiveGame();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startActiveGame() {
        currentPhase = GamePhase.PLAYING;
        currentPlayerTurn = player1UUID;
        startGameTimer();
        startNewTurn();
    }

    private void startGameTimer() {
        gameTimerTask = new BukkitRunnable() {
            int timeLeft = GAME_DURATION / 20;

            @Override
            public void run() {
                if (currentPhase == GamePhase.ENDED) {
                    this.cancel();
                    return;
                }

                timeLeft--;

                if (timeLeft <= 0) {
                    this.cancel();
                    endGameByTime();
                } else if (timeLeft % 60 == 0 || timeLeft <= 10) {
                    String message = useFrench ?
                            "&eTemps restant: " + timeLeft + " secondes" :
                            "&eTime remaining: " + timeLeft + " seconds";

                    MessageUtil.sendMessage(getPlayer1(), message);
                    MessageUtil.sendMessage(getPlayer2(), message);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startNewTurn() {
        if (!playerAlive.get(currentPlayerTurn)) {
            switchTurns();
            return;
        }

        playerHasJumped.put(currentPlayerTurn, false);
        currentPhase = GamePhase.TURN_WAITING;

        Player currentPlayer = Bukkit.getPlayer(currentPlayerTurn);
        Player waitingPlayer = Bukkit.getPlayer(currentPlayerTurn.equals(player1UUID) ? player2UUID : player1UUID);

        if (currentPlayer != null) {
            String turnMsg = useFrench ?
                    "&aVotre tour! Sautez dans la piscine!" :
                    "&aYour turn! Jump into the pool!";
            MessageUtil.sendMessage(currentPlayer, turnMsg);

            Location jumpSpot = arenaCenter.clone().add(0, 1, 0);
            jumpSpot.setYaw(180);
            currentPlayer.teleport(jumpSpot);

            String livesMsg = useFrench ?
                    "&eVies restantes: " + playerLives.get(currentPlayerTurn) :
                    "&eLives remaining: " + playerLives.get(currentPlayerTurn);
            MessageUtil.sendMessage(currentPlayer, livesMsg);

            currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            playerJumpStart.put(currentPlayerTurn, currentPlayer.getLocation().clone());
        }

        if (waitingPlayer != null) {
            String waitMsg = useFrench ?
                    "&7Attendez votre tour..." :
                    "&7Wait for your turn...";
            MessageUtil.sendMessage(waitingPlayer, waitMsg);
        }

        startTurnTimer();
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
                    handleTurnTimeout();
                    this.cancel();
                } else if (turnTimeRemaining <= 5) {
                    Player currentPlayer = Bukkit.getPlayer(currentPlayerTurn);
                    if (currentPlayer != null) {
                        String warning = useFrench ?
                                "&c" + turnTimeRemaining + " secondes restantes!" :
                                "&c" + turnTimeRemaining + " seconds left!";
                        MessageUtil.sendMessage(currentPlayer, warning);
                        currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 2.0f);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void handleTurnTimeout() {
        Player currentPlayer = Bukkit.getPlayer(currentPlayerTurn);

        int lives = playerLives.get(currentPlayerTurn) - 1;
        playerLives.put(currentPlayerTurn, lives);

        String timeoutMsg = useFrench ?
                "&cTemps écoulé! -1 vie. Vies restantes: " + lives :
                "&cTime's up! -1 life. Lives remaining: " + lives;

        if (currentPlayer != null) {
            MessageUtil.sendMessage(currentPlayer, timeoutMsg);
            currentPlayer.playSound(currentPlayer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
        }

        if (lives <= 0) {
            eliminatePlayer(currentPlayerTurn);
        } else {
            switchTurns();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (currentPhase != GamePhase.TURN_WAITING) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!playerUUID.equals(currentPlayerTurn)) return;
        if (playerHasJumped.get(playerUUID)) return;

        Location to = event.getTo();
        if (to == null) return;

        if (to.getY() < arenaCenter.getY() - 14) {
            Location checkLoc = new Location(to.getWorld(), to.getBlockX(), poolCenter.getBlockY(), to.getBlockZ());

            if (isInPoolArea(checkLoc)) {
                playerHasJumped.put(playerUUID, true);

                // Check what they landed on
                Block blockBelow = checkLoc.getBlock();
                if (blockBelow.getType() == Material.WHITE_WOOL) {
                    handleLandingOnWool(playerUUID);
                } else if (blockBelow.getType() == Material.WATER) {
                    handlePlayerLanding(playerUUID, checkLoc);
                } else {
                    handleMissedPool(playerUUID);
                }
            }
        }
    }

    private boolean isInPoolArea(Location location) {
        int x = Math.abs(location.getBlockX() - poolCenter.getBlockX());
        int z = Math.abs(location.getBlockZ() - poolCenter.getBlockZ());
        return x <= poolRadius && z <= poolRadius;
    }

    private void handleMissedPool(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);

        int lives = playerLives.get(playerUUID) - 1;
        playerLives.put(playerUUID, lives);

        String missMsg = useFrench ?
                "&cVous avez raté la piscine! -1 vie. Vies restantes: " + lives :
                "&cYou missed the pool! -1 life. Lives remaining: " + lives;

        if (player != null) {
            MessageUtil.sendMessage(player, missMsg);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
        }

        teleportPlayerBack(playerUUID);

        if (lives <= 0) {
            eliminatePlayer(playerUUID);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, this::switchTurns, 40L);
        }
    }

    private boolean isInWater(Location location) {
        Block block = location.getBlock();
        return block.getType() == Material.WATER;
    }

    private void handlePlayerLanding(UUID playerUUID, Location landingLocation) {
        if (turnTimerTask != null) {
            turnTimerTask.cancel();
        }

        Player player = Bukkit.getPlayer(playerUUID);
        Block landingBlock = landingLocation.getBlock();

        if (placedBlocks.contains(landingLocation)) {
            handleLandingOnWool(playerUUID);
            return;
        }

        boolean hasAdjacentWater = checkAdjacentWater(landingLocation);

        if (!hasAdjacentWater) {
            int currentLives = playerLives.get(playerUUID);
            playerLives.put(playerUUID, currentLives + 1);

            String bonusMsg = useFrench ?
                    "&a+1 vie bonus! Dés à coudre parfait!" :
                    "&a+1 bonus life! Perfect dés à coudre!";

            if (player != null) {
                MessageUtil.sendMessage(player, bonusMsg);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
            }
        }

        landingBlock.setType(Material.WHITE_WOOL);
        placedBlocks.add(landingLocation);
        waterBlocks.remove(landingLocation);

        String landingMsg = useFrench ?
                "&aBeau saut! Bloc placé." :
                "&aGood jump! Block placed.";

        if (player != null) {
            MessageUtil.sendMessage(player, landingMsg);
            player.playSound(player.getLocation(), Sound.BLOCK_WOOL_PLACE, 1.0f, 1.0f);
        }

        teleportPlayerBack(playerUUID);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isPoolFull()) {
                endGamePoolFull();
            } else {
                switchTurns();
            }
        }, 40L);
    }

    private void handleLandingOnWool(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);

        int lives = playerLives.get(playerUUID) - 1;
        playerLives.put(playerUUID, lives);

        String woolMsg = useFrench ?
                "&cVous avez atterri sur un bloc! -1 vie. Vies restantes: " + lives :
                "&cYou landed on a block! -1 life. Lives remaining: " + lives;

        if (player != null) {
            MessageUtil.sendMessage(player, woolMsg);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
        }

        teleportPlayerBack(playerUUID);

        if (lives <= 0) {
            eliminatePlayer(playerUUID);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, this::switchTurns, 40L);
        }
    }

    private boolean checkAdjacentWater(Location center) {
        World world = center.getWorld();
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();

        Location[] adjacentLocs = {
                new Location(world, x + 1, y, z),
                new Location(world, x - 1, y, z),
                new Location(world, x, y, z + 1),
                new Location(world, x, y, z - 1)
        };

        for (Location loc : adjacentLocs) {
            if (waterBlocks.contains(loc)) {
                return true;
            }
        }

        return false;
    }

    private void teleportPlayerBack(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            Location spawn = playerUUID.equals(player1UUID) ?
                    arenaCenter.clone().add(-2, 2, 0) :
                    arenaCenter.clone().add(2, 2, 0);
            spawn.setYaw(180);
            player.teleport(spawn);
        }
    }

    private boolean isPoolFull() {
        return waterBlocks.isEmpty();
    }

    private void switchTurns() {
        currentPlayerTurn = currentPlayerTurn.equals(player1UUID) ? player2UUID : player1UUID;

        if (!playerAlive.get(currentPlayerTurn)) {
            UUID otherPlayer = currentPlayerTurn.equals(player1UUID) ? player2UUID : player1UUID;
            if (playerAlive.get(otherPlayer)) {
                endGame(otherPlayer);
                return;
            }
        }

        startNewTurn();
    }

    private void eliminatePlayer(UUID playerUUID) {
        playerAlive.put(playerUUID, false);

        Player player = Bukkit.getPlayer(playerUUID);
        UUID remainingPlayer = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;

        String eliminationMsg = useFrench ?
                "&cVous êtes éliminé!" :
                "&cYou have been eliminated!";

        if (player != null) {
            MessageUtil.sendMessage(player, eliminationMsg);
        }

        if (playerAlive.get(remainingPlayer)) {
            endGame(remainingPlayer);
        } else {
            endGameBothEliminated();
        }
    }

    private void endGamePoolFull() {
        int player1Lives = playerLives.get(player1UUID);
        int player2Lives = playerLives.get(player2UUID);

        UUID winnerUUID;
        if (player1Lives > player2Lives) {
            winnerUUID = player1UUID;
        } else if (player2Lives > player1Lives) {
            winnerUUID = player2UUID;
        } else {
            winnerUUID = ThreadLocalRandom.current().nextBoolean() ? player1UUID : player2UUID;
        }

        String poolFullMsg = useFrench ?
                "&6Piscine remplie! Gagnant par nombre de vies." :
                "&6Pool is full! Winner by number of lives.";

        MessageUtil.sendMessage(getPlayer1(), poolFullMsg);
        MessageUtil.sendMessage(getPlayer2(), poolFullMsg);

        showFinalLives();
        endGame(winnerUUID);
    }

    private void endGameByTime() {
        String timeMsg = useFrench ?
                "&6Temps écoulé! Gagnant par nombre de vies." :
                "&6Time's up! Winner by number of lives.";

        MessageUtil.sendMessage(getPlayer1(), timeMsg);
        MessageUtil.sendMessage(getPlayer2(), timeMsg);

        int player1Lives = playerLives.get(player1UUID);
        int player2Lives = playerLives.get(player2UUID);

        UUID winnerUUID = player1Lives >= player2Lives ? player1UUID : player2UUID;

        showFinalLives();
        endGame(winnerUUID);
    }

    private void endGameBothEliminated() {
        String bothMsg = useFrench ?
                "&6Les deux joueurs éliminés! Match nul." :
                "&6Both players eliminated! It's a tie.";

        MessageUtil.sendMessage(getPlayer1(), bothMsg);
        MessageUtil.sendMessage(getPlayer2(), bothMsg);

        endGame(ThreadLocalRandom.current().nextBoolean() ? player1UUID : player2UUID);
    }

    private void showFinalLives() {
        int player1Lives = playerLives.get(player1UUID);
        int player2Lives = playerLives.get(player2UUID);

        Player p1 = getPlayer1();
        Player p2 = getPlayer2();
        String p1Name = p1 != null ? p1.getName() : "Player 1";
        String p2Name = p2 != null ? p2.getName() : "Player 2";

        String livesMsg = useFrench ? "&6=== Vies finales ===" : "&6=== Final Lives ===";
        MessageUtil.sendMessage(getPlayer1(), livesMsg);
        MessageUtil.sendMessage(getPlayer2(), livesMsg);

        MessageUtil.sendMessage(getPlayer1(), "&e" + p1Name + ": " + player1Lives + " vies");
        MessageUtil.sendMessage(getPlayer2(), "&e" + p2Name + ": " + player2Lives + " vies");
        MessageUtil.sendMessage(getPlayer1(), "&e" + p2Name + ": " + player2Lives + " vies");
        MessageUtil.sendMessage(getPlayer2(), "&e" + p1Name + ": " + player1Lives + " vies");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (currentPhase == GamePhase.ENDED) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerUUID.equals(player1UUID) || playerUUID.equals(player2UUID)) {
            UUID winnerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;

            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                String quitMsg = useFrench ?
                        "&aVotre adversaire a quitté! Vous gagnez!" :
                        "&aYour opponent left! You win!";
                MessageUtil.sendMessage(winner, quitMsg);
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

    public void setLanguage(boolean french) {
        this.useFrench = french;
    }

    private void restorePlayerInventories() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && playerInventories.containsKey(player1UUID)) {
            restoreInventory(player1);
        }

        if (player2 != null && playerInventories.containsKey(player2UUID)) {
            restoreInventory(player2);
        }
    }

    private void restoreInventory(Player player) {
        UUID playerUUID = player.getUniqueId();

        player.getInventory().clear();
        player.getInventory().setContents(playerInventories.get(playerUUID));

        GameMode previousGameMode = playerGameModes.getOrDefault(playerUUID, GameMode.SURVIVAL);
        player.setGameMode(previousGameMode);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        playerInventories.remove(playerUUID);
        playerGameModes.remove(playerUUID);
    }

    @Override
    public void endGame(UUID winnerUUID) {
        if (currentPhase == GamePhase.ENDED) return;

        currentPhase = GamePhase.ENDED;

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
                String endMsg = useFrench ?
                        "&6Dés à Coudre terminé! &aGagnant: &e" + winner.getName() :
                        "&6Dés à Coudre has ended! &aWinner: &e" + winner.getName();

                MessageUtil.sendMessage(player1, endMsg);
                MessageUtil.sendMessage(player2, endMsg);
            }
        }

        restorePlayerInventories();

        HandlerList.unregisterAll(this);
        super.endGame(winnerUUID);
    }
}