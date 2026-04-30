package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// Working
// TODO : Add some pre designed pilar/jump at the start
public class FloorIsLavaMiniGame extends MiniGameBase implements Listener {
    private enum GamePhase {
        SETUP,
        COUNTDOWN,
        ACTIVE,
        ENDING,
        GAME_OVER
    }

    private GamePhase currentPhase = GamePhase.SETUP;
    private Location arenaCenter;
    private int arenaRadius = 15; // Smaller arena
    private int arenaHeight = 40;
    private int currentLavaLevel;
    private int maxHeight;

    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Map<UUID, GameMode> playerGameModes = new HashMap<>();
    private final Map<UUID, Integer> playerHighestY = new HashMap<>();
    private final Map<UUID, Long> playerSurvivalTime = new HashMap<>();
    private final Map<UUID, Boolean> playerAlive = new HashMap<>();

    private BukkitRunnable lavaRiseTask;
    private BukkitRunnable sandDropTask;
    private BukkitRunnable gameTask;

    private final Set<Location> sandBlocks = new HashSet<>();
    private final Set<Location> lavaBlocks = new HashSet<>();
    private long gameStartTime;
    private boolean arenaReady = false;

    private final int LAVA_RISE_INTERVAL = 60; // ticks (3 seconds)
    private final int SAND_DROP_INTERVAL = 5; // ticks (0.25 seconds) - Much more frequent
    private final int GAME_DURATION = 4800; // ticks (4 minutes)

