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
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// Working
// TODO : Add more builds and variety
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
    private final int INITIAL_VIEW_TIME = 5;
    private final int BUILD_TIME = 10;

    private Location centerPlatformLocation;
    private Location player1PlatformLocation;
    private Location player2PlatformLocation;

    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Map<UUID, GameMode> playerGameModes = new HashMap<>();
    private final Map<UUID, Boolean> playerAllowFlight = new HashMap<>();
    private final Map<UUID, Boolean> playerFlying = new HashMap<>();

    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private BoundingBox player1BuildArea;
    private BoundingBox player2BuildArea;
    private BoundingBox centerBuildArea;

    private final List<MemoryStructure> structures = new ArrayList<>();
    private Map<Location, Material> currentStructureBlocks = new HashMap<>();
    private Map<Location, Material> player1PlacedBlocks = new HashMap<>();
    private Map<Location, Material> player2PlacedBlocks = new HashMap<>();

    private BukkitRunnable countdownTask;
    private boolean arenaReady = false;

    public MemoryMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.MEMORY_GAME, player1WonPvP);

        playerScores.put(player1UUID, 0);
        playerScores.put(player2UUID, 0);

        initializeStructures();
    }

    private void initializeStructures() {
        structures.add(new MemoryStructure(
                Arrays.asList(
                        new BlockData(0, 0, 0, Material.RED_CONCRETE),
                        new BlockData(1, 0, 0, Material.RED_CONCRETE),
                        new BlockData(0, 0, 1, Material.RED_CONCRETE),
                        new BlockData(1, 0, 1, Material.RED_CONCRETE)
                ),
                "Simple Square"
        ));

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
        MessageUtil.sendMessage(getPlayer1(), "&eMemory Game: onGameStart() called");
        MessageUtil.sendMessage(getPlayer2(), "&eMemory Game: onGameStart() called");

        Bukkit.getPluginManager().registerEvents(this, plugin);
        storePlayerInventories();
        prepareMemoryPlayer(getPlayer1());
        prepareMemoryPlayer(getPlayer2());

        MessageUtil.sendMessage(getPlayer1(), "&eMemory Game: Starting arena setup...");
        MessageUtil.sendMessage(getPlayer2(), "&eMemory Game: Starting arena setup...");

        // Setup arena first, then start game
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
        playerAllowFlight.put(player.getUniqueId(), player.getAllowFlight());
        playerFlying.put(player.getUniqueId(), player.isFlying());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    private void prepareMemoryPlayer(Player player) {
        if (player == null) {
            return;
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    private void setupArenaAndStart() {
        World world = getOrCreateMinigameWorld();
        if (world == null) {
            MessageUtil.sendMessage(getPlayer1(), "&cError: Could not create minigame world!");
            MessageUtil.sendMessage(getPlayer2(), "&cError: Could not create minigame world!");
            return;
        }

        MessageUtil.sendMessage(getPlayer1(), "&eSetup: Generating arena location...");
        MessageUtil.sendMessage(getPlayer2(), "&eSetup: Generating arena location...");

        generateArenaLocation(world);

        MessageUtil.sendMessage(getPlayer1(), "&eSetup: Loading chunks and building arena...");
        MessageUtil.sendMessage(getPlayer2(), "&eSetup: Loading chunks and building arena...");

        // Load chunks synchronously, then build arena
        loadChunksSync(world, centerPlatformLocation, 32);

        // Build arena and start game
        completeArenaSetupAndStart();
    }

    private void generateArenaLocation(World world) {
        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 72;

        centerPlatformLocation = new Location(world, x, y, z);
        player1PlatformLocation = centerPlatformLocation.clone().add(-15, 0, 0);
        player2PlatformLocation = centerPlatformLocation.clone().add(15, 0, 0);

        MessageUtil.sendMessage(getPlayer1(), "&eArena location: " + x + ", " + y + ", " + z);
        MessageUtil.sendMessage(getPlayer2(), "&eArena location: " + x + ", " + y + ", " + z);
    }

    private void completeArenaSetupAndStart() {
        MessageUtil.sendMessage(getPlayer1(), "&eSetup: Arena chunks loaded, building arena...");
        MessageUtil.sendMessage(getPlayer2(), "&eSetup: Arena chunks loaded, building arena...");

        defineBuildAreas();
        buildArena();
        teleportPlayers();

        // Set arena as ready
        arenaReady = true;

        MessageUtil.sendMessage(getPlayer1(), "&aSetup complete! Arena is ready!");
        MessageUtil.sendMessage(getPlayer2(), "&aSetup complete! Arena is ready!");

        // Now start the actual game
        beginGame();
    }

    private void defineBuildAreas() {
        MessageUtil.sendMessage(getPlayer1(), "&eDefining build areas...");
        MessageUtil.sendMessage(getPlayer2(), "&eDefining build areas...");

        // 5x5 build areas
        centerBuildArea = new BoundingBox(
                centerPlatformLocation.getX() - 2,
                centerPlatformLocation.getY() + 1,
                centerPlatformLocation.getZ() - 2,
                centerPlatformLocation.getX() + 2,
                centerPlatformLocation.getY() + 8,
                centerPlatformLocation.getZ() + 2
        );

        player1BuildArea = new BoundingBox(
                player1PlatformLocation.getX() - 2,
                player1PlatformLocation.getY() + 1,
                player1PlatformLocation.getZ() - 2,
                player1PlatformLocation.getX() + 2,
                player1PlatformLocation.getY() + 8,
                player1PlatformLocation.getZ() + 2
        );

        player2BuildArea = new BoundingBox(
                player2PlatformLocation.getX() - 2,
                player2PlatformLocation.getY() + 1,
                player2PlatformLocation.getZ() - 2,
                player2PlatformLocation.getX() + 2,
                player2PlatformLocation.getY() + 8,
                player2PlatformLocation.getZ() + 2
        );

        MessageUtil.sendMessage(getPlayer1(), "&aBuild areas defined!");
        MessageUtil.sendMessage(getPlayer2(), "&aBuild areas defined!");
    }

    private void loadChunksSync(World world, Location center, int radius) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(center.getBlockX() / 16 + dx, center.getBlockZ() / 16 + dz);
                chunk.load(true);
            }
        }

        MessageUtil.sendMessage(getPlayer1(), "&aChunks loaded successfully!");
        MessageUtil.sendMessage(getPlayer2(), "&aChunks loaded successfully!");
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

    private void clearBuildArea(BoundingBox area) {
        World world = getOrCreateMinigameWorld();
        if (world == null || area == null) return;

        for (int x = (int) area.getMinX(); x <= (int) area.getMaxX(); x++) {
            for (int y = (int) area.getMinY(); y <= (int) area.getMaxY(); y++) {
                for (int z = (int) area.getMinZ(); z <= (int) area.getMaxZ(); z++) {
                    // Don't clear the acacia plank floor (Y level = platform Y + 1)
                    Location blockLoc = new Location(world, x, y, z);

                    // Check if this is the floor level for any platform
                    boolean isFloorLevel = (y == centerPlatformLocation.getBlockY() + 1) ||
                            (y == player1PlatformLocation.getBlockY() + 1) ||
                            (y == player2PlatformLocation.getBlockY() + 1);

                    // Check if it's within the 5x5 floor area and is acacia planks
                    boolean isFloorBlock = false;
                    if (isFloorLevel) {
                        // Check if within 5x5 area of any platform
                        boolean withinCenter = Math.abs(x - centerPlatformLocation.getBlockX()) <= 2 &&
                                Math.abs(z - centerPlatformLocation.getBlockZ()) <= 2;
                        boolean withinPlayer1 = Math.abs(x - player1PlatformLocation.getBlockX()) <= 2 &&
                                Math.abs(z - player1PlatformLocation.getBlockZ()) <= 2;
                        boolean withinPlayer2 = Math.abs(x - player2PlatformLocation.getBlockX()) <= 2 &&
                                Math.abs(z - player2PlatformLocation.getBlockZ()) <= 2;

                        if ((withinCenter || withinPlayer1 || withinPlayer2) &&
                                world.getBlockAt(blockLoc).getType() == Material.ACACIA_PLANKS) {
                            isFloorBlock = true;
                        }
                    }

                    // Only clear if it's not a floor block
                    if (!isFloorBlock) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }
    }

    private void buildArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) {
            MessageUtil.sendMessage(getPlayer1(), "&cError: World is null during arena building!");
            MessageUtil.sendMessage(getPlayer2(), "&cError: World is null during arena building!");
            return;
        }

        MessageUtil.sendMessage(getPlayer1(), "&eBuilding platforms...");
        MessageUtil.sendMessage(getPlayer2(), "&eBuilding platforms...");

        buildPlatform(world, centerPlatformLocation, 9, Material.QUARTZ_BLOCK);
        buildPlatform(world, player1PlatformLocation, 9, Material.BLUE_CONCRETE);
        buildPlatform(world, player2PlatformLocation, 9, Material.RED_CONCRETE);

        buildBuildingFloors(world);
        buildInvisibleBarriers(world);

        MessageUtil.sendMessage(getPlayer1(), "&aArena built successfully!");
        MessageUtil.sendMessage(getPlayer2(), "&aArena built successfully!");
    }

    private void buildPlatform(World world, Location center, int size, Material material) {
        int halfSize = size / 2;

        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                world.getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z).setType(material);

                for (int y = 1; y <= 5; y++) {
                    world.getBlockAt(center.getBlockX() + x, center.getBlockY() - y, center.getBlockZ() + z).setType(Material.BARRIER);
                }
            }
        }
    }

    private void buildBuildingFloors(World world) {
        // Build 5x5 acacia plank floors for building areas
        buildBuildingFloor(world, centerPlatformLocation);
        buildBuildingFloor(world, player1PlatformLocation);
        buildBuildingFloor(world, player2PlatformLocation);
    }

    private void buildBuildingFloor(World world, Location center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(center.getBlockX() + x, center.getBlockY() + 1, center.getBlockZ() + z).setType(Material.ACACIA_PLANKS);
            }
        }
    }

    private void buildInvisibleBarriers(World world) {
        // Build barriers around the entire arena area to prevent players from flying away
        int barrierRadius = 20;
        int barrierHeight = 15;

        for (int x = -barrierRadius; x <= barrierRadius; x++) {
            for (int z = -barrierRadius; z <= barrierRadius; z++) {
                for (int y = 0; y <= barrierHeight; y++) {
                    // Only place barriers on the perimeter
                    if (x == -barrierRadius || x == barrierRadius || z == -barrierRadius || z == barrierRadius) {
                        world.getBlockAt(
                                centerPlatformLocation.getBlockX() + x,
                                centerPlatformLocation.getBlockY() + y,
                                centerPlatformLocation.getBlockZ() + z
                        ).setType(Material.BARRIER);
                    }
                }
            }
        }
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player1PlatformLocation != null) {
            Location teleportLoc = player1PlatformLocation.clone().add(0, 1, 0);
            player1.teleport(teleportLoc);
            MessageUtil.sendMessage(player1, "&aTeleported to your platform!");
        } else {
            MessageUtil.sendMessage(getPlayer1(), "&cFailed to teleport Player 1!");
        }

        if (player2 != null && player2PlatformLocation != null) {
            Location teleportLoc = player2PlatformLocation.clone().add(0, 1, 0);
            player2.teleport(teleportLoc);
            MessageUtil.sendMessage(player2, "&aTeleported to your platform!");
        } else {
            MessageUtil.sendMessage(getPlayer2(), "&cFailed to teleport Player 2!");
        }
    }

    private void beginGame() {
        MessageUtil.sendMessage(getPlayer1(), "&eStarting the actual game now...");
        MessageUtil.sendMessage(getPlayer2(), "&eStarting the actual game now...");

        currentRound = 0;

        MessageUtil.sendMessage(getPlayer1(), "&6Memory Mini-Game: Welcome!");
        MessageUtil.sendMessage(getPlayer2(), "&6Memory Mini-Game: Welcome!");

        MessageUtil.sendMessage(getPlayer1(), "&eRules: You will be shown a structure for a short time.");
        MessageUtil.sendMessage(getPlayer2(), "&eRules: You will be shown a structure for a short time.");

        MessageUtil.sendMessage(getPlayer1(), "&eThen, you must rebuild it exactly on your platform.");
        MessageUtil.sendMessage(getPlayer2(), "&eThen, you must rebuild it exactly on your platform.");

        MessageUtil.sendMessage(getPlayer1(), "&eThe viewing time gets shorter each round!");
        MessageUtil.sendMessage(getPlayer2(), "&eThe viewing time gets shorter each round!");

        MessageUtil.sendMessage(getPlayer1(), "&aStarting first round in 5 seconds...");
        MessageUtil.sendMessage(getPlayer2(), "&aStarting first round in 5 seconds...");

        Bukkit.getScheduler().runTaskLater(plugin, this::startNextRound, 100L);
    }

    private void startNextRound() {
        if (!arenaReady || centerPlatformLocation == null) {
            MessageUtil.sendMessage(getPlayer1(), "&cError: Arena not ready for next round!");
            MessageUtil.sendMessage(getPlayer2(), "&cError: Arena not ready for next round!");
            return;
        }

        currentRound++;

        if (currentRound > TOTAL_ROUNDS) {
            determineWinner();
            return;
        }

        MessageUtil.sendMessage(getPlayer1(), "&eClearing build areas...");
        MessageUtil.sendMessage(getPlayer2(), "&eClearing build areas...");

        clearBuildArea(centerBuildArea);
        clearBuildArea(player1BuildArea);
        clearBuildArea(player2BuildArea);

        currentStructureBlocks.clear();
        player1PlacedBlocks.clear();
        player2PlacedBlocks.clear();

        if (getPlayer1() != null) getPlayer1().getInventory().clear();
        if (getPlayer2() != null) getPlayer2().getInventory().clear();

        MessageUtil.sendMessage(getPlayer1(), "&6Round " + currentRound + " of " + TOTAL_ROUNDS);
        MessageUtil.sendMessage(getPlayer2(), "&6Round " + currentRound + " of " + TOTAL_ROUNDS);

        showStructure();
    }

    private void showStructure() {
        if (centerPlatformLocation == null) return;

        currentPhase = GamePhase.SHOWING_STRUCTURE;

        MemoryStructure structure = structures.get(currentRound - 1);

        MessageUtil.sendMessage(getPlayer1(), "&aStructure: " + structure.getName());
        MessageUtil.sendMessage(getPlayer2(), "&aStructure: " + structure.getName());

        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        buildStructureAtCenter(world, structure);

        int viewTime = Math.max(1, INITIAL_VIEW_TIME - (currentRound - 1));

        MessageUtil.sendMessage(getPlayer1(), "&6Memorize this structure! " + viewTime + " seconds...");
        MessageUtil.sendMessage(getPlayer2(), "&6Memorize this structure! " + viewTime + " seconds...");

        startViewingCountdown(viewTime);
    }

    private void buildStructureAtCenter(World world, MemoryStructure structure) {
        // Center the structure properly (structure coordinates are 0-2, so center is at 1,1)
        int structureCenterX = 1;
        int structureCenterZ = 1;

        for (BlockData blockData : structure.getBlocks()) {
            Location loc = centerPlatformLocation.clone().add(
                    blockData.getX() - structureCenterX,  // Center horizontally
                    blockData.getY() + 2,                 // Place on acacia planks (Y+2)
                    blockData.getZ() - structureCenterZ   // Center horizontally
            );

            world.getBlockAt(loc).setType(blockData.getMaterial());
            currentStructureBlocks.put(loc, blockData.getMaterial());
        }
    }

    private void startViewingCountdown(int viewTime) {
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

        hideStructure();
        givePlayersBuildingBlocks();

        MessageUtil.sendMessage(getPlayer1(), "&6Start building! You have " + BUILD_TIME + " seconds!");
        MessageUtil.sendMessage(getPlayer2(), "&6Start building! You have " + BUILD_TIME + " seconds!");

        startBuildingCountdown();
    }

    private void hideStructure() {
        for (Location loc : currentStructureBlocks.keySet()) {
            loc.getBlock().setType(Material.BARRIER);
        }
    }

    private void givePlayersBuildingBlocks() {
        MemoryStructure structure = structures.get(currentRound - 1);
        givePlayerBuildingBlocks(getPlayer1(), structure);
        givePlayerBuildingBlocks(getPlayer2(), structure);
    }

    private void givePlayerBuildingBlocks(Player player, MemoryStructure structure) {
        Map<Material, Integer> requiredBlocks = new HashMap<>();

        for (BlockData blockData : structure.getBlocks()) {
            Material material = blockData.getMaterial();
            requiredBlocks.put(material, requiredBlocks.getOrDefault(material, 0) + 1);
        }

        for (Map.Entry<Material, Integer> entry : requiredBlocks.entrySet()) {
            player.getInventory().addItem(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    private void startBuildingCountdown() {
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

    private void checkResults() {
        currentPhase = GamePhase.CHECKING_RESULTS;

        Map<Location, Material> referenceBlocks1 = generateReferenceBlocks(player1PlatformLocation);
        Map<Location, Material> referenceBlocks2 = generateReferenceBlocks(player2PlatformLocation);

        int player1Correct = countCorrectBlocks(player1PlacedBlocks, referenceBlocks1);
        int player2Correct = countCorrectBlocks(player2PlacedBlocks, referenceBlocks2);

        int totalBlocks = currentStructureBlocks.size();

        updateScoresAndAnnounce(player1Correct, player2Correct, totalBlocks);
        showOriginalStructure();
        scheduleNextRound(player1Correct, player2Correct, totalBlocks);
    }

    private Map<Location, Material> generateReferenceBlocks(Location platformLocation) {
        Map<Location, Material> referenceBlocks = new HashMap<>();

        // Use the same centering logic as the structure building
        int structureCenterX = 1;
        int structureCenterZ = 1;

        for (Map.Entry<Location, Material> entry : currentStructureBlocks.entrySet()) {
            Location centerLoc = entry.getKey();
            Material material = entry.getValue();

            // Calculate the original offset from center platform
            int offsetX = centerLoc.getBlockX() - centerPlatformLocation.getBlockX();
            int offsetY = centerLoc.getBlockY() - centerPlatformLocation.getBlockY();
            int offsetZ = centerLoc.getBlockZ() - centerPlatformLocation.getBlockZ();

            // Apply the same offset to the player platform
            Location playerLoc = new Location(
                    centerLoc.getWorld(),
                    platformLocation.getBlockX() + offsetX,
                    platformLocation.getBlockY() + offsetY,
                    platformLocation.getBlockZ() + offsetZ
            );

            referenceBlocks.put(playerLoc, material);
        }

        return referenceBlocks;
    }

    private void updateScoresAndAnnounce(int player1Correct, int player2Correct, int totalBlocks) {
        MessageUtil.sendMessage(getPlayer1(), "&6You placed &a" + player1Correct + "&6 of &e" + totalBlocks + "&6 blocks correctly.");
        MessageUtil.sendMessage(getPlayer2(), "&6You placed &a" + player2Correct + "&6 of &e" + totalBlocks + "&6 blocks correctly.");

        playerScores.put(player1UUID, playerScores.get(player1UUID) + player1Correct);
        playerScores.put(player2UUID, playerScores.get(player2UUID) + player2Correct);
    }

    private void showOriginalStructure() {
        for (Map.Entry<Location, Material> entry : currentStructureBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
    }

    private void scheduleNextRound(int player1Correct, int player2Correct, int totalBlocks) {
        boolean bothPerfect = (player1Correct == totalBlocks && player2Correct == totalBlocks);

        if (bothPerfect) {
            MessageUtil.sendMessage(getPlayer1(), "&aBoth players built the structure perfectly! Moving to next round.");
            MessageUtil.sendMessage(getPlayer2(), "&aBoth players built the structure perfectly! Moving to next round.");
        } else {
            announceRoundWinner(player1Correct, player2Correct);
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::startNextRound, 60L);
    }

    private void announceRoundWinner(int player1Correct, int player2Correct) {
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
    }

    private int countCorrectBlocks(Map<Location, Material> placedBlocks, Map<Location, Material> referenceBlocks) {
        int correctCount = 0;

        for (Map.Entry<Location, Material> entry : referenceBlocks.entrySet()) {
            Location loc = entry.getKey();
            Material expectedMaterial = entry.getValue();

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
            winnerUUID = ThreadLocalRandom.current().nextBoolean() ? player1UUID : player2UUID;
        }

        MessageUtil.sendMessage(getPlayer1(), "&6Final score: &eYou: " + player1TotalScore + " - Opponent: " + player2TotalScore);
        MessageUtil.sendMessage(getPlayer2(), "&6Final score: &eYou: " + player2TotalScore + " - Opponent: " + player1TotalScore);

        endGame(winnerUUID);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!isParticipant(player)) return;

        if (currentPhase != GamePhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        Location placeLoc = event.getBlock().getLocation();

        if (player.getUniqueId().equals(player1UUID)) {
            if (!player1BuildArea.contains(placeLoc.getX(), placeLoc.getY(), placeLoc.getZ())) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, "&cYou can only build within your designated area!");
                return;
            }

            player1PlacedBlocks.put(placeLoc, event.getBlock().getType());
        } else if (player.getUniqueId().equals(player2UUID)) {
            if (!player2BuildArea.contains(placeLoc.getX(), placeLoc.getY(), placeLoc.getZ())) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, "&cYou can only build within your designated area!");
                return;
            }

            player2PlacedBlocks.put(placeLoc, event.getBlock().getType());
        }
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

        if (currentPhase == GamePhase.BUILDING) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!isParticipant(player)) return;

        Location breakLoc = event.getBlock().getLocation();

        if (breakLoc.getY() == player1PlatformLocation.getY() ||
                breakLoc.getY() == player2PlatformLocation.getY() ||
                breakLoc.getY() == centerPlatformLocation.getY()) {
            event.setCancelled(true);
            return;
        }

        if (currentPhase != GamePhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        if (player.getUniqueId().equals(player1UUID)) {
            if (!player1BuildArea.contains(breakLoc.getX(), breakLoc.getY(), breakLoc.getZ())) {
                event.setCancelled(true);
                return;
            }

            player1PlacedBlocks.remove(breakLoc);
            event.setDropItems(false);
            player.getInventory().addItem(new ItemStack(event.getBlock().getType()));
        } else if (player.getUniqueId().equals(player2UUID)) {
            if (!player2BuildArea.contains(breakLoc.getX(), breakLoc.getY(), breakLoc.getZ())) {
                event.setCancelled(true);
                return;
            }

            player2PlacedBlocks.remove(breakLoc);
            event.setDropItems(false);
            player.getInventory().addItem(new ItemStack(event.getBlock().getType()));
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
        player.setAllowFlight(playerAllowFlight.getOrDefault(playerUUID, false));
        player.setFlying(playerFlying.getOrDefault(playerUUID, false));

        playerInventories.remove(playerUUID);
        playerArmorContents.remove(playerUUID);
        playerGameModes.remove(playerUUID);
        playerAllowFlight.remove(playerUUID);
        playerFlying.remove(playerUUID);
    }

    @Override
    public void endGame(UUID winnerUUID) {
        currentPhase = GamePhase.GAME_OVER;

        if (countdownTask != null) {
            countdownTask.cancel();
        }

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player2 != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                MessageUtil.sendMessage(player1, "&6Memory Mini-Game has ended! &aWinner: &e" + winner.getName());
                MessageUtil.sendMessage(player2, "&6Memory Mini-Game has ended! &aWinner: &e" + winner.getName());
            }
        }

        restorePlayerInventories();
        super.endGame(winnerUUID);

        BlockPlaceEvent.getHandlerList().unregister(this);
        BlockBreakEvent.getHandlerList().unregister(this);
        BlockDamageEvent.getHandlerList().unregister(this);
        PlayerMoveEvent.getHandlerList().unregister(this);
    }

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
