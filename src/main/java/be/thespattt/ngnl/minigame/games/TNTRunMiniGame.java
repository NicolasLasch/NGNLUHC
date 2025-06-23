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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TNTRunMiniGame extends MiniGameBase implements Listener {
    private boolean gameActive = false;
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Set<Location> removedBlocks = new HashSet<>();
    private Location arenaCenter;
    private int arenaSize = 15;
    private int arenaLayers = 3;
    private BukkitTask blockRemovalTask; // Task for continuous block removal

    public TNTRunMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.TNT_RUN, player1WonPvP);
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        storePlayerInventories();

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            player1.setGameMode(GameMode.ADVENTURE);
        }
        if (player2 != null) {
            player2.setGameMode(GameMode.ADVENTURE);
        }

        setupTNTRunArena();
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
    }

    private void setupTNTRunArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100;

        arenaCenter = new Location(world, x, y, z);

        preloadChunksAndThen(world, arenaCenter, 64, () -> {
            buildArena(world, arenaCenter);
            teleportPlayers();
            startCountdown();
        });
    }

    public void preloadChunksAndThen(World world, Location center, int radius, Runnable onLoaded) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        Set<Chunk> chunksToLoad = loadChunks(center, world, chunkRadius);

        new BukkitRunnable() {
            @Override
            public void run() {
                if(chunksToLoad.stream().anyMatch(chunk -> !chunk.isLoaded())) return;
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

    private void buildArena(World world, Location center) {
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int layer = 0; layer < arenaLayers; layer++) {
            int layerY = centerY - (layer * 3);
            buildLayer(world, centerX, layerY, centerZ, layer);
        }

        buildWaterLayer(world, centerX, centerY - (arenaLayers * 3) + 1, centerZ);
    }

    private void buildLayer(World world, int centerX, int layerY, int centerZ, int layer) {
        buildFloor(world, centerX, layerY, centerZ, layer);
        buildWalls(world, centerX, layerY, centerZ);
    }

    private void buildFloor(World world, int centerX, int layerY, int centerZ, int layer) {
        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int z = -arenaSize; z <= arenaSize; z++) {
                if (Math.abs(x) <= arenaSize && Math.abs(z) <= arenaSize) {
                    Block block = world.getBlockAt(centerX + x, layerY, centerZ + z);
                    block.setType(Material.PURPLE_WOOL);
                }
            }
        }
    }

    private void buildWalls(World world, int centerX, int layerY, int centerZ) {
        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int y = 0; y <= 3; y++) {
                world.getBlockAt(centerX + x, layerY + y, centerZ - arenaSize).setType(Material.GLASS);
                world.getBlockAt(centerX + x, layerY + y, centerZ + arenaSize).setType(Material.GLASS);
            }
        }

        for (int z = -arenaSize; z <= arenaSize; z++) {
            for (int y = 0; y <= 3; y++) {
                world.getBlockAt(centerX - arenaSize, layerY + y, centerZ + z).setType(Material.GLASS);
                world.getBlockAt(centerX + arenaSize, layerY + y, centerZ + z).setType(Material.GLASS);
            }
        }
    }

    private void buildWaterLayer(World world, int centerX, int y, int centerZ) {
        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int z = -arenaSize; z <= arenaSize; z++) {
                world.getBlockAt(centerX + x, y, centerZ + z).setType(Material.WATER);
            }
        }
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) player1.teleport(arenaCenter.clone().add(-arenaSize / 2, 1, 0));
        if (player2 != null) player2.teleport(arenaCenter.clone().add(arenaSize / 2, 1, 0));
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                Player player1 = getPlayer1();
                Player player2 = getPlayer2();

                if (countdown > 0) {
                    MessageUtil.sendMessage(player1, "&eGame starting in " + countdown + "...");
                    MessageUtil.sendMessage(player2, "&eGame starting in " + countdown + "...");
                    countdown--;
                } else {
                    MessageUtil.sendMessage(player1, "&aGO! Keep running and don't fall down!");
                    MessageUtil.sendMessage(player2, "&aGO! Keep running and don't fall down!");
                    gameActive = true;
                    startBlockRemovalTask(); // Start the block removal task when the game begins
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // New method to start continuous block removal task
    private void startBlockRemovalTask() {
        blockRemovalTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    this.cancel();
                    return;
                }

                Player player1 = getPlayer1();
                Player player2 = getPlayer2();
                if (player1 != null && player1.isOnline()) {
                    if (checkFallIntoWater(player1)) return;
                    scheduleBlockRemoval(player1);
                }

                if (player2 != null && player2.isOnline()) {
                    if (checkFallIntoWater(player2)) return;
                    scheduleBlockRemoval(player2);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameActive) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

        // We only need to check for falling into water here
        // The block removal is now handled by the repeating task
        checkFallIntoWater(player);
    }

    private boolean hasChangedBlock(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ();
    }

    private boolean checkFallIntoWater(Player player) {
        if (player.getLocation().getBlock().getType() == Material.WATER) {
            handlePlayerLost(player);
            return true;
        }
        return false;
    }

    private void scheduleBlockRemoval(Player player) {
        Location blockLoc = player.getLocation().clone().subtract(0, 1, 0);
        Block blockBelow = blockLoc.getBlock();

        if (removedBlocks.contains(blockLoc) || blockBelow.getType() != Material.PURPLE_WOOL) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (blockBelow.getType() == Material.PURPLE_WOOL) {
                    blockBelow.setType(Material.AIR);
                    removedBlocks.add(blockLoc);
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
    }

    private void handlePlayerLost(Player player) {
        if (gameActive) {
            UUID winnerUUID = player.getUniqueId().equals(player1UUID) ? player2UUID : player1UUID;
            endGame(winnerUUID);
        }
    }
    @Override
    public void endGame(UUID winnerUUID) {
        gameActive = false;

        // Cancel the block removal task
        if (blockRemovalTask != null) {
            blockRemovalTask.cancel();
            blockRemovalTask = null;
        }

        super.endGame(winnerUUID);

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();
        Player winner = Bukkit.getPlayer(winnerUUID);

        if (player1 != null && player2 != null && winner != null) {
            MessageUtil.sendMessage(player1, "&6TNTRun Mini-Game has ended! Winner: " + winner.getName());
            MessageUtil.sendMessage(player2, "&6TNTRun Mini-Game has ended! Winner: " + winner.getName());
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