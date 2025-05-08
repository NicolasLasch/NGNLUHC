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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final int courseLength = 90;
    private final int courseWidth = 5;
    private final int courseHeight = 20;
    private final int totalCheckpoints = 5;
    private final int MAX_JUMP_DISTANCE = 4;

    private final Map<Integer, Location> player1Checkpoints = new HashMap<>();
    private final Map<Integer, Location> player2Checkpoints = new HashMap<>();

    private static final String CHECKPOINT_ITEM_NAME = ChatColor.GREEN + "Return to Checkpoint";
    private static final String FORFEIT_ITEM_NAME = ChatColor.RED + "Forfeit Race";

    public class ParkourSection {
        int type;
        int startZ;
        int endZ;
        int heightOffset;

        public ParkourSection(int type, int startZ, int endZ, int heightOffset) {
            this.type = type;
            this.startZ = startZ;
            this.endZ = endZ;
            this.heightOffset = heightOffset;
        }
    }

    private final List<Integer> checkpointPositions = new ArrayList<>();
    private List<ParkourSection> courseSections = new ArrayList<>();

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

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void giveUtilityItems(Player player) {
        ItemStack checkpointItem = createCheckpointItem();
        ItemStack forfeitItem = createForfeitItem();

        player.getInventory().setItem(7, checkpointItem);
        player.getInventory().setItem(8, forfeitItem);
    }

    private ItemStack createCheckpointItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(CHECKPOINT_ITEM_NAME);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click to return to your last checkpoint");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createForfeitItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(FORFEIT_ITEM_NAME);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click to forfeit the race");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void setupParkourCourse() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100;

        courseStartLocation = new Location(world, x, y, z);

        calculateCheckpointPositions();
        generateCourseDesign();

        preloadChunksAndThen(world, courseStartLocation, Math.max(courseLength, courseWidth) * 2, () -> {
            buildParkourCourse(world, courseStartLocation);
            setupCheckpoints(world);
            teleportPlayersToStart();
            giveUtilityItems(getPlayer1());
            giveUtilityItems(getPlayer2());
            showInstructions();
            resetPlayerProgress();
            startCountdown();
        });
    }

    private void calculateCheckpointPositions() {
        checkpointPositions.clear();

        int segmentLength = courseLength / (totalCheckpoints - 1);

        for (int i = 0; i < totalCheckpoints; i++) {
            if (i == 0) {
                checkpointPositions.add(0); // Start position
            } else if (i == totalCheckpoints - 1) {
                checkpointPositions.add(courseLength); // Finish line
            } else {
                checkpointPositions.add(i * segmentLength);
            }
        }
    }

    private void generateCourseDesign() {
        courseSections.clear();

        int prevSectionType = -1;
        int currentZ = 3; // Start after platform
        int currentY = 0;  // Relative height from start

        while (currentZ < courseLength) {
            // Randomize section length, but ensure it's reasonable
            int sectionLength = 8 + ThreadLocalRandom.current().nextInt(8); // 8-15 blocks
            int nextZ = Math.min(currentZ + sectionLength, courseLength);

            // Avoid slime block sections too close to each other
            int sectionType;
            do {
                sectionType = ThreadLocalRandom.current().nextInt(5); // 0-4 (removed ice slide section)
            } while (sectionType == prevSectionType || (prevSectionType == 4 && sectionType == 4)); // Never repeat slime jumps

            ParkourSection section = new ParkourSection(sectionType, currentZ, nextZ, currentY);
            courseSections.add(section);

            // Update height for next section
            if (sectionType == 1) { // Stair climb
                int steps = (nextZ - currentZ) / 2;
                currentY += steps;
            } else if (sectionType == 2) { // Downward path
                int steps = (nextZ - currentZ) / 2;
                currentY = Math.max(0, currentY - steps);
            }

            prevSectionType = sectionType;
            currentZ = nextZ;
        }
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
        buildCoursesFromDesign(world, startX, startY, startZ);
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
        // Player 1 side - left
        for (int x = -courseWidth; x <= -1; x++) {
            for (int z = -1; z <= 2; z++) {
                world.getBlockAt(startX + x, startY, startZ + z).setType(Material.QUARTZ_BLOCK);
            }
        }

        // Player 2 side - right
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

    private void buildCoursesFromDesign(World world, int startX, int startY, int startZ) {
        // Generate a seed for the course
        long courseSeed = System.currentTimeMillis();

        // Build course on both sides using the same seed
        for (ParkourSection section : courseSections) {
            buildSection(world, startX - (courseWidth / 2), startY, startZ, section, courseSeed);
            buildSection(world, startX + (courseWidth / 2), startY, startZ, section, courseSeed);
        }
    }

    private void buildSection(World world, int centerX, int baseY, int startZ, ParkourSection section, long seed) {
        int sectionStartZ = startZ + section.startZ;
        int sectionEndZ = startZ + section.endZ;
        int sectionY = baseY + section.heightOffset;

        switch (section.type) {
            case 0: // Jumping blocks
                buildJumpingBlocksSection(world, centerX, sectionY, startZ, sectionStartZ, sectionEndZ, seed);
                break;
            case 1: // Stair climb
                buildStairClimbSection(world, centerX, sectionY, startZ, sectionStartZ, sectionEndZ, seed);
                break;
            case 2: // Downward path
                buildDownwardPathSection(world, centerX, sectionY, startZ, sectionStartZ, sectionEndZ, seed);
                break;
            case 3: // Single block jumps
                buildSingleBlockJumpsSection(world, centerX, sectionY, startZ, sectionStartZ, sectionEndZ, seed);
                break;
            case 4: // Slime jumps
                buildSlimeJumpsSection(world, centerX, sectionY, startZ, sectionStartZ, sectionEndZ, seed);
                break;
        }
    }

    private void buildJumpingBlocksSection(World world, int centerX, int y, int baseZ, int startZ, int endZ, long seed) {
        Random random = new Random(seed + startZ * 31);
        int[] xOffsets = {0, 1, -1, 0, 2, 1, 0, -1, -2};

        int lastX = centerX;
        int lastZ = startZ;

        for (int z = startZ; z < endZ; z += 2) {
            int xOffset = xOffsets[random.nextInt(xOffsets.length)];
            int blockX = centerX + xOffset;

            // Ensure jumps are never more than MAX_JUMP_DISTANCE blocks apart
            double distance = Math.sqrt(Math.pow(blockX - lastX, 2) + Math.pow(z - lastZ, 2));
            if (distance > MAX_JUMP_DISTANCE) {
                // Place an intermediary block if the jump is too far
                int midZ = (z + lastZ) / 2;
                world.getBlockAt(lastX, y, baseZ + midZ).setType(Material.OAK_PLANKS);
            }

            world.getBlockAt(blockX, y, baseZ + z).setType(Material.OAK_PLANKS);

            lastX = blockX;
            lastZ = z;
        }
    }

    private void buildStairClimbSection(World world, int centerX, int y, int baseZ, int startZ, int endZ, long seed) {
        Random random = new Random(seed + startZ * 31);
        int[] xOffsets = {0, 1, -1, 2, 0, -2, 1};

        int currentY = y;
        int lastX = centerX;
        int lastZ = startZ;

        for (int z = startZ; z < endZ; z += 2) {
            int xOffset = xOffsets[random.nextInt(xOffsets.length)];
            int blockX = centerX + xOffset;

            currentY++;

            // Ensure jumps are never more than MAX_JUMP_DISTANCE blocks apart
            double distance = Math.sqrt(Math.pow(blockX - lastX, 2) + Math.pow(z - lastZ, 2) + Math.pow(1, 2));
            if (distance > MAX_JUMP_DISTANCE) {
                // Place an intermediary block if the jump is too far
                int midZ = (z + lastZ) / 2;
                int midY = (currentY + (currentY - 1)) / 2;
                world.getBlockAt(lastX, midY, baseZ + midZ).setType(Material.STONE_BRICKS);
            }

            world.getBlockAt(blockX, currentY, baseZ + z).setType(Material.STONE_BRICKS);

            lastX = blockX;
            lastZ = z;
        }
    }

    private void buildDownwardPathSection(World world, int centerX, int y, int baseZ, int startZ, int endZ, long seed) {
        if (y <= 3) return; // Don't go too low

        Random random = new Random(seed + startZ * 31);
        int[] xOffsets = {0, 1, -1, 2, 0, -2, 1};

        int currentY = y;
        int lastX = centerX;
        int lastZ = startZ;

        for (int z = startZ; z < endZ; z += 2) {
            int xOffset = xOffsets[random.nextInt(xOffsets.length)];
            int blockX = centerX + xOffset;

            currentY = Math.max(0, currentY - 1);

            // Ensure jumps are never more than MAX_JUMP_DISTANCE blocks apart
            double distance = Math.sqrt(Math.pow(blockX - lastX, 2) + Math.pow(z - lastZ, 2) + Math.pow(1, 2));
            if (distance > MAX_JUMP_DISTANCE) {
                // Place an intermediary block if the jump is too far
                int midZ = (z + lastZ) / 2;
                int midY = (currentY + (currentY + 1)) / 2;
                world.getBlockAt(lastX, midY, baseZ + midZ).setType(Material.PRISMARINE_BRICKS);
            }

            world.getBlockAt(blockX, currentY, baseZ + z).setType(Material.PRISMARINE_BRICKS);

            lastX = blockX;
            lastZ = z;
        }
    }

    private void buildSingleBlockJumpsSection(World world, int centerX, int y, int baseZ, int startZ, int endZ, long seed) {
        Random random = new Random(seed + startZ * 31);
        int[] xOffsets = {0, 1, -1, 2, 0, -2, 1};

        int lastX = centerX;
        int lastZ = startZ;

        for (int z = startZ; z < endZ; z += 3) {
            int xOffset = xOffsets[random.nextInt(xOffsets.length)];
            int blockX = centerX + xOffset;

            // Ensure jumps are never more than MAX_JUMP_DISTANCE blocks apart
            double distance = Math.sqrt(Math.pow(blockX - lastX, 2) + Math.pow(z - lastZ, 2));
            if (distance > MAX_JUMP_DISTANCE) {
                // Place an intermediary block if the jump is too far
                int midZ = (z + lastZ) / 2;
                world.getBlockAt((lastX + blockX) / 2, y, baseZ + midZ).setType(Material.BIRCH_PLANKS);
            }

            world.getBlockAt(blockX, y, baseZ + z).setType(Material.BIRCH_PLANKS);

            lastX = blockX;
            lastZ = z;
        }
    }

    private void buildSlimeJumpsSection(World world, int centerX, int y, int baseZ, int startZ, int endZ, long seed) {
        Random random = new Random(seed + startZ * 31);
        int[] xOffsets = {0, 1, -1, 0, 2};

        int lastX = centerX;
        int lastZ = startZ;

        // Place a regular block at the start to give players a chance to prepare
        world.getBlockAt(centerX, y, baseZ + startZ).setType(Material.OAK_PLANKS);

        // Then place slime blocks with extra space between them
        for (int z = startZ + 5; z < endZ; z += 6) {
            int xOffset = xOffsets[random.nextInt(xOffsets.length)];
            int blockX = centerX + xOffset;

            // Ensure jumps are never more than MAX_JUMP_DISTANCE blocks apart
            // For slime blocks, we allow slightly larger jumps due to bounce
            double distance = Math.sqrt(Math.pow(blockX - lastX, 2) + Math.pow(z - lastZ, 2));
            if (distance > MAX_JUMP_DISTANCE + 2) {
                // Place an intermediary block if the jump is too far
                int midZ = (z + lastZ) / 2;
                world.getBlockAt((lastX + blockX) / 2, y, baseZ + midZ).setType(Material.OAK_PLANKS);
            }

            world.getBlockAt(blockX, y, baseZ + z).setType(Material.SLIME_BLOCK);

            lastX = blockX;
            lastZ = z;
        }

        // Place a regular block at the end to help transition to next section
        world.getBlockAt(lastX, y, baseZ + endZ - 1).setType(Material.OAK_PLANKS);
    }

    private void buildFinishLine(World world, int startX, int startY, int startZ) {
        // Left side finish platform
        for (int x = -courseWidth; x <= -1; x++) {
            for (int z = courseLength; z <= courseLength + 3; z++) {
                world.getBlockAt(startX + x, startY, startZ + z).setType(Material.GOLD_BLOCK);
            }
        }

        // Right side finish platform
        for (int x = 1; x <= courseWidth; x++) {
            for (int z = courseLength; z <= courseLength + 3; z++) {
                world.getBlockAt(startX + x, startY, startZ + z).setType(Material.GOLD_BLOCK);
            }
        }

        // Victory decorations
        for (int x = -courseWidth; x <= courseWidth; x++) {
            if (x == 0) continue; // Skip the dividing wall
            world.getBlockAt(startX + x, startY + 3, startZ + courseLength + 2).setType(Material.GLOWSTONE);
        }
    }

    private void setupCheckpoints(World world) {
        int startX = courseStartLocation.getBlockX();
        int startY = courseStartLocation.getBlockY();
        int startZ = courseStartLocation.getBlockZ();

        // Set start checkpoints (checkpoint 0)
        player1Checkpoints.put(0, new Location(
                world,
                startX - (courseWidth / 2),
                startY + 1,
                startZ + 1
        ));

        player2Checkpoints.put(0, new Location(
                world,
                startX + (courseWidth / 2),
                startY + 1,
                startZ + 1
        ));

        // Set finish line checkpoints (last checkpoint)
        player1Checkpoints.put(totalCheckpoints - 1, new Location(
                world,
                startX - (courseWidth / 2),
                startY + 1,
                startZ + courseLength + 1
        ));

        player2Checkpoints.put(totalCheckpoints - 1, new Location(
                world,
                startX + (courseWidth / 2),
                startY + 1,
                startZ + courseLength + 1
        ));

        // Set intermediate checkpoints
        for (int i = 1; i < totalCheckpoints - 1; i++) {
            int checkpointZ = checkpointPositions.get(i);

            // Find the height at this position by checking the course sections
            int checkpointY = getHeightAtPosition(checkpointZ);

            // Make sure there's a block under the checkpoint (fix for teleport issues)
            world.getBlockAt(startX - (courseWidth / 2), startY + checkpointY, startZ + checkpointZ).setType(Material.EMERALD_BLOCK);
            world.getBlockAt(startX + (courseWidth / 2), startY + checkpointY, startZ + checkpointZ).setType(Material.EMERALD_BLOCK);

            // Make checkpoint blocks 2 blocks long for easier hitting
            world.getBlockAt(startX - (courseWidth / 2), startY + checkpointY, startZ + checkpointZ + 1).setType(Material.EMERALD_BLOCK);
            world.getBlockAt(startX + (courseWidth / 2), startY + checkpointY, startZ + checkpointZ + 1).setType(Material.EMERALD_BLOCK);

            // Record checkpoint locations (1 block above the emerald blocks)
            player1Checkpoints.put(i, new Location(
                    world,
                    startX - (courseWidth / 2),
                    startY + checkpointY + 1,
                    startZ + checkpointZ
            ));

            player2Checkpoints.put(i, new Location(
                    world,
                    startX + (courseWidth / 2),
                    startY + checkpointY + 1,
                    startZ + checkpointZ
            ));
        }
    }

    private int getHeightAtPosition(int z) {
        for (ParkourSection section : courseSections) {
            if (z >= section.startZ && z <= section.endZ) {
                return section.heightOffset;
            }
        }
        return 0;
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

        MessageUtil.sendMessage(player1, "&7• Use the &aEnder Pearl &7to return to your last checkpoint");
        MessageUtil.sendMessage(player2, "&7• Use the &aEnder Pearl &7to return to your last checkpoint");

        MessageUtil.sendMessage(player1, "&7• Use the &cBarrier &7to forfeit if you're stuck");
        MessageUtil.sendMessage(player2, "&7• Use the &cBarrier &7to forfeit if you're stuck");

        MessageUtil.sendMessage(player1, "&7• Reach the &6Gold Finish Line &7first to win!");
        MessageUtil.sendMessage(player2, "&7• Reach the &6Gold Finish Line &7first to win!");

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

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameActive) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

        // Only handle right clicks with items
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String displayName = meta.getDisplayName();

        if (displayName.equals(CHECKPOINT_ITEM_NAME)) {
            event.setCancelled(true);
            teleportToLastCheckpoint(player);
        } else if (displayName.equals(FORFEIT_ITEM_NAME)) {
            event.setCancelled(true);
            handleForfeit(player);
        }
    }

    private void handleForfeit(Player player) {
        UUID playerUUID = player.getUniqueId();
        UUID opponentUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;

        Player opponent = Bukkit.getPlayer(opponentUUID);

        if (opponent != null) {
            MessageUtil.sendMessage(player, "&cYou have forfeited the race!");
            MessageUtil.sendMessage(opponent, "&aYour opponent has forfeited the race! You win!");

            endGame(opponentUUID);
        }
    }

    private void handleCheckpoints(Player player) {
        UUID playerUUID = player.getUniqueId();
        int currentCheckpoint = playerCheckpoints.getOrDefault(playerUUID, 0);
        Map<Integer, Location> checkpoints = playerUUID.equals(player1UUID) ? player1Checkpoints : player2Checkpoints;
        Block blockBelow = player.getLocation().subtract(0, 1, 0).getBlock();

        if (blockBelow.getType() == Material.EMERALD_BLOCK) {
            // Check all checkpoints (except finish line)
            for (int i = 1; i < totalCheckpoints - 1; i++) {
                Location checkpointLoc = checkpoints.get(i);

                // Check if the emerald block is at this checkpoint position (or one block ahead)
                if (isBlockNearCheckpoint(blockBelow, checkpointLoc.clone().subtract(0, 1, 0))) {
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
            if (hasAllCheckpoints(playerUUID)) {
                handleFinish(player);
            } else {
                MessageUtil.sendMessage(player, "&cYou must hit all checkpoints before finishing!");
            }
        }
    }

    private boolean hasAllCheckpoints(UUID playerUUID) {
        int playerCheckpoint = playerCheckpoints.getOrDefault(playerUUID, 0);
        return playerCheckpoint >= totalCheckpoints - 2;
    }

    private boolean isBlockNearCheckpoint(Block block, Location checkpointLoc) {
        // Check the exact position
        if (block.getX() == checkpointLoc.getBlockX() &&
                block.getY() == checkpointLoc.getBlockY() &&
                block.getZ() == checkpointLoc.getBlockZ()) {
            return true;
        }

        // Also check one block ahead (for 2-block wide checkpoints)
        if (block.getX() == checkpointLoc.getBlockX() &&
                block.getY() == checkpointLoc.getBlockY() &&
                block.getZ() == checkpointLoc.getBlockZ() + 1) {
            return true;
        }

        return false;
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

        // Check if player already finished
        if (playerCompletionTimes.containsKey(playerUUID)) {
            return;
        }

        // Record completion time
        long finishTime = System.currentTimeMillis();
        long startTime = playerStartTimes.getOrDefault(playerUUID, finishTime);
        long completionTime = finishTime - startTime;
        playerCompletionTimes.put(playerUUID, completionTime);

        // Show finish message
        String timeString = formatTime(completionTime);
        MessageUtil.sendMessage(player, "&6You finished the parkour in " + timeString + "!");

        // Visual and sound effects
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        createFinishFireworks(player.getLocation());

        // Check if this ends the race
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

        world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.add(0, 1, 0), 50, 1, 1, 1, 0.3);
        world.spawnParticle(Particle.FLAME, location, 30, 0.5, 0.5, 0.5, 0.05);
    }

    private void checkRaceEnd() {
        boolean player1Finished = playerCompletionTimes.containsKey(player1UUID);
        boolean player2Finished = playerCompletionTimes.containsKey(player2UUID);

        if (player1Finished && player2Finished) {
            // Both players finished - compare times
            long player1Time = playerCompletionTimes.get(player1UUID);
            long player2Time = playerCompletionTimes.get(player2UUID);

            if (player1Time <= player2Time) {
                endGame(player1UUID);
            } else {
                endGame(player2UUID);
            }
            return;
        }

        // If only one player finished, start timeout for the other
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

                // Cancel timeout if player finished during countdown
                if (playerCompletionTimes.containsKey(playerUUID)) {
                    this.cancel();
                    checkRaceEnd(); // Re-check in case both finished now
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);

                // Time's up
                if (countdown <= 0) {
                    this.cancel();

                    // Determine winner (the player who did finish)
                    UUID winnerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;
                    endGame(winnerUUID);

                    if (player != null) {
                        MessageUtil.sendMessage(player, "&cTime's up! Your opponent wins the race.");
                    }
                    return;
                }

                // Countdown warnings
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

        // Call parent method to handle the game ending logic
        super.endGame(winnerUUID);

        // Display result messages
        if (player1 != null && player2 != null && winner != null) {
            // Main win announcement
            String winnerName = winner.getName();
            MessageUtil.sendMessage(player1, "&6Parkour Race has ended! &aWinner: &e" + winnerName);
            MessageUtil.sendMessage(player2, "&6Parkour Race has ended! &aWinner: &e" + winnerName);

            // Show completion times if available
            if (playerCompletionTimes.containsKey(player1UUID)) {
                String timeStr = formatTime(playerCompletionTimes.get(player1UUID));
                MessageUtil.sendMessage(player1, "&eYour time: &b" + timeStr);
                MessageUtil.sendMessage(player2, "&ePlayer 1's time: &b" + timeStr);
            } else {
                MessageUtil.sendMessage(player1, "&eYou did not finish the course.");
                MessageUtil.sendMessage(player2, "&ePlayer 1 did not finish the course.");
            }

            if (playerCompletionTimes.containsKey(player2UUID)) {
                String timeStr = formatTime(playerCompletionTimes.get(player2UUID));
                MessageUtil.sendMessage(player1, "&ePlayer 2's time: &b" + timeStr);
                MessageUtil.sendMessage(player2, "&eYour time: &b" + timeStr);
            } else {
                MessageUtil.sendMessage(player1, "&ePlayer 2 did not finish the course.");
                MessageUtil.sendMessage(player2, "&eYou did not finish the course.");
            }

            // Play sounds
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            Player loser = winner.equals(player1) ? player2 : player1;
            if (loser != null) {
                loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }

        // Cleanup and return players
        teleportToSafeLocation();
        restorePlayerInventories();

        // Unregister events
        PlayerMoveEvent.getHandlerList().unregister(this);
        PlayerInteractEvent.getHandlerList().unregister(this);
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