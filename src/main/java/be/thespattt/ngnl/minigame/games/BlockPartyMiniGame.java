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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BlockPartyMiniGame extends MiniGameBase implements Listener {
    private boolean gameActive = false;
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private Location arenaCenter;
    private final int arenaSize = 13;
    private final int floorHeight = 70;
    private int roundNumber = 1;
    private Material targetMaterial;
    private final List<Material> colorMaterials = Arrays.asList(
            Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.YELLOW_WOOL,
            Material.PINK_WOOL, Material.LIGHT_BLUE_WOOL, Material.LIME_WOOL, Material.ORANGE_WOOL,
            Material.MAGENTA_WOOL, Material.PURPLE_WOOL, Material.CYAN_WOOL, Material.WHITE_WOOL
    );

    public BlockPartyMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.BLOC_PARTY, player1WonPvP);
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        storePlayerInventories();
        setupBlockPartyArena();
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

        if (player1 != null && playerInventories.containsKey(player1UUID))
            restoreInventory(player1);

        if (player2 != null && playerInventories.containsKey(player2UUID))
            restoreInventory(player2);
    }

    private void restoreInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(playerInventories.get(player.getUniqueId()));
        player.getInventory().setArmorContents(playerArmorContents.get(player.getUniqueId()));
        playerInventories.remove(player.getUniqueId());
        playerArmorContents.remove(player.getUniqueId());
    }

    private void setupBlockPartyArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);

        arenaCenter = new Location(world, x, floorHeight, z);

        preloadChunksAndThen(world, arenaCenter, 64, () -> {
            buildArena(world, arenaCenter);
            teleportPlayers();
            showInstructions();
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

        // Build floor with random colored blocks
        buildRandomFloor(world, centerX, centerY, centerZ);

        // Build walls
        buildWalls(world, centerX, centerY, centerZ);

        // Build void/kill layer below
        buildVoidLayer(world, centerX, centerY - 5, centerZ);
    }

    private void buildRandomFloor(World world, int centerX, int centerY, int centerZ) {
        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int z = -arenaSize; z <= arenaSize; z++) {
                if (Math.abs(x) <= arenaSize && Math.abs(z) <= arenaSize) {
                    Material material = colorMaterials.get(ThreadLocalRandom.current().nextInt(colorMaterials.size()));
                    world.getBlockAt(centerX + x, centerY, centerZ + z).setType(material);
                }
            }
        }
    }

    private void buildWalls(World world, int centerX, int centerY, int centerZ) {
        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int y = 1; y <= 4; y++) {
                world.getBlockAt(centerX + x, centerY + y, centerZ - arenaSize).setType(Material.GLASS);
                world.getBlockAt(centerX + x, centerY + y, centerZ + arenaSize).setType(Material.GLASS);
            }
        }

        for (int z = -arenaSize; z <= arenaSize; z++) {
            for (int y = 1; y <= 4; y++) {
                world.getBlockAt(centerX - arenaSize, centerY + y, centerZ + z).setType(Material.GLASS);
                world.getBlockAt(centerX + arenaSize, centerY + y, centerZ + z).setType(Material.GLASS);
            }
        }
    }

    private void buildVoidLayer(World world, int centerX, int centerY, int centerZ) {
        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int z = -arenaSize; z <= arenaSize; z++) {
                world.getBlockAt(centerX + x, centerY, centerZ + z).setType(Material.LAVA);
            }
        }
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) player1.teleport(arenaCenter.clone().add(0, 1, 0));
        if (player2 != null) player2.teleport(arenaCenter.clone().add(0, 1, 0));
    }

    private void showInstructions() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        String instructions = "&6⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯";
        MessageUtil.sendMessage(player1, instructions);
        MessageUtil.sendMessage(player2, instructions);

        MessageUtil.sendMessage(player1, "&6Block Party &e- Minigame Instructions");
        MessageUtil.sendMessage(player2, "&6Block Party &e- Minigame Instructions");

        MessageUtil.sendMessage(player1, "&7• A color will be announced");
        MessageUtil.sendMessage(player2, "&7• A color will be announced");

        MessageUtil.sendMessage(player1, "&7• Find that colored block and stand on it before time runs out");
        MessageUtil.sendMessage(player2, "&7• Find that colored block and stand on it before time runs out");

        MessageUtil.sendMessage(player1, "&7• All other blocks will disappear");
        MessageUtil.sendMessage(player2, "&7• All other blocks will disappear");

        MessageUtil.sendMessage(player1, "&7• Last player standing wins!");
        MessageUtil.sendMessage(player2, "&7• Last player standing wins!");

        MessageUtil.sendMessage(player1, instructions);
        MessageUtil.sendMessage(player2, instructions);
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    Player player1 = getPlayer1();
                    Player player2 = getPlayer2();

                    MessageUtil.sendMessage(player1, "&eGame starting in " + countdown + "...");
                    MessageUtil.sendMessage(player2, "&eGame starting in " + countdown + "...");

                    player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

                    countdown--;
                } else {
                    cancel();
                    beginBlockParty();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void beginBlockParty() {
        gameActive = true;
        startRound();
    }

    private void startRound() {
        // Select a random color for this round
        targetMaterial = colorMaterials.get(ThreadLocalRandom.current().nextInt(colorMaterials.size()));

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        String colorName = formatMaterialName(targetMaterial);

        // Give players a sample block in their inventory
        giveColorReferenceBlock(player1);
        giveColorReferenceBlock(player2);

        MessageUtil.sendMessage(player1, "&eRound " + roundNumber + ": Stand on " + colorName + " &eblocks!");
        MessageUtil.sendMessage(player2, "&eRound " + roundNumber + ": Stand on " + colorName + " &eblocks!");

        player1.playSound(player1.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player2.playSound(player2.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // Give players time to find the correct block
        int searchTime = Math.max(7 - roundNumber/2, 3); // Decreases with each round, minimum 3 seconds

        new BukkitRunnable() {
            int timeLeft = searchTime;

            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }

                if (timeLeft > 0) {
                    MessageUtil.sendMessage(player1, "&e" + timeLeft + " seconds left to find " + colorName + " &eblocks!");
                    MessageUtil.sendMessage(player2, "&e" + timeLeft + " seconds left to find " + colorName + " &eblocks!");

                    player1.playSound(player1.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player2.playSound(player2.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

                    timeLeft--;
                } else {
                    cancel();
                    removeBadBlocks();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void giveColorReferenceBlock(Player player) {
        if (player == null) return;

        // Clear the first slot
        player.getInventory().setItem(0, null);

        // Create a wool block of the target color
        ItemStack referenceBlock = new ItemStack(targetMaterial);

        // Get color name without color codes for item name
        String plainColorName = targetMaterial.name().replace("_WOOL", "");
        plainColorName = plainColorName.charAt(0) + plainColorName.substring(1).toLowerCase();

        // Set the item name
        org.bukkit.inventory.meta.ItemMeta meta = referenceBlock.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "Find " + plainColorName + " blocks!");
            referenceBlock.setItemMeta(meta);
        }

        // Give to player in first slot
        player.getInventory().setItem(0, referenceBlock);
    }

    private String formatMaterialName(Material material) {
        String name = material.name();
        name = name.replace("_WOOL", "");
        String plainName = name.charAt(0) + name.substring(1).toLowerCase();

        // Add color codes based on the material
        String colorCode = "&f"; // Default white

        switch (material) {
            case RED_WOOL:
                colorCode = "&c";
                break;
            case BLUE_WOOL:
                colorCode = "&9";
                break;
            case GREEN_WOOL:
                colorCode = "&2";
                break;
            case YELLOW_WOOL:
                colorCode = "&e";
                break;
            case PINK_WOOL:
                colorCode = "&d";
                break;
            case LIGHT_BLUE_WOOL:
                colorCode = "&b";
                break;
            case LIME_WOOL:
                colorCode = "&a";
                break;
            case ORANGE_WOOL:
                colorCode = "&6";
                break;
            case MAGENTA_WOOL:
                colorCode = "&5";
                break;
            case PURPLE_WOOL:
                colorCode = "&5";
                break;
            case CYAN_WOOL:
                colorCode = "&3";
                break;
            case WHITE_WOOL:
                colorCode = "&f";
                break;
        }

        return colorCode + plainName;
    }

    private void removeBadBlocks() {
        World world = arenaCenter.getWorld();
        int centerX = arenaCenter.getBlockX();
        int centerY = arenaCenter.getBlockY();
        int centerZ = arenaCenter.getBlockZ();

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int z = -arenaSize; z <= arenaSize; z++) {
                Block block = world.getBlockAt(centerX + x, centerY, centerZ + z);
                if (block.getType() != targetMaterial) {
                    block.setType(Material.AIR);
                }
            }
        }

        player1.playSound(player1.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        player2.playSound(player2.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);

        // Check for players in void
        checkPlayersAndContinue();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameActive) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

        // Check if player fell into void
        if (player.getLocation().getY() < arenaCenter.getY() - 2) {
            handlePlayerLost(player);
        }
    }

    private void checkPlayersAndContinue() {
        // Add a delay before checking to give time for players to fall
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player1 = getPlayer1();
                Player player2 = getPlayer2();

                // Check if players are on valid blocks
                boolean player1Safe = isPlayerSafe(player1);
                boolean player2Safe = isPlayerSafe(player2);

                if (!player1Safe && !player2Safe) {
                    // Both fell, it's a tie - let's make a random winner
                    boolean player1Wins = ThreadLocalRandom.current().nextBoolean();
                    UUID winnerUUID = player1Wins ? player1UUID : player2UUID;

                    MessageUtil.sendMessage(player1, "&eBoth players fell! Random winner selected.");
                    MessageUtil.sendMessage(player2, "&eBoth players fell! Random winner selected.");

                    endGame(winnerUUID);
                } else if (!player1Safe) {
                    handlePlayerLost(player1);
                } else if (!player2Safe) {
                    handlePlayerLost(player2);
                } else {
                    // Both players survived, start next round
                    roundNumber++;

                    // Rebuild floor with new random colors for next round
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            buildRandomFloor(arenaCenter.getWorld(), arenaCenter.getBlockX(), arenaCenter.getBlockY(), arenaCenter.getBlockZ());
                            startRound();
                        }
                    }.runTaskLater(plugin, 40L); // 2-second delay before next round
                }
            }
        }.runTaskLater(plugin, 20L); // 1-second delay
    }

    private boolean isPlayerSafe(Player player) {
        if (player == null) return false;

        // Check if player is still in game
        if (player.getLocation().getY() < arenaCenter.getY() - 2) return false;

        // Check if player is standing on correct block
        Location blockLoc = player.getLocation().clone().subtract(0, 1, 0);
        Block block = blockLoc.getBlock();

        return block.getType() == targetMaterial;
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
    }

    private void handlePlayerLost(Player player) {
        if (!gameActive) return;

        UUID winnerUUID;

        if (player.getUniqueId().equals(player1UUID)) {
            winnerUUID = player2UUID;
        } else {
            winnerUUID = player1UUID;
        }

        endGame(winnerUUID);
    }

    @Override
    public void endGame(UUID winnerUUID) {
        gameActive = false;
        super.endGame(winnerUUID);

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();
        Player winner = Bukkit.getPlayer(winnerUUID);

        if (player1 != null && player2 != null && winner != null) {
            MessageUtil.sendMessage(player1, "&6Block Party Mini-Game has ended! Winner: " + winner.getName());
            MessageUtil.sendMessage(player2, "&6Block Party Mini-Game has ended! Winner: " + winner.getName());

            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
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