    public FloorIsLavaMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.FLOOR_IS_LAVA, player1WonPvP);

        playerAlive.put(player1UUID, true);
        playerAlive.put(player2UUID, true);
        playerHighestY.put(player1UUID, 0);
        playerHighestY.put(player2UUID, 0);
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        storePlayerInventories();
        setPlayersGameMode(GameMode.SURVIVAL);
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
        playerArmorContents.put(player.getUniqueId(), player.getInventory().getArmorContents());
        playerGameModes.put(player.getUniqueId(), player.getGameMode());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    private void setPlayersGameMode(GameMode gameMode) {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) player1.setGameMode(gameMode);
        if (player2 != null) player2.setGameMode(gameMode);
    }

    private void setupArenaAndStart() {
        World world = getOrCreateMinigameWorld();
        if (world == null) {
            MessageUtil.sendMessage(getPlayer1(), "&cError: Could not create minigame world!");
            MessageUtil.sendMessage(getPlayer2(), "&cError: Could not create minigame world!");
            return;
        }

        MessageUtil.sendMessage(getPlayer1(), "&eFloor is Lava: Generating arena...");
        MessageUtil.sendMessage(getPlayer2(), "&eFloor is Lava: Generating arena...");

        generateArenaLocation(world);
        loadChunksSync(world, arenaCenter, arenaRadius + 10);
        completeArenaSetupAndStart();
    }

    private void generateArenaLocation(World world) {
        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100;

        arenaCenter = new Location(world, x, y, z);
        currentLavaLevel = y;
        maxHeight = y + arenaHeight;
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

        MessageUtil.sendMessage(getPlayer1(), "&aArena ready! Starting Floor is Lava...");
        MessageUtil.sendMessage(getPlayer2(), "&aArena ready! Starting Floor is Lava...");

        beginGame();
    }

    private void buildArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        clearArenaArea(world);
        buildArenaFloor(world);
        buildStartingPlatforms(world);
        buildArenaWalls(world);
    }

    private void clearArenaArea(World world) {
        for (int x = -arenaRadius - 5; x <= arenaRadius + 5; x++) {
            for (int z = -arenaRadius - 5; z <= arenaRadius + 5; z++) {
                for (int y = -10; y <= arenaHeight + 10; y++) {
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildArenaFloor(World world) {
        // Build visible stone floor at the base level
        for (int x = -arenaRadius; x <= arenaRadius; x++) {
            for (int z = -arenaRadius; z <= arenaRadius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= arenaRadius) {
                    // Main floor
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() - 1, arenaCenter.getBlockZ() + z).setType(Material.STONE);

                    // Base foundation
                    for (int y = -5; y <= -2; y++) {
                        world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.BEDROCK);
                    }
                }
            }
        }
    }

    private void buildStartingPlatforms(World world) {
        // Build small starting platforms for each player just above the floor
        buildPlatform(world, arenaCenter.clone().add(-8, 0, 0), 2, Material.COBBLESTONE);
        buildPlatform(world, arenaCenter.clone().add(8, 0, 0), 2, Material.COBBLESTONE);
    }

    private void buildPlatform(World world, Location center, int size, Material material) {
        int halfSize = size / 2;
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                world.getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z).setType(material);
            }
        }
    }

    private void buildArenaWalls(World world) {
        // Build invisible barrier walls to contain players
        for (int y = -5; y <= arenaHeight + 10; y++) {
            for (int angle = 0; angle < 360; angle += 10) {
                double radians = Math.toRadians(angle);
                int x = (int) (arenaCenter.getX() + (arenaRadius + 1) * Math.cos(radians));
                int z = (int) (arenaCenter.getZ() + (arenaRadius + 1) * Math.sin(radians));
                world.getBlockAt(x, arenaCenter.getBlockY() + y, z).setType(Material.BARRIER);
            }
        }
    }

    private void buildPillar(World world, Location base, int height, Material material) {
        for (int y = 0; y <= height; y++) {
            world.getBlockAt(base.getBlockX(), base.getBlockY() + y, base.getBlockZ()).setType(material);
        }

        // Add a small platform on top
        buildPlatform(world, base.clone().add(0, height + 1, 0), 2, material);
    }

    private Material getPlatformMaterial(Random random) {
        Material[] materials = {
                Material.STONE, Material.COBBLESTONE, Material.STONE_BRICKS,
                Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS
        };
        return materials[random.nextInt(materials.length)];
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            Location spawn1 = arenaCenter.clone().add(-8, 1, 0);
            player1.teleport(spawn1);
            playerHighestY.put(player1.getUniqueId(), spawn1.getBlockY());
        }

        if (player2 != null) {
            Location spawn2 = arenaCenter.clone().add(8, 1, 0);
            player2.teleport(spawn2);
            playerHighestY.put(player2.getUniqueId(), spawn2.getBlockY());
        }
    }

    private void beginGame() {
        currentPhase = GamePhase.COUNTDOWN;

        MessageUtil.sendMessage(getPlayer1(), "&6Floor is Lava: Climb as high as possible!");
        MessageUtil.sendMessage(getPlayer2(), "&6Floor is Lava: Climb as high as possible!");

        MessageUtil.sendMessage(getPlayer1(), "&eThe lava will start rising in 10 seconds!");
        MessageUtil.sendMessage(getPlayer2(), "&eThe lava will start rising in 10 seconds!");

        MessageUtil.sendMessage(getPlayer1(), "&eLots of sand blocks will fall constantly!");
        MessageUtil.sendMessage(getPlayer2(), "&eLots of sand blocks will fall constantly!");

        startCountdown();
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    MessageUtil.sendMessage(getPlayer1(), "&eGame starting in " + countdown + "...");
                    MessageUtil.sendMessage(getPlayer2(), "&eGame starting in " + countdown + "...");

                    if (getPlayer1() != null) {
                        getPlayer1().playSound(getPlayer1().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    if (getPlayer2() != null) {
                        getPlayer2().playSound(getPlayer2().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    countdown--;
                } else {
                    MessageUtil.sendMessage(getPlayer1(), "&aGO! The lava is rising!");
                    MessageUtil.sendMessage(getPlayer2(), "&aGO! The lava is rising!");

                    if (getPlayer1() != null) {
                        getPlayer1().playSound(getPlayer1().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                    if (getPlayer2() != null) {
                        getPlayer2().playSound(getPlayer2().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }

                    startActiveGame();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startActiveGame() {
        currentPhase = GamePhase.ACTIVE;
        gameStartTime = System.currentTimeMillis();

        playerSurvivalTime.put(player1UUID, gameStartTime);
        playerSurvivalTime.put(player2UUID, gameStartTime);

        startLavaRising();
        startSandDropping();
        startGameTimer();
    }

    private void startLavaRising() {
        lavaRiseTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentPhase != GamePhase.ACTIVE) {
                    this.cancel();
                    return;
                }

                raiseLavaLevel();
                checkPlayerSafety();
            }
        };
        // Start lava rising after 10 seconds delay, then every 3 seconds
        lavaRiseTask.runTaskTimer(plugin, 200L, LAVA_RISE_INTERVAL);
    }

    private void startSandDropping() {
        sandDropTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentPhase != GamePhase.ACTIVE) {
                    this.cancel();
                    return;
                }

                dropSandBlocks();
            }
        };
        sandDropTask.runTaskTimer(plugin, SAND_DROP_INTERVAL, SAND_DROP_INTERVAL);
    }

    private void startGameTimer() {
        gameTask = new BukkitRunnable() {
            int timeLeft = GAME_DURATION / 20; // Convert ticks to seconds

            @Override
            public void run() {
                if (currentPhase != GamePhase.ACTIVE) {
                    this.cancel();
                    return;
                }

                timeLeft--;

                if (timeLeft <= 0) {
                    this.cancel();
                    endGameByTime();
                } else if (timeLeft % 30 == 0 || timeLeft <= 10) {
                    MessageUtil.sendMessage(getPlayer1(), "&eTime remaining: " + timeLeft + " seconds");
                    MessageUtil.sendMessage(getPlayer2(), "&eTime remaining: " + timeLeft + " seconds");
                }
            }
        };
        gameTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void raiseLavaLevel() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        currentLavaLevel++;

        // Place lava at the new level
        for (int x = -arenaRadius; x <= arenaRadius; x++) {
            for (int z = -arenaRadius; z <= arenaRadius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= arenaRadius) {
                    Location lavaLoc = new Location(world, arenaCenter.getX() + x, currentLavaLevel, arenaCenter.getZ() + z);

                    // Only place lava if there's no solid block
                    if (world.getBlockAt(lavaLoc).getType() == Material.AIR) {
                        world.getBlockAt(lavaLoc).setType(Material.LAVA);
                        lavaBlocks.add(lavaLoc);
                    }
                }
            }
        }

        // Announce lava level every 3 levels
        if ((currentLavaLevel - arenaCenter.getBlockY()) % 3 == 0) {
            int level = currentLavaLevel - arenaCenter.getBlockY();
            MessageUtil.sendMessage(getPlayer1(), "&cLava level: " + level + " blocks high!");
            MessageUtil.sendMessage(getPlayer2(), "&cLava level: " + level + " blocks high!");

            // Play warning sound
            if (getPlayer1() != null) {
                getPlayer1().playSound(getPlayer1().getLocation(), Sound.BLOCK_LAVA_POP, 1.0f, 0.5f);
            }
            if (getPlayer2() != null) {
                getPlayer2().playSound(getPlayer2().getLocation(), Sound.BLOCK_LAVA_POP, 1.0f, 0.5f);
            }
        }
    }

    private void dropSandBlocks() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        Random random = new Random();
        int dropCount = random.nextInt(5) + 3; // 3-7 sand blocks per drop (much more!)

        for (int i = 0; i < dropCount; i++) {
            // Random position within arena
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * (arenaRadius - 1);

            int x = (int) (arenaCenter.getX() + distance * Math.cos(angle));
            int z = (int) (arenaCenter.getZ() + distance * Math.sin(angle));
            int y = Math.max(currentLavaLevel + 15, arenaCenter.getBlockY() + 25);

            Location dropLoc = new Location(world, x, y, z);

            // Drop sand block that falls naturally
            world.getBlockAt(dropLoc).setType(Material.SAND);
            sandBlocks.add(dropLoc);

            // Sometimes drop gravel for variety
            if (random.nextInt(4) == 0) {
                world.getBlockAt(dropLoc).setType(Material.GRAVEL);
            }
        }
    }

    private void checkPlayerSafety() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && playerAlive.get(player1UUID)) {
            checkPlayerLava(player1);
        }

        if (player2 != null && playerAlive.get(player2UUID)) {
            checkPlayerLava(player2);
        }

        // Check if game should end
        boolean anyPlayerAlive = playerAlive.values().stream().anyMatch(alive -> alive);
        if (!anyPlayerAlive) {
            endGameAllDead();
        }
    }

    private void checkPlayerLava(Player player) {
        if (player.getLocation().getY() <= currentLavaLevel + 1) {
            eliminatePlayer(player);
        }
    }

    private void eliminatePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        playerAlive.put(playerUUID, false);

        long survivalTime = System.currentTimeMillis() - playerSurvivalTime.get(playerUUID);

        MessageUtil.sendMessage(player, "&cYou were consumed by lava!");
        MessageUtil.sendMessage(player, "&eSurvival time: " + (survivalTime / 1000) + " seconds");

        Player opponent = playerUUID.equals(player1UUID) ? getPlayer2() : getPlayer1();
        if (opponent != null) {
            MessageUtil.sendMessage(opponent, "&eYour opponent was eliminated by lava!");
        }

        // Check if other player wins
        UUID otherPlayerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;
        if (playerAlive.get(otherPlayerUUID)) {
            endGame(otherPlayerUUID);
        }
    }

    private void endGameByTime() {
        // Game ended by time - determine winner by highest Y reached
        int player1Height = playerHighestY.get(player1UUID);
        int player2Height = playerHighestY.get(player2UUID);

        UUID winnerUUID;
        if (player1Height > player2Height) {
            winnerUUID = player1UUID;
        } else if (player2Height > player1Height) {
            winnerUUID = player2UUID;
        } else {
            // Tie - check survival time or pick randomly
            winnerUUID = ThreadLocalRandom.current().nextBoolean() ? player1UUID : player2UUID;
        }

        MessageUtil.sendMessage(getPlayer1(), "&6Time's up! Final heights:");
        MessageUtil.sendMessage(getPlayer2(), "&6Time's up! Final heights:");
        MessageUtil.sendMessage(getPlayer1(), "&ePlayer 1: " + player1Height + " blocks");
        MessageUtil.sendMessage(getPlayer2(), "&ePlayer 2: " + player2Height + " blocks");

        endGame(winnerUUID);
    }

    private void endGameAllDead() {
        // Both players died - determine winner by who survived longest
        long player1Time = playerSurvivalTime.getOrDefault(player1UUID, 0L);
        long player2Time = playerSurvivalTime.getOrDefault(player2UUID, 0L);

        UUID winnerUUID = player1Time >= player2Time ? player1UUID : player2UUID;

        MessageUtil.sendMessage(getPlayer1(), "&6Both players eliminated! Winner by survival time.");
        MessageUtil.sendMessage(getPlayer2(), "&6Both players eliminated! Winner by survival time.");

        endGame(winnerUUID);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (currentPhase != GamePhase.ACTIVE) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

        UUID playerUUID = player.getUniqueId();
        if (!playerAlive.getOrDefault(playerUUID, false)) return;

        // Update highest Y reached
        int currentY = player.getLocation().getBlockY();
        int previousHigh = playerHighestY.getOrDefault(playerUUID, 0);

        if (currentY > previousHigh) {
            playerHighestY.put(playerUUID, currentY);

            // Announce new height records
            if (currentY > arenaCenter.getBlockY() + 20 && (currentY - previousHigh) >= 5) {
                MessageUtil.sendMessage(player, "&aNew height record: " + (currentY - arenaCenter.getBlockY()) + " blocks!");
            }
        }

        // Check if player is too far from arena center
        double distance = event.getTo().distance(arenaCenter);
        if (distance > arenaRadius + 1) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, "&cYou can't leave the arena!");
        }
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
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
        player.getInventory().setArmorContents(playerArmorContents.get(playerUUID));

        GameMode previousGameMode = playerGameModes.getOrDefault(playerUUID, GameMode.SURVIVAL);
        player.setGameMode(previousGameMode);

        // Remove any potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        playerInventories.remove(playerUUID);
        playerArmorContents.remove(playerUUID);
        playerGameModes.remove(playerUUID);
    }

    @Override
    public void endGame(UUID winnerUUID) {
        currentPhase = GamePhase.GAME_OVER;

        // Cancel all tasks
        if (lavaRiseTask != null) lavaRiseTask.cancel();
        if (sandDropTask != null) sandDropTask.cancel();
        if (gameTask != null) gameTask.cancel();

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player2 != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                MessageUtil.sendMessage(player1, "&6Floor is Lava has ended! &aWinner: &e" + winner.getName());
                MessageUtil.sendMessage(player2, "&6Floor is Lava has ended! &aWinner: &e" + winner.getName());

                // Show final stats
                int player1Height = playerHighestY.getOrDefault(player1UUID, 0) - arenaCenter.getBlockY();
                int player2Height = playerHighestY.getOrDefault(player2UUID, 0) - arenaCenter.getBlockY();

                MessageUtil.sendMessage(player1, "&eYour max height: " + player1Height + " blocks");
                MessageUtil.sendMessage(player2, "&eYour max height: " + player2Height + " blocks");
            }
        }

        restorePlayerInventories();
        super.endGame(winnerUUID);

        PlayerMoveEvent.getHandlerList().unregister(this);
    }
}
