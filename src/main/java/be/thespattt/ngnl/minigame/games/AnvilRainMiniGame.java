package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// Working
// TODO : find nice size and timer
public class AnvilRainMiniGame extends MiniGameBase implements Listener {
    private enum GamePhase {
        SETUP,
        COUNTDOWN,
        ACTIVE,
        ENDING,
        GAME_OVER
    }

    private GamePhase currentPhase = GamePhase.SETUP;
    private Location arenaCenter;
    private int arenaRadius = 20;
    private int arenaHeight = 30;

    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Map<UUID, GameMode> playerGameModes = new HashMap<>();
    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private final Map<UUID, Integer> playerLives = new HashMap<>();
    private final Map<UUID, Boolean> playerAlive = new HashMap<>();
    private final Map<UUID, Long> playerLastHit = new HashMap<>();

    private BukkitRunnable anvilDropTask;
    private BukkitRunnable powerupDropTask;
    private BukkitRunnable gameTask;
    private BukkitRunnable difficultyTask;

    private final Set<FallingBlock> activeAnvils = new HashSet<>();
    private final Set<Item> activePowerups = new HashSet<>();
    private long gameStartTime;
    private boolean arenaReady = false;
    private int currentDifficulty = 1;

    private final int ANVIL_DROP_INTERVAL = 8; // ticks (0.4 seconds) - Much faster!
    private final int POWERUP_DROP_INTERVAL = 80; // ticks (4 seconds)
    private final int GAME_DURATION = 1800; // ticks (90 seconds)
    private final int PLAYER_LIVES = 5;
    private final int INVULNERABILITY_TIME = 2000; // 2 seconds in milliseconds

