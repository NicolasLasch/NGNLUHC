package be.thespattt.ngnl.game.world;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.*;
import org.bukkit.WorldType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Manager class for world generation and handling
 */
public class WorldManager {

    private final NoGameNoLife plugin;
    private final Random random = new Random();

    private final String waitingWorldName = "ngnl_waiting";
    private final String miningWorldName = "ngnl_mining";
    private final String arenaWorldName = "ngnl_arena";
    private final String minigameWorldName = "ngnl_minigame";

    private World waitingWorld;
    private World miningWorld;
    private World arenaWorld;
    private World minigameWorld;

    private final Map<be.thespattt.ngnl.game.world.WorldType, List<Location>> spawnLocations = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public WorldManager(NoGameNoLife plugin) {
        this.plugin = plugin;

        // Initialize spawn location lists
        for (be.thespattt.ngnl.game.world.WorldType type : be.thespattt.ngnl.game.world.WorldType.values()) {
            spawnLocations.put(type, new ArrayList<>());
        }
    }

    /**
     * Initialize game worlds
     */
    public void initializeWorlds() {
        // Load or create waiting world
        waitingWorld = getOrCreateWorld(waitingWorldName, World.Environment.NORMAL, WorldType.FLAT);
        if (waitingWorld != null) {
            setupWaitingWorld(waitingWorld);
        }

        miningWorld = getOrCreateWorld(miningWorldName, World.Environment.NORMAL, WorldType.NORMAL);
        if (miningWorld != null) {
            setupMiningWorld(miningWorld);
        }

        arenaWorld = getOrCreateWorld(arenaWorldName, World.Environment.NORMAL, WorldType.FLAT);
        if (arenaWorld != null) {
            setupArenaWorld(arenaWorld);
        }

        minigameWorld = getOrCreateWorld(minigameWorldName, World.Environment.NORMAL, WorldType.FLAT);
        if (minigameWorld != null) {
            setupMiniGameWorld(arenaWorld);
        }

        // Load spawn locations
        loadSpawnLocations();

        MessageUtil.logInfo("Worlds initialized");
    }

    /**
     * Get or create a world
     *
     * @param worldName Name of the world
     * @param environment Environment type
     * @param worldType World type
     * @return World instance or null if creation failed
     */
    private World getOrCreateWorld(String worldName, World.Environment environment, WorldType worldType) {
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            MessageUtil.logInfo("Creating world: " + worldName);

            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(environment);
            creator.type(worldType);

            world = creator.createWorld();
        }

