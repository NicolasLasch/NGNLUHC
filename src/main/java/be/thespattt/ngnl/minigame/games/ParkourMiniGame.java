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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ParkourMiniGame extends MiniGameBase implements Listener {
    private boolean gameActive = false;
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Map<UUID, Integer> playerCheckpoints = new HashMap<>();
    private final Map<UUID, Long> playerStartTimes = new HashMap<>();
    private final Map<UUID, Long> playerCompletionTimes = new HashMap<>();

    private Location courseStartLocation;
    private final int courseLength = 50; // Length of the course
    private final int courseWidth = 5;   // Width of each player's course
    private final int courseHeight = 20; // Maximum height of the course
    private final int totalCheckpoints = 5; // Number of checkpoints (including finish line)

    private final Map<Integer, Location> player1Checkpoints = new HashMap<>();
    private final Map<Integer, Location> player2Checkpoints = new HashMap<>();

    public ParkourMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.PARKOUR, player1WonPvP);
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        storePlayerInventories();
        setupParkourCourse();
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
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    private void restorePlayerInventories() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && playerInventories.containsKey(player1UUID)) restoreInventory(player1);
        if (player2 != null && playerInventories.containsKey(player2UUID)) restoreInventory(player2);
    }

    private void restoreInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(playerInventories.get(player.getUniqueId()));
        player.getInventory().setArmorContents(playerArmorContents.get(player.getUniqueId()));
        playerInventories.remove(player.getUniqueId());
        playerArmorContents.remove(player.getUniqueId());

        // Remove any potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void setupParkourCourse() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100; // Start building from y=100

        courseStartLocation = new Location(world, x, y, z);

        preloadChunksAndThen(world, courseStartLocation, Math.max(courseLength, courseWidth) * 2, () -> {
            buildParkourCourse(world, courseStartLocation);
            setupCheckpoints();
            teleportPlayersToStart();
            showInstructions();
            resetPlayerProgress();
            startCountdown();
        });
    }

    public void preloadChunksAndThen(World world, Location center, int radius, Runnable onLoaded) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        Set<Chunk> chunksToLoad = loadChunks(center, world, chunkRadius);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (chunksToLoad.stream().anyMatch(chunk -> !chunk.isLoaded())) return;
                cancel();
                onLoaded.run();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private Set<Chunk> loadChunks(Location center, World world, int chunkRadius) {
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

    private void buildParkourCourse(World world, Location startLocation) {
        int startX = startLocation.getBlockX();
        int startY = startLocation.getBlockY();
        int startZ = startLocation.getBlockZ();
        clearArea(world, startX, startY, startZ);

        buildStartingPlatform(world, startX, startY, startZ);

        buildDividingWall(world, startX, startY, startZ);

        buildCourse(world, startX, startY, startZ, true);
        buildCourse(world, startX, startY, startZ, false);

        buildFinishLine(world, startX, startY, startZ);
    }

    private void clearArea(World world, int startX, int startY, int startZ) {
        for (int x = -courseWidth - 1; x <= courseWidth * 2 + 1; x++) {
            for (int z = -1; z <= courseLength + 1; z++) {
                for (int y = 0; y <= courseHeight + 5; y++) {
                    world.getBlockAt(startX + x, startY + y, startZ + z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildStartingPlatform(World world, int startX, int startY, int startZ) {
        for (int x = -courseWidth; x <= -1; x++) {
            for (int z = -1; z <= 2; z++) {
                world.getBlockAt(startX + x, startY, startZ + z).setType(Material.QUARTZ_BLOCK);
            }
        }

        for (int x = 1; x <= courseWidth; x++) {
            for (int z = -1; z <= 2; z++) {
                world.getBlockAt(startX + x, startY, startZ + z).setType(Material.QUARTZ_BLOCK);
            }
        }
    }

    private void buildDividingWall(World world, int startX, int startY, int startZ) {
        for (int z = -1; z <= courseLength + 1; z++) {
            for (int y = 1; y <= courseHeight + 3; y++) {
                world.getBlockAt(startX, startY + y, startZ + z).setType(Material.GLASS);
            }
        }
    }

    private void buildCourse(World world, int startX, int startY, int startZ, boolean isPlayer1) {
        int sideMultiplier = isPlayer1 ? -1 : 1;

        Map<Integer, Location> checkpoints = isPlayer1 ? player1Checkpoints : player2Checkpoints;

        Random random = new Random();
        int currentZ = 3;
        int currentY = startY;

        checkpoints.put(0, new Location(world,
                startX + (sideMultiplier * (courseWidth / 2)),
                startY + 1,
                startZ + 1));

        int segmentLength = courseLength / (totalCheckpoints - 1);

        while (currentZ < courseLength) {
            int element = random.nextInt(6);
            int nextZ = Math.min(currentZ + random.nextInt(5) + 3, courseLength);

            switch (element) {
                case 0:
                    buildJumpingBlocks(world, startX, currentY, startZ, currentZ, nextZ, sideMultiplier);
                    break;
                case 1:
                    currentY = buildStairClimb(world, startX, currentY, startZ, currentZ, nextZ, sideMultiplier);
                    break;
                case 2:
                    currentY = buildDownwardPath(world, startX, currentY, startZ, currentZ, nextZ, sideMultiplier);
                    break;
                case 3:
                    buildSingleBlockJumps(world, startX, currentY, startZ, currentZ, nextZ, sideMultiplier);
                    break;
                case 4:
                    buildSlimeJumps(world, startX, currentY, startZ, currentZ, nextZ, sideMultiplier);
                    break;
                case 5:
                    buildIceSlide(world, startX, currentY, startZ, currentZ, nextZ, sideMultiplier);
                    break;
            }

            int checkpointIndex = (currentZ / segmentLength) + 1;
            if (checkpointIndex < totalCheckpoints && currentZ >= checkpointIndex * segmentLength - 2) {
                int checkpointX = startX + (sideMultiplier * (random.nextInt(courseWidth - 2) + 1));
                world.getBlockAt(checkpointX, currentY, startZ + nextZ - 1).setType(Material.EMERALD_BLOCK);

                checkpoints.put(checkpointIndex, new Location(world,
                        checkpointX,
                        currentY + 1,
                        startZ + nextZ - 1));
            }

            currentZ = nextZ;
        }
    }

    private void buildJumpingBlocks(World world, int startX, int currentY, int startZ, int startZOffset, int endZOffset, int sideMultiplier) {
        Random random = new Random();
        int lastX = startX + (sideMultiplier * (random.nextInt(courseWidth - 2) + 1));

        for (int z = startZOffset; z < endZOffset; z += 2) {
            int nextX = startX + (sideMultiplier * (random.nextInt(courseWidth - 2) + 1));
            world.getBlockAt(nextX, currentY, startZ + z).setType(Material.OAK_PLANKS);

            lastX = nextX;
        }
    }

    private int buildStairClimb(World world, int startX, int currentY, int startZ, int startZOffset, int endZOffset, int sideMultiplier) {
        Random random = new Random();
        int newY = currentY;

        for (int z = startZOffset; z < endZOffset; z++) {
            if (z % 2 == 0) {
                newY++;
                int x = startX + (sideMultiplier * (random.nextInt(courseWidth - 2) + 1));
                world.getBlockAt(x, newY, startZ + z).setType(Material.STONE_BRICKS);
            }
        }

        return newY;
    }

    private int buildDownwardPath(World world, int startX, int currentY, int startZ, int startZOffset, int endZOffset, int sideMultiplier) {
        if (currentY <= startZ + 3) {
            return currentY;
        }

        Random random = new Random();
        int newY = currentY;

        for (int z = startZOffset; z < endZOffset; z++) {
            if (z % 2 == 0) {
                newY--;
                int x = startX + (sideMultiplier * (random.nextInt(courseWidth - 2) + 1));
                world.getBlockAt(x, newY, startZ + z).setType(Material.PRISMARINE_BRICKS);
            }
        }

        return newY;
    }

    private void buildSingleBlockJumps(World world, int startX, int currentY, int startZ, int startZOffset, int endZOffset, int sideMultiplier) {
        Random random = new Random();

        for (int z = startZOffset; z < endZOffset; z += 3) {
            int x = startX + (sideMultiplier * (random.nextInt(courseWidth - 2) + 1));
            world.getBlockAt(x, currentY, startZ + z).setType(Material.BIRCH_PLANKS);
        }
    }

    private void buildSlimeJumps(World world, int startX, int currentY, int startZ, int startZOffset, int endZOffset, int sideMultiplier) {
        Random random = new Random();

        for (int z = startZOffset; z < endZOffset; z += 4) {
            int x = startX + (sideMultiplier * (random.nextInt(courseWidth - 2) + 1));
            world.getBlockAt(x, currentY, startZ + z).setType(Material.SLIME_BLOCK);
        }
    }

    private void buildIceSlide(World world, int startX, int currentY, int startZ, int startZOffset, int endZOffset, int sideMultiplier) {
        int x = startX + (sideMultiplier * (courseWidth / 2));

        for (int z = startZOffset; z < endZOffset; z++) {
            world.getBlockAt(x, currentY, startZ + z).setType(Material.BLUE_ICE);

            world.getBlockAt(x + sideMultiplier, currentY, startZ + z).setType(Material.SPRUCE_FENCE);
            world.getBlockAt(x + (2 * sideMultiplier), currentY, startZ + z).setType(Material.SPRUCE_FENCE);
        }
    }

    private void buildFinishLine(World world, int startX, int startY, int startZ) {
        for (int x = -courseWidth; x <= -1; x++) {
            for (int z = courseLength; z <= courseLength + 3; z++) {
                world.getBlockAt(startX + x, startY, startZ + z).setType(Material.GOLD_BLOCK);
            }
        }

        for (int x = 1; x <= courseWidth; x++) {
            for (int z = courseLength; z <= courseLength + 3; z++) {
                world.getBlockAt(startX + x, startY, startZ + z).setType(Material.GOLD_BLOCK);
            }
        }

        for (int x = -courseWidth; x <= courseWidth; x++) {
            if (x == 0) continue;
            world.getBlockAt(startX + x, startY + 3, startZ + courseLength + 2).setType(Material.GLOWSTONE);
        }

        player1Checkpoints.put(totalCheckpoints - 1, new Location(world,
                startX - (courseWidth / 2),
                startY + 1,
                startZ + courseLength + 1));

        player2Checkpoints.put(totalCheckpoints - 1, new Location(world,
                startX + (courseWidth / 2),
                startY + 1,
                startZ + courseLength + 1));
    }

    private void setupCheckpoints() {
        // Checkpoints are already set up during the course generation
    }

    private void teleportPlayersToStart() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player1Checkpoints.containsKey(0)) {
            player1.teleport(player1Checkpoints.get(0));
        }

        if (player2 != null && player2Checkpoints.containsKey(0)) {
            player2.teleport(player2Checkpoints.get(0));
        }
    }

    private void resetPlayerProgress() {
        playerCheckpoints.put(player1UUID, 0);
        playerCheckpoints.put(player2UUID, 0);
        playerStartTimes.clear();
        playerCompletionTimes.clear();
    }

    private void showInstructions() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        String header = "&6⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯";
        MessageUtil.sendMessage(player1, header);
        MessageUtil.sendMessage(player2, header);

        MessageUtil.sendMessage(player1, "&6Parkour &e- Minigame Instructions");
        MessageUtil.sendMessage(player2, "&6Parkour &e- Minigame Instructions");

        MessageUtil.sendMessage(player1, "&7• Race to the finish line against your opponent");
        MessageUtil.sendMessage(player2, "&7• Race to the finish line against your opponent");

        MessageUtil.sendMessage(player1, "&7• Hit each &aEmerald Checkpoint &7to track your progress");
        MessageUtil.sendMessage(player2, "&7• Hit each &aEmerald Checkpoint &7to track your progress");

        MessageUtil.sendMessage(player1, "&7• Reach the &6Gold Finish Line &7first to win!");
        MessageUtil.sendMessage(player2, "&7• Reach the &6Gold Finish Line &7first to win!");

        MessageUtil.sendMessage(player1, "&7• If you fall, you'll be teleported to your last checkpoint");
        MessageUtil.sendMessage(player2, "&7• If you fall, you'll be teleported to your last checkpoint");

        MessageUtil.sendMessage(player1, header);
        MessageUtil.sendMessage(player2, header);
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                Player player1 = getPlayer1();
                Player player2 = getPlayer2();

                if (countdown > 0) {
                    MessageUtil.sendMessage(player1, "&eRace starting in " + countdown + "...");
                    MessageUtil.sendMessage(player2, "&eRace starting in " + countdown + "...");

                    player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

                    if (player1Checkpoints.containsKey(0)) player1.teleport(player1Checkpoints.get(0));
                    if (player2Checkpoints.containsKey(0)) player2.teleport(player2Checkpoints.get(0));

                    countdown--;
                } else {
                    MessageUtil.sendMessage(player1, "&aGO! Race to the finish!");
                    MessageUtil.sendMessage(player2, "&aGO! Race to the finish!");

                    player1.playSound(player1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    player2.playSound(player2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    long startTime = System.currentTimeMillis();
                    playerStartTimes.put(player1UUID, startTime);
                    playerStartTimes.put(player2UUID, startTime);

                    gameActive = true;
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameActive) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;
        handleCheckpoints(player);
        handleFalling(player);
    }

    private void handleCheckpoints(Player player) {
        UUID playerUUID = player.getUniqueId();
        int currentCheckpoint = playerCheckpoints.getOrDefault(playerUUID, 0);
        Map<Integer, Location> checkpoints = playerUUID.equals(player1UUID) ? player1Checkpoints : player2Checkpoints;
        Block blockBelow = player.getLocation().subtract(0, 1, 0).getBlock();

        if (blockBelow.getType() == Material.EMERALD_BLOCK) {
            for (int i = 1; i < totalCheckpoints - 1; i++) {
                if (checkpoints.containsKey(i) &&
                        isBlockAt(blockBelow, checkpoints.get(i).subtract(0, 1, 0))) {

                    if (i > currentCheckpoint) {
                        playerCheckpoints.put(playerUUID, i);
                        MessageUtil.sendMessage(player, "&aCheckpoint " + i + " reached!");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                    break;
                }
            }
        }
        if (blockBelow.getType() == Material.GOLD_BLOCK) {
            if (currentCheckpoint >= totalCheckpoints - 2) {
                handleFinish(player);
            }
        }
    }

    private boolean isBlockAt(Block block, Location location) {
        return block.getX() == location.getBlockX() &&
                block.getY() == location.getBlockY() &&
                block.getZ() == location.getBlockZ();
    }

    private void handleFalling(Player player) {
        if (player.getLocation().getY() < courseStartLocation.getY() - 5) {
            teleportToLastCheckpoint(player);
        }
    }

    private void teleportToLastCheckpoint(Player player) {
        UUID playerUUID = player.getUniqueId();
        int checkpoint = playerCheckpoints.getOrDefault(playerUUID, 0);

        Map<Integer, Location> checkpoints = playerUUID.equals(player1UUID) ? player1Checkpoints : player2Checkpoints;

        if (checkpoints.containsKey(checkpoint)) {
            player.teleport(checkpoints.get(checkpoint));
            MessageUtil.sendMessage(player, "&eReturned to checkpoint " + checkpoint);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
        } else {
            player.teleport(checkpoints.get(0));
            MessageUtil.sendMessage(player, "&eReturned to the start");
        }
    }

    private void handleFinish(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (playerCompletionTimes.containsKey(playerUUID)) {
            return;
        }
        long finishTime = System.currentTimeMillis();
        long startTime = playerStartTimes.getOrDefault(playerUUID, finishTime);
        long completionTime = finishTime - startTime;
        playerCompletionTimes.put(playerUUID, completionTime);
        String timeString = formatTime(completionTime);
        MessageUtil.sendMessage(player, "&6You finished the parkour in " + timeString + "!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        createFinishFireworks(player.getLocation());
        checkRaceEnd();
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long ms = millis % 1000;

        return seconds + "." + ms + "s";
    }

    private void createFinishFireworks(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.ELECTRIC_SPARK, location.add(0, 1, 0), 50, 1, 1, 1, 0.1);
    }

    private void checkRaceEnd() {
        boolean player1Finished = playerCompletionTimes.containsKey(player1UUID);
        boolean player2Finished = playerCompletionTimes.containsKey(player2UUID);

        if (player1Finished && player2Finished) {
            long player1Time = playerCompletionTimes.get(player1UUID);
            long player2Time = playerCompletionTimes.get(player2UUID);

            if (player1Time <= player2Time) {
                endGame(player1UUID);
            } else {
                endGame(player2UUID);
            }
            return;
        }

        if (player1Finished && !player2Finished) {
            startFinishTimeout(player2UUID);
        } else if (player2Finished && !player1Finished) {
            startFinishTimeout(player1UUID);
        }
    }

    private void startFinishTimeout(UUID playerUUID) {
        new BukkitRunnable() {
            int countdown = 30;

            @Override
            public void run() {
                if (!gameActive) {
                    this.cancel();
                    return;
                }

                if (playerCompletionTimes.containsKey(playerUUID)) {
                    this.cancel();
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);

                if (countdown <= 0) {
                    this.cancel();

                    UUID winnerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;
                    endGame(winnerUUID);

                    if (player != null) {
                        MessageUtil.sendMessage(player, "&cTime's up! Your opponent wins the race.");
                    }
                    return;
                }

                if (countdown <= 5 || countdown == 10 || countdown == 20) {
                    if (player != null) {
                        MessageUtil.sendMessage(player, "&eYour opponent already finished! You have " + countdown + " seconds left.");
                    }
                }

                countdown--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
    }

    @Override
    public void endGame(UUID winnerUUID) {
        if (!gameActive) return;

        gameActive = false;

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();
        Player winner = Bukkit.getPlayer(winnerUUID);

        super.endGame(winnerUUID);

        if (player1 != null && player2 != null && winner != null) {
            MessageUtil.sendMessage(player1, "&6Parkour Mini-Game has ended! Winner: " + winner.getName());
            MessageUtil.sendMessage(player2, "&6Parkour Mini-Game has ended! Winner: " + winner.getName());

            if (playerCompletionTimes.containsKey(player1UUID)) {
                String timeStr = formatTime(playerCompletionTimes.get(player1UUID));
                MessageUtil.sendMessage(player1, "&eYour time: " + timeStr);
                MessageUtil.sendMessage(player2, "&ePlayer 1's time: " + timeStr);
            }

            if (playerCompletionTimes.containsKey(player2UUID)) {
                String timeStr = formatTime(playerCompletionTimes.get(player2UUID));
                MessageUtil.sendMessage(player1, "&ePlayer 2's time: " + timeStr);
                MessageUtil.sendMessage(player2, "&eYour time: " + timeStr);
            }

            // Play sounds
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            Player loser = winner.equals(player1) ? player2 : player1;
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }

        teleportToSafeLocation();
        restorePlayerInventories();

        PlayerMoveEvent.getHandlerList().unregister(this);
    }

    private void teleportToSafeLocation() {
        World world = getOrCreateMinigameWorld();
        if (world != null) {
            Location safeLocation = new Location(world, 0, 80, 0);
            if (getPlayer1() != null) getPlayer1().teleport(safeLocation);
            if (getPlayer2() != null) getPlayer2().teleport(safeLocation);
        }
    }
}