    public AnvilRainMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.ANVIL_RAIN, player1WonPvP);

        playerScores.put(player1UUID, 0);
        playerScores.put(player2UUID, 0);
        playerLives.put(player1UUID, PLAYER_LIVES);
        playerLives.put(player2UUID, PLAYER_LIVES);
        playerAlive.put(player1UUID, true);
        playerAlive.put(player2UUID, true);
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

        if (player1 != null) {
            player1.setGameMode(gameMode);
            // Prevent hunger loss during the game
            player1.setFoodLevel(20);
            player1.setSaturation(20.0f);
        }
        if (player2 != null) {
            player2.setGameMode(gameMode);
            // Prevent hunger loss during the game
            player2.setFoodLevel(20);
            player2.setSaturation(20.0f);
        }
    }

    private void setupArenaAndStart() {
        World world = getOrCreateMinigameWorld();
        if (world == null) {
            MessageUtil.sendMessage(getPlayer1(), "&cError: Could not create minigame world!");
            MessageUtil.sendMessage(getPlayer2(), "&cError: Could not create minigame world!");
            return;
        }

        MessageUtil.sendMessage(getPlayer1(), "&eAnvil Rain: Generating arena...");
        MessageUtil.sendMessage(getPlayer2(), "&eAnvil Rain: Generating arena...");

        generateArenaLocation(world);
        loadChunksSync(world, arenaCenter, arenaRadius + 10);
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

        MessageUtil.sendMessage(getPlayer1(), "&aArena ready! Starting Anvil Rain...");
        MessageUtil.sendMessage(getPlayer2(), "&aArena ready! Starting Anvil Rain...");

        beginGame();
    }

    private void buildArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        clearArenaArea(world);
        buildArenaFloor(world);
        buildArenaWalls(world);
        buildObstacles(world);
    }

    private void clearArenaArea(World world) {
        for (int x = -arenaRadius - 5; x <= arenaRadius + 5; x++) {
            for (int z = -arenaRadius - 5; z <= arenaRadius + 5; z++) {
                for (int y = -5; y <= arenaHeight + 10; y++) {
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildArenaFloor(World world) {
        // Build a checkered pattern floor
        for (int x = -arenaRadius; x <= arenaRadius; x++) {
            for (int z = -arenaRadius; z <= arenaRadius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= arenaRadius) {
                    // Checkered pattern
                    Material floorMaterial = ((x + z) % 2 == 0) ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE;
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY(), arenaCenter.getBlockZ() + z).setType(floorMaterial);

                    // Foundation
                    for (int y = -5; y <= -1; y++) {
                        world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.BEDROCK);
                    }
                }
            }
        }
    }

    private void buildArenaWalls(World world) {
        // Build invisible barrier walls to contain players
        for (int y = 0; y <= arenaHeight; y++) {
            for (int angle = 0; angle < 360; angle += 10) {
                double radians = Math.toRadians(angle);
                int x = (int) (arenaCenter.getX() + (arenaRadius + 1) * Math.cos(radians));
                int z = (int) (arenaCenter.getZ() + (arenaRadius + 1) * Math.sin(radians));
                world.getBlockAt(x, arenaCenter.getBlockY() + y, z).setType(Material.BARRIER);
            }
        }
    }

    private void buildObstacles(World world) {
        Random random = new Random();

        // Add some cover objects that players can hide behind/under
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI * i) / 8; // Evenly distribute
            double distance = arenaRadius * 0.6; // 60% of radius

            int x = (int) (arenaCenter.getX() + distance * Math.cos(angle));
            int z = (int) (arenaCenter.getZ() + distance * Math.sin(angle));

            // Build small shelters
            if (i % 2 == 0) {
                buildShelter(world, new Location(world, x, arenaCenter.getY() + 1, z));
            } else {
                buildPillar(world, new Location(world, x, arenaCenter.getY() + 1, z));
            }
        }
    }

    private void buildShelter(World world, Location center) {
        // Small 3x3 roof at height 3
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(center.getBlockX() + x, center.getBlockY() + 2, center.getBlockZ() + z).setType(Material.STONE_BRICKS);
            }
        }

        // Corner pillars
        world.getBlockAt(center.getBlockX() - 1, center.getBlockY(), center.getBlockZ() - 1).setType(Material.STONE_BRICK_STAIRS);
        world.getBlockAt(center.getBlockX() + 1, center.getBlockY(), center.getBlockZ() - 1).setType(Material.STONE_BRICK_STAIRS);
        world.getBlockAt(center.getBlockX() - 1, center.getBlockY(), center.getBlockZ() + 1).setType(Material.STONE_BRICK_STAIRS);
        world.getBlockAt(center.getBlockX() + 1, center.getBlockY(), center.getBlockZ() + 1).setType(Material.STONE_BRICK_STAIRS);

        world.getBlockAt(center.getBlockX() - 1, center.getBlockY() + 1, center.getBlockZ() - 1).setType(Material.STONE_BRICK_STAIRS);
        world.getBlockAt(center.getBlockX() + 1, center.getBlockY() + 1, center.getBlockZ() - 1).setType(Material.STONE_BRICK_STAIRS);
        world.getBlockAt(center.getBlockX() - 1, center.getBlockY() + 1, center.getBlockZ() + 1).setType(Material.STONE_BRICK_STAIRS);
        world.getBlockAt(center.getBlockX() + 1, center.getBlockY() + 1, center.getBlockZ() + 1).setType(Material.STONE_BRICK_STAIRS);
    }

    private void buildPillar(World world, Location base) {
        for (int y = 0; y <= 4; y++) {
            world.getBlockAt(base.getBlockX(), base.getBlockY() + y, base.getBlockZ()).setType(Material.COBBLESTONE);
        }
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            Location spawn1 = arenaCenter.clone().add(-8, 1, 0);
            player1.teleport(spawn1);
        }

        if (player2 != null) {
            Location spawn2 = arenaCenter.clone().add(8, 1, 0);
            player2.teleport(spawn2);
        }
    }

    private void beginGame() {
        currentPhase = GamePhase.COUNTDOWN;

        MessageUtil.sendMessage(getPlayer1(), "&6Anvil Rain: Dodge the falling anvils!");
        MessageUtil.sendMessage(getPlayer2(), "&6Anvil Rain: Dodge the falling anvils!");

        MessageUtil.sendMessage(getPlayer1(), "&eCollect golden apples for points!");
        MessageUtil.sendMessage(getPlayer2(), "&eCollect golden apples for points!");

        MessageUtil.sendMessage(getPlayer1(), "&eYou have " + PLAYER_LIVES + " lives each!");
        MessageUtil.sendMessage(getPlayer2(), "&eYou have " + PLAYER_LIVES + " lives each!");

        MessageUtil.sendMessage(getPlayer1(), "&eSurvive for 90 seconds!");
        MessageUtil.sendMessage(getPlayer2(), "&eSurvive for 90 seconds!");

        MessageUtil.sendMessage(getPlayer1(), "&eUse the shelters for protection!");
        MessageUtil.sendMessage(getPlayer2(), "&eUse the shelters for protection!");

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
                    MessageUtil.sendMessage(getPlayer1(), "&aTake cover! Anvils incoming!");
                    MessageUtil.sendMessage(getPlayer2(), "&aTake cover! Anvils incoming!");

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

        startAnvilRain();
        startPowerupDrops();
        startDifficultyIncrease();
        startGameTimer();
        startHungerPrevention();
    }

    private void startAnvilRain() {
        anvilDropTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentPhase != GamePhase.ACTIVE) {
                    this.cancel();
                    return;
                }

                dropAnvils();
            }
        };
        anvilDropTask.runTaskTimer(plugin, ANVIL_DROP_INTERVAL, ANVIL_DROP_INTERVAL);
    }

    private void startPowerupDrops() {
        powerupDropTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentPhase != GamePhase.ACTIVE) {
                    this.cancel();
                    return;
                }

                dropPowerup();
            }
        };
        powerupDropTask.runTaskTimer(plugin, POWERUP_DROP_INTERVAL, POWERUP_DROP_INTERVAL);
    }

    private void startDifficultyIncrease() {
        difficultyTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentPhase != GamePhase.ACTIVE) {
                    this.cancel();
                    return;
                }

                currentDifficulty++;
                MessageUtil.sendMessage(getPlayer1(), "&cDifficulty increased! More anvils incoming!");
                MessageUtil.sendMessage(getPlayer2(), "&cDifficulty increased! More anvils incoming!");
            }
        };
        difficultyTask.runTaskTimer(plugin, 400L, 400L); // Every 20 seconds (faster progression)
    }

    private void startHungerPrevention() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentPhase != GamePhase.ACTIVE) {
                    this.cancel();
                    return;
                }

                // Keep players fed during the game
                Player player1 = getPlayer1();
                Player player2 = getPlayer2();

                if (player1 != null && playerAlive.getOrDefault(player1UUID, false)) {
                    player1.setFoodLevel(20);
                    player1.setSaturation(20.0f);
                }
                if (player2 != null && playerAlive.getOrDefault(player2UUID, false)) {
                    player2.setFoodLevel(20);
                    player2.setSaturation(20.0f);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    private void startGameTimer() {
        gameTask = new BukkitRunnable() {
            int timeLeft = GAME_DURATION / 20;

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

    private void dropAnvils() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        Random random = new Random();
        int anvilCount = Math.min(2 + (currentDifficulty / 2), 8); // Start with 2, max 8 anvils at once

        for (int i = 0; i < anvilCount; i++) {
            // Random position within arena
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * (arenaRadius - 2);

            int x = (int) (arenaCenter.getX() + distance * Math.cos(angle));
            int z = (int) (arenaCenter.getZ() + distance * Math.sin(angle));
            int y = arenaCenter.getBlockY() + arenaHeight;

            Location dropLoc = new Location(world, x, y, z);

            // Create falling anvil
            FallingBlock anvil = world.spawnFallingBlock(dropLoc, Material.ANVIL.createBlockData());
            anvil.setDropItem(false); // Don't drop item when it lands
            anvil.setHurtEntities(true);
            anvil.setFallDistance(0);

            activeAnvils.add(anvil);

            // Play warning sound
            world.playSound(dropLoc, Sound.BLOCK_ANVIL_LAND, 0.3f, 2.0f);
        }
    }

    private void dropPowerup() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        Random random = new Random();

        // Random position within arena
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * (arenaRadius - 3);

        int x = (int) (arenaCenter.getX() + distance * Math.cos(angle));
        int z = (int) (arenaCenter.getZ() + distance * Math.sin(angle));
        int y = arenaCenter.getBlockY() + 3;

        Location dropLoc = new Location(world, x, y, z);

        // Drop golden apple
        Item powerup = world.dropItem(dropLoc, new ItemStack(Material.GOLDEN_APPLE));
        powerup.setVelocity(new Vector(0, 0, 0));
        powerup.setGlowing(true);

        activePowerups.add(powerup);

        // Play powerup sound
        world.playSound(dropLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onAnvilHit(EntityDamageByEntityEvent event) {
        if (currentPhase != GamePhase.ACTIVE) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof FallingBlock)) return;

        Player player = (Player) event.getEntity();
        FallingBlock anvil = (FallingBlock) event.getDamager();

        if (!isParticipant(player)) return;
        if (anvil.getBlockData().getMaterial() != Material.ANVIL) return;

        UUID playerUUID = player.getUniqueId();

        // Check invulnerability
        long lastHit = playerLastHit.getOrDefault(playerUUID, 0L);
        if (System.currentTimeMillis() - lastHit < INVULNERABILITY_TIME) {
            event.setCancelled(true);
            return;
        }

        // Reduce lives
        int lives = playerLives.get(playerUUID) - 1;
        playerLives.put(playerUUID, lives);
        playerLastHit.put(playerUUID, System.currentTimeMillis());

        // Give brief invulnerability effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 255));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0));

        MessageUtil.sendMessage(player, "&cHit by anvil! Lives remaining: " + lives);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

        // Check if player eliminated
        if (lives <= 0) {
            eliminatePlayer(player);
        }

        event.setCancelled(true); // Prevent normal damage
    }

    @EventHandler
    public void onPowerupPickup(PlayerPickupItemEvent event) {
        if (currentPhase != GamePhase.ACTIVE) return;

        Player player = event.getPlayer();
        Item item = event.getItem();

        if (!isParticipant(player)) return;
        if (!activePowerups.contains(item)) return;
        if (item.getItemStack().getType() != Material.GOLDEN_APPLE) return;

        UUID playerUUID = player.getUniqueId();

        // Add score
        int score = playerScores.get(playerUUID) + 10;
        playerScores.put(playerUUID, score);

        activePowerups.remove(item);

        MessageUtil.sendMessage(player, "&a+10 points! Score: " + score);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

        event.setCancelled(true);
        item.remove();
    }

    @EventHandler
    public void onAnvilLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) return;

        FallingBlock anvil = (FallingBlock) event.getEntity();
        if (activeAnvils.contains(anvil)) {
            activeAnvils.remove(anvil);

            // Remove the anvil block after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getBlock().getType() == Material.ANVIL) {
                    event.getBlock().setType(Material.AIR);
                }
            }, 100L); // 5 seconds
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (currentPhase != GamePhase.ACTIVE) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

        // Check if player is leaving arena
        double distance = event.getTo().distance(arenaCenter);
        if (distance > arenaRadius) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, "&cYou can't leave the arena!");
        }
    }

    private void eliminatePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        playerAlive.put(playerUUID, false);

        MessageUtil.sendMessage(player, "&cYou've been eliminated!");
        MessageUtil.sendMessage(player, "&eFinal score: " + playerScores.get(playerUUID));

        Player opponent = playerUUID.equals(player1UUID) ? getPlayer2() : getPlayer1();
        if (opponent != null) {
            MessageUtil.sendMessage(opponent, "&eYour opponent was eliminated!");
        }

        // Give spectator effects
        player.setGameMode(GameMode.SPECTATOR);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));

        // Check if other player wins
        UUID otherPlayerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;
        if (playerAlive.get(otherPlayerUUID)) {
            endGame(otherPlayerUUID);
        }
    }

    private void endGameByTime() {
        // Game ended by time - determine winner by score
        int player1Score = playerScores.get(player1UUID);
        int player2Score = playerScores.get(player2UUID);

        UUID winnerUUID;
        if (player1Score > player2Score) {
            winnerUUID = player1UUID;
        } else if (player2Score > player1Score) {
            winnerUUID = player2UUID;
        } else {
            // Tie - check lives remaining
            int player1Lives = playerLives.get(player1UUID);
            int player2Lives = playerLives.get(player2UUID);

            if (player1Lives > player2Lives) {
                winnerUUID = player1UUID;
            } else if (player2Lives > player1Lives) {
                winnerUUID = player2UUID;
            } else {
                // Complete tie - random winner
                winnerUUID = ThreadLocalRandom.current().nextBoolean() ? player1UUID : player2UUID;
            }
        }

        MessageUtil.sendMessage(getPlayer1(), "&6Time's up! Final scores:");
        MessageUtil.sendMessage(getPlayer2(), "&6Time's up! Final scores:");
        MessageUtil.sendMessage(getPlayer1(), "&ePlayer 1: " + player1Score + " points, " + playerLives.get(player1UUID) + " lives");
        MessageUtil.sendMessage(getPlayer2(), "&ePlayer 2: " + player2Score + " points, " + playerLives.get(player2UUID) + " lives");

        endGame(winnerUUID);
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
        if (anvilDropTask != null) anvilDropTask.cancel();
        if (powerupDropTask != null) powerupDropTask.cancel();
        if (gameTask != null) gameTask.cancel();
        if (difficultyTask != null) difficultyTask.cancel();

        // Clean up entities
        activeAnvils.forEach(anvil -> {
            if (anvil.isValid()) anvil.remove();
        });
        activePowerups.forEach(item -> {
            if (item.isValid()) item.remove();
        });

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player2 != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                MessageUtil.sendMessage(player1, "&6Anvil Rain has ended! &aWinner: &e" + winner.getName());
                MessageUtil.sendMessage(player2, "&6Anvil Rain has ended! &aWinner: &e" + winner.getName());

                // Show final stats
                int player1Score = playerScores.getOrDefault(player1UUID, 0);
                int player2Score = playerScores.getOrDefault(player2UUID, 0);
                int player1Lives = playerLives.getOrDefault(player1UUID, 0);
                int player2Lives = playerLives.getOrDefault(player2UUID, 0);

                MessageUtil.sendMessage(player1, "&eYour final score: " + player1Score + " points, " + player1Lives + " lives left");
                MessageUtil.sendMessage(player2, "&eYour final score: " + player2Score + " points, " + player2Lives + " lives left");
            }
        }

        super.endGame(winnerUUID);

        restorePlayerInventories();

        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        PlayerPickupItemEvent.getHandlerList().unregister(this);
        EntityChangeBlockEvent.getHandlerList().unregister(this);
        PlayerMoveEvent.getHandlerList().unregister(this);
    }
}