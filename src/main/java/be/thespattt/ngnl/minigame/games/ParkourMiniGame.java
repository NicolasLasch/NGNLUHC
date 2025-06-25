package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

// Working
// TODO : Fix some weird obstacles and add more
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

    private final Map<Integer, Location> player1Checkpoints = new HashMap<>();
    private final Map<Integer, Location> player2Checkpoints = new HashMap<>();

    private static final String CHECKPOINT_ITEM_NAME = ChatColor.GREEN + "Return to Checkpoint";
    private static final String FORFEIT_ITEM_NAME = ChatColor.RED + "Forfeit Race";

    private final List<Integer> checkpointPositions = new ArrayList<>();
    private final List<ParkourSection> courseSections = new ArrayList<>();

    private static final int SIMPLE_JUMPS = 0;
    private static final int SLIME_BOUNCE = 1;
    private static final int ICE_PATH = 2;
    private static final int FENCE_JUMPS = 3;
    private static final int LADDER_CLIMB = 4;

    public class ParkourSection {
        int type;
        int startZ;
        int endZ;
        int baseY;

        public ParkourSection(int type, int startZ, int endZ, int baseY) {
            this.type = type;
            this.startZ = startZ;
            this.endZ = endZ;
            this.baseY = baseY;
        }
    }

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

        generateCourseLocation();
        calculateCheckpointPositions();
        generateCourseDesign();

        preloadChunksAndThen(world, courseStartLocation, Math.max(courseLength, courseWidth) * 2, () -> {
            buildParkourCourse(world);
            setupCheckpoints(world);
            teleportPlayersToStart();
            giveUtilityItems(getPlayer1());
            giveUtilityItems(getPlayer2());
            showInstructions();
            resetPlayerProgress();
            startCountdown();
        });
    }

    private void generateCourseLocation() {
        World world = getOrCreateMinigameWorld();
        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100;
        courseStartLocation = new Location(world, x, y, z);
    }

    private void calculateCheckpointPositions() {
        checkpointPositions.clear();
        int segmentLength = courseLength / (totalCheckpoints - 1);

        for (int i = 0; i < totalCheckpoints; i++) {
            if (i == 0) {
                checkpointPositions.add(0);
            } else if (i == totalCheckpoints - 1) {
                checkpointPositions.add(courseLength);
            } else {
                checkpointPositions.add(i * segmentLength);
            }
        }
    }

    private void generateCourseDesign() {
        courseSections.clear();
        Random random = new Random();
        int lastObstacleType = -1;

        int sectionsPerCheckpoint = 3;
        int totalSections = (totalCheckpoints - 1) * sectionsPerCheckpoint;

        for (int i = 0; i < totalSections; i++) {
            int checkpointIndex = i / sectionsPerCheckpoint;
            int sectionInCheckpoint = i % sectionsPerCheckpoint;

            int checkpointStart = checkpointPositions.get(checkpointIndex);
            int checkpointEnd = checkpointPositions.get(checkpointIndex + 1);

            // Reduce available space to account for 3-block gaps
            int availableSpace = checkpointEnd - checkpointStart - (sectionsPerCheckpoint * 3);
            int sectionLength = Math.max(10, availableSpace / sectionsPerCheckpoint); // Minimum 5 blocks per section

            // Calculate actual start/end with 3-block spacing
            int sectionStart = checkpointStart + 3 + (sectionInCheckpoint * (sectionLength + 3));
            int sectionEnd = sectionStart + sectionLength;

            // Ensure we don't go past checkpoint
            if (sectionEnd > checkpointEnd - 3) {
                sectionEnd = checkpointEnd - 3;
            }

            int obstacleType;
            do {
                obstacleType = random.nextInt(5);
            } while (obstacleType == lastObstacleType);

            lastObstacleType = obstacleType;
            int baseY = courseStartLocation.getBlockY();

            ParkourSection section = new ParkourSection(obstacleType, sectionStart, sectionEnd, baseY);
            courseSections.add(section);
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

    private void buildParkourCourse(World world) {
        int startX = courseStartLocation.getBlockX();
        int startY = courseStartLocation.getBlockY();
        int startZ = courseStartLocation.getBlockZ();

        clearArea(world, startX, startY, startZ);
        buildStartingPlatform(world, startX, startY, startZ);
        buildDividingWall(world, startX, startY, startZ);
        buildCoursesFromDesign(world, startX, startY, startZ);
        buildFinishLine(world, startX, startY, startZ);
    }

    private void clearArea(World world, int startX, int startY, int startZ) {
        for (int x = -courseWidth - 5; x <= courseWidth * 2 + 5; x++) {
            for (int z = -5; z <= courseLength + 5; z++) {
                for (int y = -15; y <= courseHeight + 5; y++) {
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
        for (int z = -5; z <= courseLength + 5; z++) {
            for (int y = 1; y <= courseHeight + 3; y++) {
                world.getBlockAt(startX, startY + y, startZ + z).setType(Material.BARRIER);
            }
        }
    }

    private void buildCoursesFromDesign(World world, int startX, int startY, int startZ) {
        int leftCenterX = startX - (courseWidth / 2);
        int rightCenterX = startX + (courseWidth / 2);

        for (ParkourSection section : courseSections) {
            buildObstacleSection(world, leftCenterX, startY, startZ, section);
            buildObstacleSection(world, rightCenterX, startY, startZ, section);
        }
    }

    private void buildObstacleSection(World world, int centerX, int baseY, int baseZ, ParkourSection section) {
        switch (section.type) {
            case SIMPLE_JUMPS:
                buildSimpleJumps(world, centerX, baseY, baseZ, section);
                break;
//            case CLIMBING_WALL:
//                buildClimbingWall(world, centerX, baseY, baseZ, section);
//                break;
            case SLIME_BOUNCE:
                buildSlimeBounce(world, centerX, baseY, baseZ, section);
                break;
//            case LAVA_PARKOUR:
//                buildLavaParkour(world, centerX, baseY, baseZ, section);
//                break;
            case ICE_PATH:
                buildIcePath(world, centerX, baseY, baseZ, section);
                break;
            case FENCE_JUMPS:
                buildFenceJumps(world, centerX, baseY, baseZ, section);
                break;
//            case WATER_SWIM:
//                buildWaterSwim(world, centerX, baseY, baseZ, section);
//                break;
            case LADDER_CLIMB:
                buildLadderClimb(world, centerX, baseY, baseZ, section);
                break;
        }
    }

    private void buildSimpleJumps(World world, int centerX, int y, int baseZ, ParkourSection section) {
        Random random = new Random();
        int[] xOffsets = {-1, 0, 1};

        int currentZ = section.startZ + 1; // Start at Z+1 to avoid checkpoint conflicts
        int lastXOffset = 0; // Track last X position to prevent adjacent blocks

        // Place starting block
        world.getBlockAt(centerX, y, baseZ + currentZ).setType(Material.MAGENTA_STAINED_GLASS);

        while (currentZ < section.endZ - 2) { // -2 to ensure space for gaps
            int jumpGap = 1 + random.nextInt(4); // 1-4 block gaps
            currentZ += jumpGap + 1;

            if (currentZ >= section.endZ - 1) {
                currentZ = section.endZ - 1;
            }

            // Choose X offset that's different from last position (no adjacent blocks)
            int xOffset;
            do {
                xOffset = xOffsets[random.nextInt(xOffsets.length)];
            } while (xOffset == lastXOffset && jumpGap == 1); // Only avoid if it's a 1-block gap

            world.getBlockAt(centerX + xOffset, y, baseZ + currentZ).setType(Material.MAGENTA_STAINED_GLASS);
            lastXOffset = xOffset;

            // Height variation (less frequent to avoid adjacency issues)
            if (random.nextInt(6) == 0 && currentZ < section.endZ - 3) {
                int heightVariation = random.nextBoolean() ? 1 : -1;
                currentZ += 2; // Ensure gap
                int newXOffset = xOffsets[random.nextInt(xOffsets.length)];
                world.getBlockAt(centerX + newXOffset, y + heightVariation, baseZ + currentZ).setType(Material.MAGENTA_STAINED_GLASS);
                lastXOffset = newXOffset;
            }
        }

        // End block
        world.getBlockAt(centerX, y, baseZ + section.endZ - 1).setType(Material.MAGENTA_STAINED_GLASS);
    }

    private void buildSlimeBounce(World world, int centerX, int y, int baseZ, ParkourSection section) {
        // Build 5-block high pillar at start with ladder
        for (int yOffset = 0; yOffset <= 5; yOffset++) {
            world.getBlockAt(centerX, y + yOffset, baseZ + section.startZ).setType(Material.MAGENTA_STAINED_GLASS);

            // Add ladder on the side
            if (yOffset > 0) {
                Block ladderBlock = world.getBlockAt(centerX + 1, y + yOffset, baseZ + section.startZ);
                ladderBlock.setType(Material.LADDER);
                org.bukkit.block.data.Directional ladderData = (org.bukkit.block.data.Directional) ladderBlock.getBlockData();
                ladderData.setFacing(BlockFace.EAST);
                ladderBlock.setBlockData(ladderData);
            }
        }

        world.getBlockAt(centerX, y, baseZ + section.startZ + 2).setType(Material.SLIME_BLOCK);

        world.getBlockAt(centerX, y, baseZ + section.endZ - 1).setType(Material.MAGENTA_STAINED_GLASS);
    }

    private void buildIcePath(World world, int centerX, int y, int baseZ, ParkourSection section) {
        for (int z = section.startZ; z < section.endZ; z++) {
            world.getBlockAt(centerX, y, baseZ + z).setType(Material.BLUE_ICE);
            world.getBlockAt(centerX, y + 3, baseZ + z).setType(Material.MAGENTA_STAINED_GLASS);
        }
    }

    private void buildFenceJumps(World world, int centerX, int y, int baseZ, ParkourSection section) {
        world.getBlockAt(centerX, y + 1, baseZ + section.startZ).setType(Material.MAGENTA_STAINED_GLASS);
        for (int z = section.startZ; z < section.endZ; z++) {
            if ((z - section.startZ) % 3 == 0 && z > section.startZ) {
                world.getBlockAt(centerX, y + 1, baseZ + z).setType(Material.OAK_FENCE);
            }
        }
    }

    private void buildLadderClimb(World world, int centerX, int y, int baseZ, ParkourSection section) {
        int wallZ = section.startZ + 1;

        // Build ladder wall
        for (int yOffset = 0; yOffset <= 6; yOffset++) {
            world.getBlockAt(centerX, y + yOffset, baseZ + wallZ).setType(Material.MAGENTA_STAINED_GLASS);

            if (yOffset > 0) {
                Block ladderBlock = world.getBlockAt(centerX + 1, y + yOffset, baseZ + wallZ);
                ladderBlock.setType(Material.LADDER);
                org.bukkit.block.data.Directional ladderData = (org.bukkit.block.data.Directional) ladderBlock.getBlockData();
                ladderData.setFacing(BlockFace.EAST);
                ladderBlock.setBlockData(ladderData);
            }
        }

        // Add jump down at the end (3-4 blocks forward, back to ground level)
        world.getBlockAt(centerX, y, baseZ + section.endZ - 1).setType(Material.MAGENTA_STAINED_GLASS);
    }

    private void buildClimbingWall(World world, int centerX, int y, int baseZ, ParkourSection section) {
        int wallZ = section.startZ + 2;

        for (int yOffset = 0; yOffset <= 5; yOffset++) {
            world.getBlockAt(centerX, y + yOffset, baseZ + wallZ).setType(Material.MAGENTA_STAINED_GLASS);
        }

        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            int wallY = y + 1 + random.nextInt(4);
            Block buttonBlock = world.getBlockAt(centerX, wallY, baseZ + wallZ);
            buttonBlock.setType(Material.STONE_BUTTON);
        }

        world.getBlockAt(centerX, y + 5, baseZ + section.endZ - 1).setType(Material.MAGENTA_STAINED_GLASS);
    }


    private void buildLavaParkour(World world, int centerX, int y, int baseZ, ParkourSection section) {
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = section.startZ; z < section.endZ; z++) {
                if (x == centerX - 2 || x == centerX + 2) {
                    world.getBlockAt(x, y - 1, baseZ + z).setType(Material.OBSIDIAN);
                } else {
                    world.getBlockAt(x, y - 1, baseZ + z).setType(Material.LAVA);
                }
            }
        }

        Random random = new Random();
        for (int z = section.startZ; z < section.endZ; z += 3) {
            int xOffset = random.nextInt(3) - 1;
            world.getBlockAt(centerX + xOffset, y, baseZ + z).setType(Material.NETHERRACK);
        }
    }

    private void buildWaterSwim(World world, int centerX, int y, int baseZ, ParkourSection section) {
        for (int z = section.startZ; z < section.endZ; z++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (x == centerX - 1 || x == centerX + 1) {
                    world.getBlockAt(x, y - 1, baseZ + z).setType(Material.PRISMARINE);
                    world.getBlockAt(x, y, baseZ + z).setType(Material.PRISMARINE_WALL);
                } else {
                    world.getBlockAt(x, y - 1, baseZ + z).setType(Material.PRISMARINE);
                    world.getBlockAt(x, y, baseZ + z).setType(Material.WATER);
                    world.getBlockAt(x, y + 1, baseZ + z).setType(Material.WATER);
                }
            }
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
    }

    private void setupCheckpoints(World world) {
        int startX = courseStartLocation.getBlockX();
        int startY = courseStartLocation.getBlockY();
        int startZ = courseStartLocation.getBlockZ();

        player1Checkpoints.put(0, new Location(world, startX - (courseWidth / 2), startY + 1, startZ + 1));
        player2Checkpoints.put(0, new Location(world, startX + (courseWidth / 2), startY + 1, startZ + 1));

        for (int i = 1; i < totalCheckpoints - 1; i++) {
            int checkpointZ = checkpointPositions.get(i);

            world.getBlockAt(startX - (courseWidth / 2), startY, startZ + checkpointZ).setType(Material.EMERALD_BLOCK);
            world.getBlockAt(startX + (courseWidth / 2), startY, startZ + checkpointZ).setType(Material.EMERALD_BLOCK);

            player1Checkpoints.put(i, new Location(world, startX - (courseWidth / 2), startY + 1, startZ + checkpointZ));
            player2Checkpoints.put(i, new Location(world, startX + (courseWidth / 2), startY + 1, startZ + checkpointZ));
        }

        player1Checkpoints.put(totalCheckpoints - 1, new Location(world, startX - (courseWidth / 2), startY + 1, startZ + courseLength + 1));
        player2Checkpoints.put(totalCheckpoints - 1, new Location(world, startX + (courseWidth / 2), startY + 1, startZ + courseLength + 1));
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

        player.setFallDistance(0);

        handleCheckpoints(player);
        handleFalling(player);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameActive) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

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
            for (int i = 1; i < totalCheckpoints - 1; i++) {
                Location checkpointLoc = checkpoints.get(i);

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
        return block.getX() == checkpointLoc.getBlockX() &&
                block.getY() == checkpointLoc.getBlockY() &&
                block.getZ() == checkpointLoc.getBlockZ();
    }

    private void handleFalling(Player player) {
        if (player.getLocation().getY() < courseStartLocation.getY() - 10) {
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

        world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.add(0, 1, 0), 50, 1, 1, 1, 0.3);
        world.spawnParticle(Particle.FLAME, location, 30, 0.5, 0.5, 0.5, 0.05);
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
                    checkRaceEnd();
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
            String winnerName = winner.getName();
            MessageUtil.sendMessage(player1, "&6Parkour Race has ended! &aWinner: &e" + winnerName);
            MessageUtil.sendMessage(player2, "&6Parkour Race has ended! &aWinner: &e" + winnerName);

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

            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            Player loser = winner.equals(player1) ? player2 : player1;
            if (loser != null) {
                loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }

        teleportToSafeLocation();
        restorePlayerInventories();

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