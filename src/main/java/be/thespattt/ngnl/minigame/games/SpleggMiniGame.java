package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpleggMiniGame extends MiniGameBase implements Listener {
    private boolean gameActive = false;
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Map<Location, Integer> blockHealth = new HashMap<>();
    private Location arenaCenter;
    private int arenaSize = 12;
    private int arenaLayers = 3;
    private int eggRefillTask = -1;

    public SpleggMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.SPLEGG, player1WonPvP);
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        storePlayerInventories();
        setupSpleggArena();
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

    private void setupSpleggArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100;

        arenaCenter = new Location(world, x, y, z);

        preloadChunksAndThen(world, arenaCenter, 64, () -> {
            buildArena(world, arenaCenter);
            teleportPlayers();
            giveEggs();
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

        // Build each layer
        for (int layer = 0; layer < arenaLayers; layer++) {
            int layerY = centerY - (layer * 3);
            buildLayer(world, centerX, layerY, centerZ);
        }

        // Build water layer at the bottom
        buildWaterLayer(world, centerX, centerY - (arenaLayers * 3) + 1, centerZ);
    }

    private void buildLayer(World world, int centerX, int layerY, int centerZ) {
        buildFloor(world, centerX, layerY, centerZ);
        buildWalls(world, centerX, layerY, centerZ);
    }

    private void buildFloor(World world, int centerX, int layerY, int centerZ) {
        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int z = -arenaSize; z <= arenaSize; z++) {
                if (Math.abs(x) <= arenaSize && Math.abs(z) <= arenaSize) {
                    Block block = world.getBlockAt(centerX + x, layerY, centerZ + z);
                    block.setType(Material.STONE);
                    blockHealth.put(block.getLocation(), 3);
                }
            }
        }
    }

    private void buildWalls(World world, int centerX, int layerY, int centerZ) {
        // Build glass walls around the arena
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

    private void giveEggs() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        ItemStack eggs = createEggStack();

        if (player1 != null) player1.getInventory().addItem(eggs.clone());
        if (player2 != null) player2.getInventory().addItem(eggs.clone());
    }

    private ItemStack createEggStack() {
        ItemStack eggs = new ItemStack(Material.EGG, 16);
        ItemMeta meta = eggs.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Splegg Shooter");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Shoot to break blocks!");
            lore.add(ChatColor.GRAY + "Break blocks under your opponent to win!");
            meta.setLore(lore);
            eggs.setItemMeta(meta);
        }
        return eggs;
    }

    // New method to handle egg refill after throwing
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!gameActive) return;

        if (!(event.getEntity() instanceof Egg)) return;

        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player shooter = (Player) event.getEntity().getShooter();

        if (!isParticipant(shooter)) return;

        // Immediately refill egg to maximum amount in the same slot
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack eggs = shooter.getInventory().getItem(0);
            if (eggs != null && eggs.getType() == Material.EGG) {
                eggs.setAmount(16); // Always keep at maximum
            } else {
                shooter.getInventory().setItem(0, createEggStack());
            }
        });
    }

    private void startEggRefill() {
        // This method is kept for backup in case something goes wrong with the immediate refill
        if (eggRefillTask != -1) {
            Bukkit.getScheduler().cancelTask(eggRefillTask);
        }

        eggRefillTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }

                Player player1 = getPlayer1();
                Player player2 = getPlayer2();

                if (player1 != null) {
                    ItemStack eggs = player1.getInventory().getItem(0);
                    if (eggs != null && eggs.getType() == Material.EGG) {
                        eggs.setAmount(16); // Always refill to maximum
                    } else {
                        player1.getInventory().setItem(0, createEggStack());
                    }
                }

                if (player2 != null) {
                    ItemStack eggs = player2.getInventory().getItem(0);
                    if (eggs != null && eggs.getType() == Material.EGG) {
                        eggs.setAmount(16); // Always refill to maximum
                    } else {
                        player2.getInventory().setItem(0, createEggStack());
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L).getTaskId(); // 5 seconds (as backup)
    }

    private void showInstructions() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        String header = "&6⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯";
        MessageUtil.sendMessage(player1, header);
        MessageUtil.sendMessage(player2, header);

        MessageUtil.sendMessage(player1, "&6Splegg &e- Minigame Instructions");
        MessageUtil.sendMessage(player2, "&6Splegg &e- Minigame Instructions");

        MessageUtil.sendMessage(player1, "&7• Shoot eggs at the blocks to damage them");
        MessageUtil.sendMessage(player2, "&7• Shoot eggs at the blocks to damage them");

        MessageUtil.sendMessage(player1, "&7• Blocks change color as they get damaged: &aGreen &7→ &6Orange &7→ &cRed &7→ disappear");
        MessageUtil.sendMessage(player2, "&7• Blocks change color as they get damaged: &aGreen &7→ &6Orange &7→ &cRed &7→ disappear");

        MessageUtil.sendMessage(player1, "&7• Try to make your opponent fall into the water below");
        MessageUtil.sendMessage(player2, "&7• Try to make your opponent fall into the water below");

        MessageUtil.sendMessage(player1, "&7• You have unlimited eggs! Shoot as many as you want!");
        MessageUtil.sendMessage(player2, "&7• You have unlimited eggs! Shoot as many as you want!");

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
                    MessageUtil.sendMessage(player1, "&eGame starting in " + countdown + "...");
                    MessageUtil.sendMessage(player2, "&eGame starting in " + countdown + "...");

                    player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

                    countdown--;
                } else {
                    MessageUtil.sendMessage(player1, "&aGO! Start shooting eggs!");
                    MessageUtil.sendMessage(player2, "&aGO! Start shooting eggs!");

                    player1.playSound(player1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    player2.playSound(player2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    gameActive = true;
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onEggThrow(PlayerEggThrowEvent event) {
        event.setHatching(false);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!gameActive) return;

        if (!(event.getEntity() instanceof Egg)) return;

        if (event.getHitBlock() == null) return;

        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player shooter = (Player) event.getEntity().getShooter();

        if (!isParticipant(shooter)) return;

        Block hitBlock = event.getHitBlock();

        handleBlockDamage(hitBlock);
    }

    private void handleBlockDamage(Block block) {
        if (!blockHealth.containsKey(block.getLocation())) return;

        int health = blockHealth.get(block.getLocation());

        health--;

        if (health <= 0) {
            block.setType(Material.AIR);
            blockHealth.remove(block.getLocation());
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
            block.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, block.getLocation().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, block.getBlockData());
        } else {
            if (health == 2) {
                block.setType(Material.LIME_CONCRETE);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_HIT, 1.0f, 1.0f);
            } else if (health == 1) {
                block.setType(Material.ORANGE_CONCRETE);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_HIT, 1.0f, 0.8f);
            }
            blockHealth.put(block.getLocation(), health);
            block.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, block.getBlockData());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameActive) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

        // Check if player fell into water
        if (player.getLocation().getBlock().getType() == Material.WATER) {
            handlePlayerLost(player);
        }
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
    }

    private void handlePlayerLost(Player player) {
        if (!gameActive) return;

        gameActive = false;

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
        if (eggRefillTask != -1) {
            Bukkit.getScheduler().cancelTask(eggRefillTask);
            eggRefillTask = -1;
        }

        super.endGame(winnerUUID);

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();
        Player winner = Bukkit.getPlayer(winnerUUID);

        if (player1 != null && player2 != null && winner != null) {
            MessageUtil.sendMessage(player1, "&6Splegg Mini-Game has ended! Winner: " + winner.getName());
            MessageUtil.sendMessage(player2, "&6Splegg Mini-Game has ended! Winner: " + winner.getName());

            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            Player loser = winner.equals(player1) ? player2 : player1;
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }

        teleportToSafeLocation();
        restorePlayerInventories();

        // Unregister events
        PlayerMoveEvent.getHandlerList().unregister(this);
        PlayerEggThrowEvent.getHandlerList().unregister(this);
        ProjectileHitEvent.getHandlerList().unregister(this);
        ProjectileLaunchEvent.getHandlerList().unregister(this); // Unregister the new event handler
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