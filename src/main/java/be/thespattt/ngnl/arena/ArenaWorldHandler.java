package be.thespattt.ngnl.arena;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.world.MapInstaller;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ArenaWorldHandler {

    private final NoGameNoLife plugin;
    private World arenaWorld;
    private Location centerLocation;
    /** Flat generator settings (JSON, required since 1.21): bedrock, stone, dirt, grass, plains. */
    private static final String FLAT_ARENA_SETTINGS = "{\"biome\":\"minecraft:plains\",\"features\":false,\"lakes\":false,"
            + "\"structure_overrides\":[],\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},"
            + "{\"block\":\"minecraft:stone\",\"height\":10},{\"block\":\"minecraft:dirt\",\"height\":3},"
            + "{\"block\":\"minecraft:grass_block\",\"height\":1}]}";

    private final List<Location> spawnLocations = new ArrayList<>();
    private final Random random = new Random();
    private boolean fallbackWorld = false;
    /** Number of player spawn points on the ring. */
    private static final int SPAWN_COUNT = 8;
    /** Radius (blocks) of the spawn ring around the arena center. */
    private static final int SPAWN_RING_RADIUS = 150;

    public ArenaWorldHandler(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    public void initializeArenaWorld() {
        String worldName = plugin.getConfigManager().getGameConfig().getArenaWorldName();
        arenaWorld = Bukkit.getWorld(worldName);

        if (arenaWorld == null) {
            try {
                arenaWorld = createOrLoadWorld(worldName);
            } catch (RuntimeException exception) {
                MessageUtil.logError("Could not create or load the arena world '" + worldName + "'", exception);
            }
        }

        if (arenaWorld != null) {
            setupArenaWorld();
            setupCenterAndSpawns();
            MessageUtil.logInfo("Monde arène initialisé avec succès !");
        } else {
            MessageUtil.logError("Échec du chargement du monde arène !");
        }
    }

    private World createOrLoadWorld(String worldName) {
        MessageUtil.logInfo("Chargement du monde arène: " + worldName);
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        boolean installed = !worldFolder.exists() && new MapInstaller(plugin).installIfAvailable(worldName, worldFolder);
        if (installed || (worldFolder.exists() && worldFolder.isDirectory())) {
            return loadCustomWorld(worldName);
        }
        return createFlatWorld(worldName, worldFolder);
    }

    private World loadCustomWorld(String worldName) {
        MessageUtil.logInfo("Map personnalisée trouvée, chargement...");
        WorldCreator creator = new WorldCreator(worldName);
        return creator.createWorld();
    }

    private World createFlatWorld(String worldName, File worldFolder) {
        MessageUtil.logWarning("Aucune map personnalisée trouvée à " + worldFolder.getAbsolutePath());
        MessageUtil.logWarning("Création d'une ville générée par défaut. Placez votre map dans ce dossier pour la remplacer !");
        fallbackWorld = true;

        WorldCreator creator = new WorldCreator(worldName);
        creator.type(org.bukkit.WorldType.FLAT);
        creator.generatorSettings(FLAT_ARENA_SETTINGS);
        return creator.createWorld();
    }

    private void setupArenaWorld() {
        configureGameRules();
        setEnvironmentSettings();
        setupWorldBorder();
    }

    private void configureGameRules() {
        arenaWorld.setGameRuleValue("doDaylightCycle", "false");
        arenaWorld.setGameRuleValue("doWeatherCycle", "false");
        arenaWorld.setGameRuleValue("doMobSpawning", "false");
        arenaWorld.setGameRuleValue("doFireTick", "false");
        arenaWorld.setGameRuleValue("keepInventory", "true");
        arenaWorld.setGameRuleValue("naturalRegeneration", "false");
        arenaWorld.setGameRuleValue("doTileDrops", "true");
        arenaWorld.setGameRuleValue("mobGriefing", "false");
    }

    private void setEnvironmentSettings() {
        arenaWorld.setTime(18000);
        arenaWorld.setStorm(false);
        arenaWorld.setThundering(false);
    }

    private void setupWorldBorder() {
        int initialBorderSize = plugin.getConfigManager().getGameConfig().getInitialArenaBorderSize();
        arenaWorld.getWorldBorder().setSize(initialBorderSize * 2);
        MessageUtil.logInfo("Monde arène configuré - Bordure initiale: " + initialBorderSize + "x" + initialBorderSize);
    }

    private void setupCenterAndSpawns() {
        int centerX = 0;
        int centerZ = 0;

        List<int[]> plannedSpawns = planSpawnPoints();
        if (fallbackWorld) {
            new ArenaCityBuilder().buildIfMissing(arenaWorld, SPAWN_RING_RADIUS - 10, arenaWorld.getHighestBlockYAt(0, 0), plannedSpawns);
        }

        int centerY = arenaWorld.getHighestBlockYAt(centerX, centerZ) + 1;
        centerLocation = new Location(arenaWorld, centerX + 0.5, centerY, centerZ + 0.5);
        arenaWorld.getWorldBorder().setCenter(centerX, centerZ);

        generateSpawnLocations();
        logSetupInfo(centerX, centerY, centerZ);
    }

    /**
     * Compute the X/Z of the spawn ring before anything is built on it.
     *
     * @return X/Z pairs of the spawn points
     */
    private List<int[]> planSpawnPoints() {
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < SPAWN_COUNT; i++) {
            double angle = 2 * Math.PI * i / SPAWN_COUNT;
            points.add(new int[]{(int) (SPAWN_RING_RADIUS * Math.cos(angle)), (int) (SPAWN_RING_RADIUS * Math.sin(angle))});
        }
        return points;
    }

    /**
     * Put the arena in its starting state for a new game (initial border, no mobs).
     */
    public void prepareForGame() {
        if (arenaWorld == null) {
            return;
        }
        setupWorldBorder();
        clearHostileMobs();
    }

    private void logSetupInfo(int centerX, int centerY, int centerZ) {
        MessageUtil.logInfo("Centre de l'arène défini à: X=" + centerX + ", Y=" + centerY + ", Z=" + centerZ);
        MessageUtil.logInfo("Bordure centrée sur: X=" + centerX + ", Z=" + centerZ);
    }

    private void generateSpawnLocations() {
        spawnLocations.clear();

        for (int i = 0; i < SPAWN_COUNT; i++) {
            Location spawnLoc = calculateSpawnLocation(i, SPAWN_COUNT, SPAWN_RING_RADIUS);
            if (spawnLoc != null) {
                spawnLocations.add(spawnLoc);
            }
        }

        MessageUtil.logInfo("Générés " + spawnLocations.size() + " points de spawn autour du centre");
    }

    private Location calculateSpawnLocation(int index, int total, int radius) {
        double angle = 2 * Math.PI * index / total;

        int spawnX = (int) (centerLocation.getX() + radius * Math.cos(angle));
        int spawnZ = (int) (centerLocation.getZ() + radius * Math.sin(angle));

        int safeY = findSafeSpawnHeight(spawnX, spawnZ);

        Location spawnLoc = new Location(arenaWorld, spawnX + 0.5, safeY, spawnZ + 0.5);
        spawnLoc.setYaw((float) (angle * 180 / Math.PI + 90));

        return spawnLoc;
    }

    private int findSafeSpawnHeight(int x, int z) {
        int highestY = arenaWorld.getHighestBlockYAt(x, z);

        for (int y = highestY; y >= 1; y--) {
            if (isSafeSpawnLocation(x, y, z)) {
                return y + 1;
            }
        }

        return Math.max(centerLocation.getBlockY(), highestY + 1);
    }

    private boolean isSafeSpawnLocation(int x, int y, int z) {
        Block groundBlock = arenaWorld.getBlockAt(x, y, z);
        Block airBlock1 = arenaWorld.getBlockAt(x, y + 1, z);
        Block airBlock2 = arenaWorld.getBlockAt(x, y + 2, z);

        return groundBlock.getType().isSolid() &&
                !groundBlock.getType().toString().contains("LAVA") &&
                !groundBlock.getType().toString().contains("WATER") &&
                airBlock1.getType().isAir() &&
                airBlock2.getType().isAir();
    }

    public void teleportPlayersToArena(List<Player> players) {
        if (spawnLocations.isEmpty()) {
            MessageUtil.logError("Aucun point de spawn disponible pour l'arène !");
            return;
        }

        MessageUtil.logInfo("Téléportation de " + players.size() + " joueurs vers l'arène");

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Location spawnLocation = selectSpawnLocation(i);

            performTeleport(player, spawnLocation);
        }
    }

    private Location selectSpawnLocation(int playerIndex) {
        if (playerIndex < spawnLocations.size()) {
            return spawnLocations.get(playerIndex);
        } else {
            return spawnLocations.get(random.nextInt(spawnLocations.size()));
        }
    }

    private void performTeleport(Player player, Location location) {
        player.teleport(location);
        playTeleportEffects(player);
        MessageUtil.sendMessage(player, "&5Bienvenue dans l'arène finale !");
    }

    private void playTeleportEffects(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 50, 1, 2, 1, 0.1);
    }

    public Location getRandomSpawnLocation() {
        if (spawnLocations.isEmpty()) {
            return centerLocation != null ? centerLocation : new Location(arenaWorld, 0, 80, 0);
        }

        return spawnLocations.get(random.nextInt(spawnLocations.size()));
    }

    public void clearHostileMobs() {
        if (arenaWorld == null) return;

        arenaWorld.getEntities().forEach(entity -> {
            if (isHostileMob(entity)) {
                entity.remove();
            }
        });

        MessageUtil.logInfo("Mobs hostiles supprimés du monde arène");
    }

    private boolean isHostileMob(org.bukkit.entity.Entity entity) {
        return entity instanceof org.bukkit.entity.Monster ||
                entity instanceof org.bukkit.entity.Slime ||
                entity instanceof org.bukkit.entity.Phantom ||
                entity instanceof org.bukkit.entity.Ghast;
    }

    public World getArenaWorld() {
        return arenaWorld;
    }

    public Location getCenterLocation() {
        return centerLocation;
    }

    public List<Location> getSpawnLocations() {
        return new ArrayList<>(spawnLocations);
    }
}