        return world;
    }

    /**
     * Setup waiting world
     *
     * @param world World to setup
     */
    private void setupWaitingWorld(World world) {
        // Set game rules
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setTime(6000); // Midday

        // Set world spawn
        world.setSpawnLocation(0, 64, 0);

        // Add spawn location
        Location spawn = new Location(world, 0.5, 64, 0.5, 0, 0);
        spawnLocations.get(be.thespattt.ngnl.game.world.WorldType.WAITING).add(spawn);
    }

    /**
     * Create mining world for the game
     */
    public void createMiningWorld() {
        // Check if world already exists
        if (miningWorld != null) {
            MessageUtil.logInfo("Mining world already exists");
            return;
        }

        // Create new mining world
        miningWorld = getOrCreateWorld(miningWorldName, World.Environment.NORMAL, WorldType.NORMAL);

        if (miningWorld != null) {
            setupMiningWorld(miningWorld);
        }
    }

    /**
     * Setup mining world
     *
     * @param world World to setup
     */
    public void setupMiningWorld(World world) {
        // Set game rules
        world.setGameRuleValue("doDaylightCycle", "true");
        world.setGameRuleValue("doWeatherCycle", "true");
        world.setGameRuleValue("doMobSpawning", "true");

        // Set world border
        int borderSize = plugin.getConfigManager().getGameConfig().getMiningWorldBorderSize();
        world.getWorldBorder().setSize(borderSize * 2);
        world.getWorldBorder().setWarningDistance(50);
        world.getWorldBorder().setCenter(0, 0);

        // Generate spawn locations
        generateSpawnLocations(world, be.thespattt.ngnl.game.world.WorldType.MINING, 16);
    }

    /**
     * Create arena world for the game
     */
    public void createArenaWorld() {
        // Check if world already exists
        if (arenaWorld != null) {
            MessageUtil.logInfo("Arena world already exists");
            return;
        }

        // Create new arena world
        arenaWorld = getOrCreateWorld(arenaWorldName, World.Environment.NORMAL, WorldType.FLAT);

        if (arenaWorld != null) {
            setupArenaWorld(arenaWorld);
        }
    }

    public void setupArenaWorld(World world) {
        // Set game rules
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setTime(6000); // Midday

        // Set world border
        int borderSize = plugin.getConfigManager().getGameConfig().getArenaWorldBorderSize();
        world.getWorldBorder().setSize(borderSize * 2);
        world.getWorldBorder().setWarningDistance(20);
        world.getWorldBorder().setCenter(0, 0);

        // Generate spawn locations
        generateSpawnLocations(world, be.thespattt.ngnl.game.world.WorldType.ARENA, 8);
    }


    private void createMiniGameWorld() {
        String name = "ngnl_minigame";

        if (Bukkit.getWorld(name) == null) {
            WorldCreator creator = new WorldCreator(name);
            creator.environment(World.Environment.NORMAL);
            creator.type(WorldType.FLAT);
            creator.generatorSettings("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;1"); // plat
            World world = creator.createWorld();

            generateWoodenRoom(world); // salle 5x5
        }
    }

    private void generateWoodenRoom(World world) {
        Location center = new Location(world, 0, 70, 0); // coordonnée de base
        int radius = 2;

        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean isWall = x == -radius || x == radius || z == -radius || z == radius || y == 0 || y == 4;
                    Material material = isWall ? Material.OAK_PLANKS : Material.AIR;
                    world.getBlockAt(center.clone().add(x, y, z)).setType(material);
                }
            }
        }
    }


    /**
     * Setup arena world
     *
     * @param world World to setup
     */
    public void setupMiniGameWorld(World world) {
        // Set game rules
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setTime(6000); // Midday

        // Set world border
        int borderSize = plugin.getConfigManager().getGameConfig().getArenaWorldBorderSize();
        world.getWorldBorder().setSize(borderSize * 2);
        world.getWorldBorder().setWarningDistance(20);
        world.getWorldBorder().setCenter(0, 0);
    }

    /**
     * Generate spawn locations for a world
     *
     * @param world World to generate locations for
     * @param worldType World type
     * @param count Number of locations to generate
     */
    private void generateSpawnLocations(World world, be.thespattt.ngnl.game.world.WorldType worldType, int count) {
        List<Location> locations = spawnLocations.get(worldType);
        locations.clear();

        // Determine world border size
        double borderSize = world.getWorldBorder().getSize() / 2;
        double minDistance = borderSize * 0.1; // Minimum 10% from center
        double maxDistance = borderSize * 0.7; // Maximum 70% from center

        // Generate spawn locations
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);

            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;

            // Find safe Y position
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;

            Location location = new Location(world, x + 0.5, y, z + 0.5, (float) (angle * 180 / Math.PI + 90), 0);
            locations.add(location);
        }
    }

    /**
     * Load spawn locations from config (not implemented yet)
     */
    private void loadSpawnLocations() {
        // This would load saved spawn locations from a config file
        // For now, we'll just use the dynamically generated ones
    }

    /**
     * Save spawn locations to config (not implemented yet)
     */
    private void saveSpawnLocations() {
        // This would save spawn locations to a config file
        // For now, we'll just use the dynamically generated ones
    }

    /**
     * Get a random spawn location for a world type
     *
     * @param worldType World type
     * @return Random spawn location or null if none available
     */
    public Location getRandomSpawnLocation(be.thespattt.ngnl.game.world.WorldType worldType) {
        List<Location> locations = spawnLocations.get(worldType);

        if (locations.isEmpty()) {
            return getSpawnLocation(worldType);
        }

        return locations.get(random.nextInt(locations.size()));
    }

    /**
     * Get the main spawn location for a world type
     *
     * @param worldType World type
     * @return Spawn location or null if not available
     */
    public Location getSpawnLocation(be.thespattt.ngnl.game.world.WorldType worldType) {
        World world;

        switch (worldType) {
            case WAITING:
                world = waitingWorld;
                break;
            case MINING:
                world = miningWorld;
                break;
            case ARENA:
                world = arenaWorld;
                break;
            default:
                return null;
        }

        if (world == null) {
            return null;
        }

        // Use world's spawn location
        int x = world.getSpawnLocation().getBlockX();
        int z = world.getSpawnLocation().getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;

        return new Location(world, x + 0.5, y, z + 0.5, 0, 0);
    }


    /**
     * Cleanup worlds after a game
     */
    public void cleanup() {
        // Save spawn locations
        saveSpawnLocations();

        // Check if worlds should be destroyed
        if (plugin.getConfigManager().getGameConfig().isDestroyWorldsAfterGame()) {
            destroyGameWorlds();
        }
    }

    /**
     * Destroy game worlds
     */
    public void destroyGameWorlds() {
        // Unload and delete mining world
        if (miningWorld != null) {
            Bukkit.unloadWorld(miningWorld, false);
            miningWorld = null;

            // Delete world folder
            deleteWorldFolder(miningWorldName);
        }

        // Unload and delete arena world
        if (arenaWorld != null) {
            Bukkit.unloadWorld(arenaWorld, false);
            arenaWorld = null;

            // Delete world folder
            deleteWorldFolder(arenaWorldName);
        }
    }

    /**
     * Delete a world folder
     *
     * @param worldName Name of the world
     */
    private void deleteWorldFolder(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        if (worldFolder.exists()) {
            // This would delete the world folder
            // For safety, we'll just log it for now
            MessageUtil.logInfo("Would delete world folder: " + worldFolder.getPath());
        }
    }

    /**
     * Get the waiting world
     *
     * @return Waiting world
     */
    public World getWaitingWorld() {
        return waitingWorld;
    }

    /**
     * Get the mining world
     *
     * @return Mining world
     */
    public World getMiningWorld() {
        return miningWorld;
    }

    /**
     * Get the arena world
     *
     * @return Arena world
     */
    public World getArenaWorld() {
        return arenaWorld;
    }

    /**
     * Get the waiting world name
     *
     * @return Waiting world name
     */
    public String getWaitingWorldName() {
        return waitingWorldName;
    }

    /**
     * Get the mining world name
     *
     * @return Mining world name
     */
    public String getMiningWorldName() {
        return miningWorldName;
    }

    /**
     * Get the arena world name
     *
     * @return Arena world name
     */
    public String getArenaWorldName() {
        return arenaWorldName;
    }
}