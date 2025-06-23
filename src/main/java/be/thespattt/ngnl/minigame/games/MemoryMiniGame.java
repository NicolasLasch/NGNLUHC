package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MemoryMiniGame extends MiniGameBase implements Listener {
    private enum GamePhase {
        SETUP,
        SHOWING_STRUCTURE,
        BUILDING,
        CHECKING_RESULTS,
        GAME_OVER
    }

    private GamePhase currentPhase = GamePhase.SETUP;
    private int currentRound = 0;
    private final int TOTAL_ROUNDS = 5;
    private final int INITIAL_VIEW_TIME = 5; // seconds
    private final int BUILD_TIME = 10; // seconds

    private Location centerPlatformLocation;
    private Location player1PlatformLocation;
    private Location player2PlatformLocation;

    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Map<UUID, GameMode> playerGameModes = new HashMap<>();

    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private BoundingBox player1BuildArea;
    private BoundingBox player2BuildArea;
    private BoundingBox centerBuildArea;

    private final List<MemoryStructure> structures = new ArrayList<>();
    private Map<Location, Material> currentStructureBlocks = new HashMap<>();
    private Map<Location, Material> player1PlacedBlocks = new HashMap<>();
    private Map<Location, Material> player2PlacedBlocks = new HashMap<>();

    // Countdown task reference
    private BukkitRunnable countdownTask;

    public MemoryMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.MEMORY_GAME, player1WonPvP);

        // Initialize player scores
        playerScores.put(player1UUID, 0);
        playerScores.put(player2UUID, 0);

        // Initialize structures
        initializeStructures();
    }

    private void initializeStructures() {
        // Structure 1: Simple cube (4 blocks)
        structures.add(new MemoryStructure(
                Arrays.asList(
                        new BlockData(0, 0, 0, Material.RED_CONCRETE),
                        new BlockData(1, 0, 0, Material.RED_CONCRETE),
                        new BlockData(0, 0, 1, Material.RED_CONCRETE),
                        new BlockData(1, 0, 1, Material.RED_CONCRETE)
                ),
                "Simple Square"
        ));

        // Structure 2: Small T shape (5 blocks)
        structures.add(new MemoryStructure(
                Arrays.asList(
                        new BlockData(0, 0, 0, Material.BLUE_CONCRETE),
                        new BlockData(1, 0, 0, Material.BLUE_CONCRETE),
                        new BlockData(2, 0, 0, Material.BLUE_CONCRETE),
                        new BlockData(1, 0, 1, Material.BLUE_CONCRETE),
                        new BlockData(1, 0, 2, Material.BLUE_CONCRETE)
                ),
                "T Shape"
        ));

        // Structure 3: Little house (7 blocks)
        structures.add(new MemoryStructure(
                Arrays.asList(
                        new BlockData(0, 0, 0, Material.OAK_PLANKS),
                        new BlockData(1, 0, 0, Material.OAK_PLANKS),
                        new BlockData(2, 0, 0, Material.OAK_PLANKS),
                        new BlockData(0, 1, 0, Material.GLASS),
                        new BlockData(2, 1, 0, Material.GLASS),
                        new BlockData(1, 2, 0, Material.OAK_SLAB),
                        new BlockData(1, 1, 0, Material.OAK_DOOR)
                ),
                "Little House"
        ));

        // Structure 4: Multi-colored pattern (9 blocks)
        structures.add(new MemoryStructure(
                Arrays.asList(
                        new BlockData(0, 0, 0, Material.RED_CONCRETE),
                        new BlockData(1, 0, 0, Material.BLUE_CONCRETE),
                        new BlockData(2, 0, 0, Material.YELLOW_CONCRETE),
                        new BlockData(0, 0, 1, Material.GREEN_CONCRETE),
                        new BlockData(1, 0, 1, Material.BLACK_CONCRETE),
                        new BlockData(2, 0, 1, Material.WHITE_CONCRETE),
                        new BlockData(0, 0, 2, Material.PINK_CONCRETE),
                        new BlockData(1, 0, 2, Material.PURPLE_CONCRETE),
                        new BlockData(2, 0, 2, Material.CYAN_CONCRETE)
                ),
                "Color Pattern"
        ));

        // Structure 5: Complex shape (12 blocks with different heights)
        structures.add(new MemoryStructure(
                Arrays.asList(
                        new BlockData(1, 0, 1, Material.STONE),
                        new BlockData(1, 1, 1, Material.STONE),
                        new BlockData(1, 2, 1, Material.STONE),
                        new BlockData(0, 2, 0, Material.GOLD_BLOCK),
                        new BlockData(2, 2, 0, Material.GOLD_BLOCK),
                        new BlockData(0, 2, 2, Material.GOLD_BLOCK),
                        new BlockData(2, 2, 2, Material.GOLD_BLOCK),
                        new BlockData(0, 3, 0, Material.DIAMOND_BLOCK),
                        new BlockData(2, 3, 0, Material.DIAMOND_BLOCK),
                        new BlockData(0, 3, 2, Material.DIAMOND_BLOCK),
                        new BlockData(2, 3, 2, Material.DIAMOND_BLOCK),
                        new BlockData(1, 3, 1, Material.EMERALD_BLOCK)
                ),
                "Tower Design"
        ));
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        storePlayerInventories();
        setPlayersGameMode(GameMode.CREATIVE);
        setupArena();
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

    // Replace the existing setupArena method with this in MemoryMiniGame
    private void setupArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        // Create a random location
        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 72; // Set a consistent height

        // Center platform (for showing the structure)
        centerPlatformLocation = new Location(world, x, y, z);

        // Player 1 platform (west)
        player1PlatformLocation = centerPlatformLocation.clone().add(-15, 0, 0);

        // Player 2 platform (east)
        player2PlatformLocation = centerPlatformLocation.clone().add(15, 0, 0);

        // Preload chunks before initializing build areas and building arena
        preloadChunksAndThen(world, centerPlatformLocation, 32, () -> {
            // Define build areas
            centerBuildArea = new BoundingBox(
                    centerPlatformLocation.getX() - 2,
                    centerPlatformLocation.getY() + 1,
                    centerPlatformLocation.getZ() - 2,
                    centerPlatformLocation.getX() + 2,
                    centerPlatformLocation.getY() + 5,
                    centerPlatformLocation.getZ() + 2
            );

            player1BuildArea = new BoundingBox(
                    player1PlatformLocation.getX() - 2,
                    player1PlatformLocation.getY() + 1,
                    player1PlatformLocation.getZ() - 2,
                    player1PlatformLocation.getX() + 2,
                    player1PlatformLocation.getY() + 5,
                    player1PlatformLocation.getZ() + 2
            );

            player2BuildArea = new BoundingBox(
                    player2PlatformLocation.getX() - 2,
                    player2PlatformLocation.getY() + 1,
                    player2PlatformLocation.getZ() - 2,
                    player2PlatformLocation.getX() + 2,
                    player2PlatformLocation.getY() + 5,
                    player2PlatformLocation.getZ() + 2
            );

            buildArena(world);
            teleportPlayers();
            startGame();
        });
    }

    // Update the chunk loading methods to match your expected implementation
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

    // Also modify the clearBuildArea method to check for null
    private void clearBuildArea(BoundingBox area) {
        World world = getOrCreateMinigameWorld();
        if (world == null || area == null) return;

        for (int x = (int) area.getMinX(); x <= (int) area.getMaxX(); x++) {
            for (int y = (int) area.getMinY(); y <= (int) area.getMaxY(); y++) {
                for (int z = (int) area.getMinZ(); z <= (int) area.getMaxZ(); z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildArena(World world) {
        // Build center platform (9x9)
        buildPlatform(world, centerPlatformLocation, 9, Material.QUARTZ_BLOCK);

        // Build player1 platform (9x9)
        buildPlatform(world, player1PlatformLocation, 9, Material.BLUE_CONCRETE);

        // Build player2 platform (9x9)
        buildPlatform(world, player2PlatformLocation, 9, Material.RED_CONCRETE);

        // Add directional markers
        markBuildingArea(world, player1PlatformLocation, Material.BLUE_STAINED_GLASS, 5);
        markBuildingArea(world, player2PlatformLocation, Material.RED_STAINED_GLASS, 5);
        markBuildingArea(world, centerPlatformLocation, Material.WHITE_STAINED_GLASS, 5);
    }

    private void buildPlatform(World world, Location center, int size, Material material) {
        int halfSize = size / 2;

        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                // Main platform
                world.getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z).setType(material);

                // Void protection below
                for (int y = 1; y <= 5; y++) {
                    world.getBlockAt(center.getBlockX() + x, center.getBlockY() - y, center.getBlockZ() + z).setType(Material.BARRIER);
                }
            }
        }
    }

    private void markBuildingArea(World world, Location center, Material material, int size) {
        int halfSize = size / 2;

        // Mark corners of building area
        world.getBlockAt(center.getBlockX() - halfSize, center.getBlockY() + 1, center.getBlockZ() - halfSize).setType(material);
        world.getBlockAt(center.getBlockX() + halfSize, center.getBlockY() + 1, center.getBlockZ() - halfSize).setType(material);
        world.getBlockAt(center.getBlockX() - halfSize, center.getBlockY() + 1, center.getBlockZ() + halfSize).setType(material);
        world.getBlockAt(center.getBlockX() + halfSize, center.getBlockY() + 1, center.getBlockZ() + halfSize).setType(material);
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            player1.teleport(player1PlatformLocation.clone().add(0, 1, 0));
        }

        if (player2 != null) {
            player2.teleport(player2PlatformLocation.clone().add(0, 1, 0));
        }
    }

    public void startGame() {
        currentRound = 0;

        MessageUtil.sendMessage(getPlayer1(), "&6Memory Mini-Game: Welcome!");
        MessageUtil.sendMessage(getPlayer2(), "&6Memory Mini-Game: Welcome!");

        MessageUtil.sendMessage(getPlayer1(), "&eRules: You will be shown a structure for a short time.");
        MessageUtil.sendMessage(getPlayer2(), "&eRules: You will be shown a structure for a short time.");

        MessageUtil.sendMessage(getPlayer1(), "&eThen, you must rebuild it exactly on your platform.");
        MessageUtil.sendMessage(getPlayer2(), "&eThen, you must rebuild it exactly on your platform.");

        MessageUtil.sendMessage(getPlayer1(), "&eThe viewing time gets shorter each round!");
        MessageUtil.sendMessage(getPlayer2(), "&eThe viewing time gets shorter each round!");

        // Start first round after 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, this::startNextRound, 100L);
    }

    private void startNextRound() {
        currentRound++;

        if (currentRound > TOTAL_ROUNDS) {
            determineWinner();
            return;
        }

        // Clear platforms
        clearBuildArea(centerBuildArea);
        clearBuildArea(player1BuildArea);
        clearBuildArea(player2BuildArea);

        // Clear tracked blocks
        currentStructureBlocks.clear();
        player1PlacedBlocks.clear();
        player2PlacedBlocks.clear();

        // Clear player inventories
        getPlayer1().getInventory().clear();
        getPlayer2().getInventory().clear();

        // Announce round
        MessageUtil.sendMessage(getPlayer1(), "&6Round " + currentRound + " of " + TOTAL_ROUNDS);
        MessageUtil.sendMessage(getPlayer2(), "&6Round " + currentRound + " of " + TOTAL_ROUNDS);

        // Show structure
        showStructure();
    }

    private void showStructure() {
        currentPhase = GamePhase.SHOWING_STRUCTURE;

        // Get current structure
        MemoryStructure structure = structures.get(currentRound - 1);

        MessageUtil.sendMessage(getPlayer1(), "&aStructure: " + structure.getName());
        MessageUtil.sendMessage(getPlayer2(), "&aStructure: " + structure.getName());

        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        // Build structure
        for (BlockData blockData : structure.getBlocks()) {
            Location loc = centerPlatformLocation.clone().add(
                    blockData.getX() - 2,
                    blockData.getY() + 1,
                    blockData.getZ() - 2
            );

            world.getBlockAt(loc).setType(blockData.getMaterial());
            currentStructureBlocks.put(loc, blockData.getMaterial());
        }

        // Calculate view time (decreases each round)
        int viewTime = Math.max(1, INITIAL_VIEW_TIME - (currentRound - 1));

        // Start countdown
        MessageUtil.sendMessage(getPlayer1(), "&6Memorize this structure! " + viewTime + " seconds...");
        MessageUtil.sendMessage(getPlayer2(), "&6Memorize this structure! " + viewTime + " seconds...");

        // Show countdown titles
        countdownTask = new BukkitRunnable() {
            int timeLeft = viewTime;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    cancel();
                    startBuildingPhase();
                    return;
                }

                getPlayer1().sendTitle(
                        ChatColor.YELLOW + Integer.toString(timeLeft),
                        ChatColor.GOLD + "Memorize the structure!",
                        0, 20, 5
                );
                getPlayer2().sendTitle(
                        ChatColor.YELLOW + Integer.toString(timeLeft),
                        ChatColor.GOLD + "Memorize the structure!",
                        0, 20, 5
                );

                timeLeft--;
            }
        };

        countdownTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startBuildingPhase() {
        currentPhase = GamePhase.BUILDING;

        // Hide the structure (replace with barriers)
        for (Location loc : currentStructureBlocks.keySet()) {
            loc.getBlock().setType(Material.BARRIER);
        }

        // Give players the necessary blocks
        MemoryStructure structure = structures.get(currentRound - 1);
        givePlayerBuildingBlocks(getPlayer1(), structure);
        givePlayerBuildingBlocks(getPlayer2(), structure);

        MessageUtil.sendMessage(getPlayer1(), "&6Start building! You have " + BUILD_TIME + " seconds!");
        MessageUtil.sendMessage(getPlayer2(), "&6Start building! You have " + BUILD_TIME + " seconds!");

        // Start countdown for building phase
        countdownTask = new BukkitRunnable() {
            int timeLeft = BUILD_TIME;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    cancel();
                    checkResults();
                    return;
                }

                if (timeLeft <= 3) {
                    getPlayer1().sendTitle(
                            ChatColor.RED + Integer.toString(timeLeft),
                            ChatColor.GOLD + "Hurry up!",
                            0, 20, 5
                    );
                    getPlayer2().sendTitle(
                            ChatColor.RED + Integer.toString(timeLeft),
                            ChatColor.GOLD + "Hurry up!",
                            0, 20, 5
                    );
                }

                timeLeft--;
            }
        };

        countdownTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void givePlayerBuildingBlocks(Player player, MemoryStructure structure) {
        // Count required blocks by material
        Map<Material, Integer> requiredBlocks = new HashMap<>();

        for (BlockData blockData : structure.getBlocks()) {
            Material material = blockData.getMaterial();
            requiredBlocks.put(material, requiredBlocks.getOrDefault(material, 0) + 1);
        }

        // Give blocks to player
        for (Map.Entry<Material, Integer> entry : requiredBlocks.entrySet()) {
            player.getInventory().addItem(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    private void checkResults() {
        currentPhase = GamePhase.CHECKING_RESULTS;

        // Shift the currentStructureBlocks from the center to player platforms for comparison
        Map<Location, Material> referenceBlocks1 = new HashMap<>();
        Map<Location, Material> referenceBlocks2 = new HashMap<>();

        for (Map.Entry<Location, Material> entry : currentStructureBlocks.entrySet()) {
            Location centerLoc = entry.getKey();
            Material material = entry.getValue();

            // Calculate offset from center platform
            int offsetX = centerLoc.getBlockX() - centerPlatformLocation.getBlockX();
            int offsetY = centerLoc.getBlockY() - centerPlatformLocation.getBlockY();
            int offsetZ = centerLoc.getBlockZ() - centerPlatformLocation.getBlockZ();

            // Create equivalents for player platforms
            Location player1Loc = new Location(
                    centerLoc.getWorld(),
                    player1PlatformLocation.getBlockX() + offsetX,
                    player1PlatformLocation.getBlockY() + offsetY,
                    player1PlatformLocation.getBlockZ() + offsetZ
            );

            Location player2Loc = new Location(
                    centerLoc.getWorld(),
                    player2PlatformLocation.getBlockX() + offsetX,
                    player2PlatformLocation.getBlockY() + offsetY,
                    player2PlatformLocation.getBlockZ() + offsetZ
            );

            referenceBlocks1.put(player1Loc, material);
            referenceBlocks2.put(player2Loc, material);
        }

        // Count correct blocks for each player
        int player1Correct = countCorrectBlocks(player1PlacedBlocks, referenceBlocks1);
        int player2Correct = countCorrectBlocks(player2PlacedBlocks, referenceBlocks2);

        int totalBlocks = currentStructureBlocks.size();

        MessageUtil.sendMessage(getPlayer1(), "&6You placed &a" + player1Correct + "&6 of &e" + totalBlocks + "&6 blocks correctly.");
        MessageUtil.sendMessage(getPlayer2(), "&6You placed &a" + player2Correct + "&6 of &e" + totalBlocks + "&6 blocks correctly.");

        // Update scores
        playerScores.put(player1UUID, playerScores.get(player1UUID) + player1Correct);
        playerScores.put(player2UUID, playerScores.get(player2UUID) + player2Correct);

        // Show original structure again
        for (Map.Entry<Location, Material> entry : currentStructureBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }

        // Check if both completed perfectly
        boolean bothPerfect = (player1Correct == totalBlocks && player2Correct == totalBlocks);

        if (bothPerfect) {
            MessageUtil.sendMessage(getPlayer1(), "&aBoth players built the structure perfectly! Moving to next round.");
            MessageUtil.sendMessage(getPlayer2(), "&aBoth players built the structure perfectly! Moving to next round.");

            // Start next round after delay
            Bukkit.getScheduler().runTaskLater(plugin, this::startNextRound, 60L);
        } else {
            // Determine if we have a winner by comparing correct blocks
            if (player1Correct > player2Correct) {
                MessageUtil.sendMessage(getPlayer1(), "&aYou win this round!");
                MessageUtil.sendMessage(getPlayer2(), "&cYour opponent built more blocks correctly.");
            } else if (player2Correct > player1Correct) {
                MessageUtil.sendMessage(getPlayer1(), "&cYour opponent built more blocks correctly.");
                MessageUtil.sendMessage(getPlayer2(), "&aYou win this round!");
            } else {
                MessageUtil.sendMessage(getPlayer1(), "&eIt's a tie this round!");
                MessageUtil.sendMessage(getPlayer2(), "&eIt's a tie this round!");
            }

            // Start next round after delay
            Bukkit.getScheduler().runTaskLater(plugin, this::startNextRound, 60L);
        }
    }

    private int countCorrectBlocks(Map<Location, Material> placedBlocks, Map<Location, Material> referenceBlocks) {
        int correctCount = 0;

        for (Map.Entry<Location, Material> entry : referenceBlocks.entrySet()) {
            Location loc = entry.getKey();
            Material expectedMaterial = entry.getValue();

            // Check if this location has the correct material
            if (placedBlocks.containsKey(loc) && placedBlocks.get(loc) == expectedMaterial) {
                correctCount++;
            }
        }

        return correctCount;
    }

    private void determineWinner() {
        currentPhase = GamePhase.GAME_OVER;

        int player1TotalScore = playerScores.get(player1UUID);
        int player2TotalScore = playerScores.get(player2UUID);

        UUID winnerUUID;

        if (player1TotalScore > player2TotalScore) {
            winnerUUID = player1UUID;
        } else if (player2TotalScore > player1TotalScore) {
            winnerUUID = player2UUID;
        } else {
            // In case of a tie, randomly select winner
            winnerUUID = ThreadLocalRandom.current().nextBoolean() ? player1UUID : player2UUID;
        }

        // Announce final scores
        MessageUtil.sendMessage(getPlayer1(), "&6Final score: &eYou: " + player1TotalScore + " - Opponent: " + player2TotalScore);
        MessageUtil.sendMessage(getPlayer2(), "&6Final score: &eYou: " + player2TotalScore + " - Opponent: " + player1TotalScore);

        // End the game
        endGame(winnerUUID);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!isParticipant(player)) return;

        // Only allow building in the appropriate build area and during building phase
        if (currentPhase != GamePhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        Location placeLoc = event.getBlock().getLocation();

        // Check if player is placing block in their build area
        if (player.getUniqueId().equals(player1UUID)) {
            if (!player1BuildArea.contains(placeLoc.getX(), placeLoc.getY(), placeLoc.getZ())) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, "&cYou can only build within your designated area!");
                return;
            }

            // Track placed blocks
            player1PlacedBlocks.put(placeLoc, event.getBlock().getType());
        } else if (player.getUniqueId().equals(player2UUID)) {
            if (!player2BuildArea.contains(placeLoc.getX(), placeLoc.getY(), placeLoc.getZ())) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, "&cYou can only build within your designated area!");
                return;
            }

            // Track placed blocks
            player2PlacedBlocks.put(placeLoc, event.getBlock().getType());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!isParticipant(player)) return;

        Location breakLoc = event.getBlock().getLocation();

        // Always prevent breaking the platform
        if (breakLoc.getY() == player1PlatformLocation.getY() ||
                breakLoc.getY() == player2PlatformLocation.getY() ||
                breakLoc.getY() == centerPlatformLocation.getY()) {
            event.setCancelled(true);
            return;
        }

        // Prevent breaking outside build phase
        if (currentPhase != GamePhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        // Check if player is breaking block in their build area
        if (player.getUniqueId().equals(player1UUID)) {
            if (!player1BuildArea.contains(breakLoc.getX(), breakLoc.getY(), breakLoc.getZ())) {
                event.setCancelled(true);
                return;
            }

            // Remove from tracked blocks
            player1PlacedBlocks.remove(breakLoc);
        } else if (player.getUniqueId().equals(player2UUID)) {
            if (!player2BuildArea.contains(breakLoc.getX(), breakLoc.getY(), breakLoc.getZ())) {
                event.setCancelled(true);
                return;
            }

            // Remove from tracked blocks
            player2PlacedBlocks.remove(breakLoc);
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

        // Restore inventory contents
        player.getInventory().clear();
        player.getInventory().setContents(playerInventories.get(playerUUID));
        player.getInventory().setArmorContents(playerArmorContents.get(playerUUID));

        // Restore game mode
        GameMode previousGameMode = playerGameModes.getOrDefault(playerUUID, GameMode.SURVIVAL);
        player.setGameMode(previousGameMode);

        // Clean up maps
        playerInventories.remove(playerUUID);
        playerArmorContents.remove(playerUUID);
        playerGameModes.remove(playerUUID);
    }

    @Override
    public void endGame(UUID winnerUUID) {
        currentPhase = GamePhase.GAME_OVER;

        // Cancel any ongoing tasks
        if (countdownTask != null) {
            countdownTask.cancel();
        }

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        // Display results
        if (player1 != null && player2 != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                MessageUtil.sendMessage(player1, "&6Memory Mini-Game has ended! &aWinner: &e" + winner.getName());
                MessageUtil.sendMessage(player2, "&6Memory Mini-Game has ended! &aWinner: &e" + winner.getName());
            }
        }

        // Call parent to handle game ending
        super.endGame(winnerUUID);

        // Restore inventories and game modes
        restorePlayerInventories();

        // Unregister event listeners
        BlockPlaceEvent.getHandlerList().unregister(this);
        BlockBreakEvent.getHandlerList().unregister(this);
        PlayerMoveEvent.getHandlerList().unregister(this);
    }

    // Helper classes
    private static class BlockData {
        private final int x;
        private final int y;
        private final int z;
        private final Material material;

        public BlockData(int x, int y, int z, Material material) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public Material getMaterial() { return material; }
    }

    private static class MemoryStructure {
        private final List<BlockData> blocks;
        private final String name;

        public MemoryStructure(List<BlockData> blocks, String name) {
            this.blocks = blocks;
            this.name = name;
        }

        public List<BlockData> getBlocks() { return blocks; }
        public String getName() { return name; }
    }
}
