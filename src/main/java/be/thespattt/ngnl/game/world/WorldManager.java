package be.thespattt.ngnl.game.world;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.arena.ArenaWorldHandler;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Creates, prepares and destroys the four worlds of the game:
 * waiting (lobby), mining (qualifications, fresh every game), mini-game (rooms) and arena (final).
 */
public class WorldManager {

    /** Flat-world generator settings producing an empty (void) world. */
    private static final String VOID_SETTINGS =
            "{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\",\"structures\":{\"structures\":[]}}";
    /** Height of the lobby platform. */
    private static final int LOBBY_Y = 100;
    /** Half-size of the lobby platform. */
    private static final int LOBBY_RADIUS = 15;
    /** Number of scattered spawn points generated for the mining world (at least). */
    private static final int MIN_MINING_SPAWNS = 16;

    private final NoGameNoLife plugin;
    private final Random random = new Random();

    private final String waitingWorldName = "ngnl_waiting";
    private final String miningWorldName = "ngnl_mining";
    private final String minigameWorldName = "ngnl_minigame";

    private World waitingWorld;
    private World miningWorld;
    private World minigameWorld;
    private final ArenaWorldHandler arenaWorldHandler;

    private final Map<WorldType, List<Location>> spawnLocations = new EnumMap<>(WorldType.class);

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public WorldManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.arenaWorldHandler = new ArenaWorldHandler(plugin);
        for (WorldType type : WorldType.values()) {
            spawnLocations.put(type, new ArrayList<>());
        }
    }

    // ------------------------------------------------------------------ initialization

    /**
     * Load or create the worlds needed before a game (lobby, mini-games, arena, mining).
     */
    public void initializeWorlds() {
        waitingWorld = loadOrCreate(waitingWorldName, World.Environment.NORMAL, org.bukkit.WorldType.FLAT, VOID_SETTINGS, null);
        if (waitingWorld != null) {
            setupWaitingWorld(waitingWorld);
        }

        minigameWorld = loadOrCreate(minigameWorldName, World.Environment.NORMAL, org.bukkit.WorldType.FLAT, VOID_SETTINGS, null);
        if (minigameWorld != null) {
            setupMiniGameWorld(minigameWorld);
        }

        arenaWorldHandler.initializeArenaWorld();
        ensureMiningWorld();
        MessageUtil.logInfo("Worlds initialized");
    }

    /**
     * Get a world if loaded, otherwise load it from disk or generate it.
     *
     * @param name          World name
     * @param environment   Environment
     * @param type          Generation type
     * @param generatorJson Flat generator settings (null for none)
     * @param seed          Seed to use (null for random)
     * @return The world or null if creation failed
     */
    private World loadOrCreate(String name, World.Environment environment, org.bukkit.WorldType type, String generatorJson, Long seed) {
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        MessageUtil.logInfo("Loading/creating world: " + name);
        WorldCreator creator = new WorldCreator(name).environment(environment).type(type);
        if (generatorJson != null) {
            creator.generatorSettings(generatorJson);
        }
        if (seed != null) {
            creator.seed(seed);
        }
        return creator.createWorld();
    }

    // ------------------------------------------------------------------ waiting world

    /**
     * Configure the lobby and build its platform once.
     *
     * @param world Waiting world
     */
    private void setupWaitingWorld(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setPVP(false);
        world.setTime(6000);

        buildLobbyPlatform(world);
        world.setSpawnLocation(0, LOBBY_Y + 1, 0);

        List<Location> spawns = spawnLocations.get(WorldType.WAITING);
        spawns.clear();
        spawns.add(new Location(world, 0.5, LOBBY_Y + 1, 0.5, 0, 0));
    }

    /**
     * Build the lobby platform (quartz floor, sea lantern corners) unless it already exists.
     *
     * @param world Waiting world
     */
    private void buildLobbyPlatform(World world) {
        if (world.getBlockAt(0, LOBBY_Y, 0).getType() != Material.AIR) {
            return;
        }
        for (int x = -LOBBY_RADIUS; x <= LOBBY_RADIUS; x++) {
            for (int z = -LOBBY_RADIUS; z <= LOBBY_RADIUS; z++) {
                boolean edge = Math.abs(x) == LOBBY_RADIUS || Math.abs(z) == LOBBY_RADIUS;
                boolean checker = (x + z) % 2 == 0;
                Material floor = edge ? Material.SMOOTH_QUARTZ : checker ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE;
                world.getBlockAt(x, LOBBY_Y, z).setType(floor, false);
            }
        }
        int[][] corners = {{-LOBBY_RADIUS, -LOBBY_RADIUS}, {-LOBBY_RADIUS, LOBBY_RADIUS}, {LOBBY_RADIUS, -LOBBY_RADIUS}, {LOBBY_RADIUS, LOBBY_RADIUS}};
        for (int[] corner : corners) {
            world.getBlockAt(corner[0], LOBBY_Y + 1, corner[1]).setType(Material.SEA_LANTERN, false);
            world.getBlockAt(corner[0], LOBBY_Y + 2, corner[1]).setType(Material.SEA_LANTERN, false);
        }
        world.getBlockAt(0, LOBBY_Y - 1, 0).setType(Material.BARRIER, false);
    }

    // ------------------------------------------------------------------ mining world

    /**
     * Make sure the mining world exists (created with the configured seed if needed) and is configured.
     * Call before every game and right after the previous mining world was destroyed.
     *
     * @return The mining world, or null if it could not be created
     */
    public World ensureMiningWorld() {
        if (miningWorld == null) {
            miningWorld = Bukkit.getWorld(miningWorldName);
        }
        if (miningWorld == null) {
            miningWorld = loadOrCreate(miningWorldName, World.Environment.NORMAL, org.bukkit.WorldType.NORMAL, null, readMiningSeed());
        }
        if (miningWorld != null) {
            setupMiningWorld(miningWorld);
        }
        return miningWorld;
    }

    /**
     * Read the configured mining seed.
     *
     * @return The seed, or null for a random one
     */
    private Long readMiningSeed() {
        String raw = plugin.getConfigManager().getGameConfig().getMiningSeed();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return (long) raw.trim().hashCode();
        }
    }

    /**
     * Configure the mining world (UHC rules, border) and generate safe spawn points.
     *
     * @param world Mining world
     */
    public void setupMiningWorld(World world) {
        var config = plugin.getConfigManager().getGameConfig();
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !config.isAlwaysDay());
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, config.isNaturalRegenEnabled());
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setDifficulty(Difficulty.HARD);
        world.setPVP(true);
        if (config.isAlwaysDay()) {
            world.setTime(6000);
        }

        int borderRadius = config.getMiningWorldBorderSize();
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(borderRadius * 2.0);
        world.getWorldBorder().setWarningDistance(50);

        generateMiningSpawns(world, Math.max(MIN_MINING_SPAWNS, Bukkit.getOnlinePlayers().size()));
    }

    /**
     * Generate evenly spread, safe (dry, solid ground) spawn points on a ring inside the border.
     *
     * @param world Mining world
     * @param count Number of spawn points
     */
    private void generateMiningSpawns(World world, int count) {
        List<Location> spawns = spawnLocations.get(WorldType.MINING);
        spawns.clear();

        double halfBorder = world.getWorldBorder().getSize() / 2;
        double minDistance = halfBorder * 0.1;
        double maxDistance = halfBorder * 0.7;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            spawns.add(findSafeSpawn(world, angle, minDistance, maxDistance));
        }
        Collections.shuffle(spawns, random);
    }

    /**
     * Find a spawn point around an angle, retrying with other distances if the ground is liquid.
     *
     * @param world       World to search in
     * @param angle       Angle of the ray (radians)
     * @param minDistance Minimum distance from the center
     * @param maxDistance Maximum distance from the center
     * @return A safe location (falls back to the last candidate)
     */
    private Location findSafeSpawn(World world, double angle, double minDistance, double maxDistance) {
        Location candidate = null;
        for (int attempt = 0; attempt < 15; attempt++) {
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = (int) (Math.cos(angle) * distance);
            int z = (int) (Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z);
            Material ground = world.getBlockAt(x, y, z).getType();
            candidate = new Location(world, x + 0.5, y + 1, z + 0.5, (float) (angle * 180 / Math.PI + 90), 0);
            if (ground.isSolid() && ground != Material.LAVA && ground != Material.WATER && !ground.name().contains("LEAVES")) {
                return candidate;
            }
        }
        return candidate;
    }

    /**
     * Give a list of distinct spawn points, one per player.
     *
     * @param count Number of players
     * @return Distinct mining spawn locations (repeated only if there are more players than points)
     */
    public List<Location> getMiningSpawns(int count) {
        List<Location> pool = spawnLocations.get(WorldType.MINING);
        if (pool.size() < count && miningWorld != null) {
            generateMiningSpawns(miningWorld, count);
            pool = spawnLocations.get(WorldType.MINING);
        }
        List<Location> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(pool.get(i % pool.size()));
        }
        return result;
    }

    // ------------------------------------------------------------------ mini-game world

    /**
     * Configure the mini-game world (no mobs, no weather, always day, big border).
     *
     * @param world Mini-game world
     */
    public void setupMiniGameWorld(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(6000);

        // Mini-games are spread across the world (some rooms use ordinal * 100): big border.
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(10000);
        world.getWorldBorder().setWarningDistance(50);
    }

    /**
     * Remove every non-player living entity from the mini-game world.
     */
    public void clearMiniGameWorldMobs() {
        if (minigameWorld == null) {
            return;
        }
        for (LivingEntity entity : minigameWorld.getLivingEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    // ------------------------------------------------------------------ spawn access

    /**
     * Get a random spawn location for a world type.
     *
     * @param worldType World type
     * @return Random spawn location or null if none available
     */
    public Location getRandomSpawnLocation(WorldType worldType) {
        if (worldType == WorldType.ARENA) {
            return arenaWorldHandler.getArenaWorld() != null ? arenaWorldHandler.getRandomSpawnLocation() : null;
        }
        List<Location> locations = spawnLocations.get(worldType);
        if (locations == null || locations.isEmpty()) {
            return getSpawnLocation(worldType);
        }
        return locations.get(random.nextInt(locations.size()));
    }

    /**
     * Get the main spawn location for a world type.
     *
     * @param worldType World type
     * @return Spawn location or null if not available
     */
    public Location getSpawnLocation(WorldType worldType) {
        if (worldType == WorldType.ARENA) {
            return arenaWorldHandler.getCenterLocation();
        }
        World world = switch (worldType) {
            case WAITING -> waitingWorld;
            case MINING -> miningWorld;
            case MINIGAME -> minigameWorld;
            default -> null;
        };
        if (world == null) {
            return null;
        }
        Location spawn = world.getSpawnLocation();
        int y = world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ()) + 1;
        return new Location(world, spawn.getBlockX() + 0.5, y, spawn.getBlockZ() + 0.5, 0, 0);
    }

    // ------------------------------------------------------------------ cleanup

    /**
     * Called when a game ends or the plugin is disabled: destroy the mining world if configured.
     */
    public void cleanup() {
        if (plugin.getConfigManager().getGameConfig().isDestroyWorldsAfterGame()) {
            destroyGameWorlds();
        }
    }

    /**
     * Destroy the mining world (players are sent to the lobby first) and prepare a fresh one.
     */
    public void destroyGameWorlds() {
        if (miningWorld == null) {
            return;
        }
        evacuate(miningWorld);
        String name = miningWorld.getName();
        if (Bukkit.unloadWorld(miningWorld, false)) {
            miningWorld = null;
            spawnLocations.get(WorldType.MINING).clear();
            deleteWorldFolder(name);
            scheduleFreshMiningWorld();
        } else {
            MessageUtil.logWarning("Could not unload the mining world, it will be reused next game.");
        }
    }

    /**
     * Generate the next mining world a little later (not while the server is shutting down).
     */
    private void scheduleFreshMiningWorld() {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureMiningWorld, 40L);
    }

    /**
     * Teleport every player out of a world so it can be unloaded.
     *
     * @param world World to empty
     */
    private void evacuate(World world) {
        Location lobby = getSpawnLocation(WorldType.WAITING);
        for (Player player : new ArrayList<>(world.getPlayers())) {
            if (lobby != null) {
                player.teleport(lobby);
            }
        }
    }

    /**
     * Delete a world folder recursively.
     *
     * @param worldName Name of the world
     */
    private void deleteWorldFolder(String worldName) {
        Path folder = new File(Bukkit.getWorldContainer(), worldName).toPath();
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            MessageUtil.logInfo("Deleted world folder: " + folder);
        } catch (IOException exception) {
            MessageUtil.logError("Could not delete world folder " + folder, exception);
        }
    }

    // ------------------------------------------------------------------ getters

    public World getWaitingWorld() {
        return waitingWorld;
    }

    public World getMiningWorld() {
        return miningWorld;
    }

    /**
     * Get the arena world.
     *
     * @return Arena world, or null if it is not loaded
     */
    public World getArenaWorld() {
        return arenaWorldHandler.getArenaWorld();
    }

    public World getMinigameWorld() {
        return minigameWorld;
    }

    public String getWaitingWorldName() {
        return waitingWorldName;
    }

    public String getMiningWorldName() {
        return miningWorldName;
    }

    /**
     * Initialize the arena world through its handler.
     */
    public void initializeArenaWorld() {
        arenaWorldHandler.initializeArenaWorld();
    }

    public ArenaWorldHandler getArenaWorldHandler() {
        return arenaWorldHandler;
    }
}
