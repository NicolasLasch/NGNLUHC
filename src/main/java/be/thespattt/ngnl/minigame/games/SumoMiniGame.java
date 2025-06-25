package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// Working
// TODO : Fix some arena for not overflowing water + walls
public class SumoMiniGame extends MiniGameBase implements Listener {
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean roundActive = false;
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Map<UUID, GameMode> playerGameModes = new HashMap<>();
    private Location arenaCenter;
    private final int ARENA_RADIUS = 4;

    public SumoMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.SUMO, player1WonPvP);
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        storePlayerInventories();
        setPlayersGameMode(GameMode.SURVIVAL);
        startNewRound();
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

    public void preloadChunksAndThen(World world, Location center, int radius, Runnable onLoaded) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);

        Set<Chunk> chunksToLoad = loadingChunks(center, world, chunkRadius);

        new BukkitRunnable() {
            @Override
            public void run() {
                if(chunksToLoad.stream().anyMatch(chunk -> !chunk.isLoaded())) return;
                cancel();
                onLoaded.run();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private Set<Chunk> loadingChunks(Location center, World world, int chunkRadius) {
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

    private void setupSumoArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 72; // Set a consistent height

        arenaCenter = new Location(world, x, y, z);

        preloadChunksAndThen(world, arenaCenter, ARENA_RADIUS * 2, () -> {
            buildSumoArena(world, arenaCenter);
            teleportPlayersToArena();
            startCountdown();
        });
    }

    private void buildSumoArena(World world, Location center) {
        // Clear the area
        for (int x = -ARENA_RADIUS - 2; x <= ARENA_RADIUS + 2; x++) {
            for (int z = -ARENA_RADIUS - 2; z <= ARENA_RADIUS + 2; z++) {
                for (int y = -5; y <= 5; y++) {
                    world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z).setType(Material.AIR);
                }
            }
        }

        // Build the circular platform
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double distance = Math.sqrt(x * x + z * z);

                if (distance <= ARENA_RADIUS) {
                    world.getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z).setType(Material.GRAY_CONCRETE);
                }
            }
        }

        // Build walls around water area to prevent overflow
        for (int x = -ARENA_RADIUS - 2; x <= ARENA_RADIUS + 2; x++) {
            for (int z = -ARENA_RADIUS - 2; z <= ARENA_RADIUS + 2; z++) {
                if (Math.abs(x) == ARENA_RADIUS + 2 || Math.abs(z) == ARENA_RADIUS + 2) {
                    for (int y = -5; y <= -1; y++) {
                        world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z).setType(Material.BLACK_CONCRETE);
                        world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y + 5, center.getBlockZ() + z).setType(Material.BARRIER);
                    }
                } else {
                    world.getBlockAt(center.getBlockX() + x, center.getBlockY() - 4, center.getBlockZ() + z).setType(Material.BLACK_CONCRETE);
                }
            }
        }

        // Add contained water pool
        for (int x = -ARENA_RADIUS - 1; x <= ARENA_RADIUS + 1; x++) {
            for (int z = -ARENA_RADIUS - 1; z <= ARENA_RADIUS + 1; z++) {
                world.getBlockAt(center.getBlockX() + x, center.getBlockY() - 3, center.getBlockZ() + z).setType(Material.WATER);
            }
        }

        // Add decoration
        world.getBlockAt(center.getBlockX(), center.getBlockY() + 1, center.getBlockZ()).setType(Material.YELLOW_CONCRETE);
    }

    private void teleportPlayersToArena() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            Location player1Pos = arenaCenter.clone().add(-2, 1, 0);
            player1Pos.setDirection(new Vector(1, 0, 0)); // Face toward center/opponent
            player1.teleport(player1Pos);
        }

        if (player2 != null) {
            Location player2Pos = arenaCenter.clone().add(2, 1, 0);
            player2Pos.setDirection(new Vector(-1, 0, 0)); // Face toward center/opponent
            player2.teleport(player2Pos);
        }
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 3;

            @Override
            public void run() {
                Player player1 = getPlayer1();
                Player player2 = getPlayer2();

                if (countdown > 0) {
                    // Display countdown
                    if (player1 != null) {
                        player1.sendTitle(
                                ChatColor.YELLOW + Integer.toString(countdown),
                                ChatColor.GOLD + "Get ready to fight!",
                                0, 20, 10
                        );
                    }

                    if (player2 != null) {
                        player2.sendTitle(
                                ChatColor.YELLOW + Integer.toString(countdown),
                                ChatColor.GOLD + "Get ready to fight!",
                                0, 20, 10
                        );
                    }

                    // Play sound
                    if (player1 != null) player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    if (player2 != null) player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

                    countdown--;
                } else {
                    // Start the round
                    if (player1 != null) {
                        player1.sendTitle(
                                ChatColor.GREEN + "FIGHT!",
                                ChatColor.GOLD + "Knock your opponent off!",
                                0, 20, 10
                        );
                    }

                    if (player2 != null) {
                        player2.sendTitle(
                                ChatColor.GREEN + "FIGHT!",
                                ChatColor.GOLD + "Knock your opponent off!",
                                0, 20, 10
                        );
                    }

                    // Play sound
                    if (player1 != null) player1.playSound(player1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    if (player2 != null) player2.playSound(player2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    roundActive = true;
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!roundActive) return;

        Player player = event.getPlayer();
        if (!isParticipant(player)) return;

        // Check if player is in the water or fell off
        Material blockType = player.getLocation().getBlock().getType();

        if (blockType == Material.WATER ||
                player.getLocation().getY() < arenaCenter.getY() - 1) {

            // Run task to ensure sync
            Bukkit.getScheduler().runTask(plugin, () -> {
                handlePlayerFell(player);
            });
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // No block breaking allowed during sumo
        if (isParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Only allow knockback, no actual damage
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            if (isParticipant(damager) && isParticipant(victim) && roundActive) {
                event.setDamage(0);
            } else if (isParticipant(damager) || isParticipant(victim)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        // Prevent all damage to participants except knockback from other players
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (isParticipant(player)) {
                // Allow only knockback from other players
                if (event instanceof EntityDamageByEntityEvent) {
                    EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
                    if (entityEvent.getDamager() instanceof Player && isParticipant((Player) entityEvent.getDamager())) {
                        event.setDamage(0);
                        return;
                    }
                }

                // Cancel all other damage
                event.setCancelled(true);
            }
        }
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
    }

    private void handlePlayerFell(Player player) {
        if (!roundActive) return;

        roundActive = false;

        if (player.getUniqueId().equals(player1UUID)) {
            player2Score++;
            MessageUtil.sendMessage(player, "&cYou were knocked off! Your opponent won the round.");
            MessageUtil.sendMessage(getPlayer2(), "&aYour opponent fell! You won the round.");
        } else if (player.getUniqueId().equals(player2UUID)) {
            player1Score++;
            MessageUtil.sendMessage(player, "&cYou were knocked off! Your opponent won the round.");
            MessageUtil.sendMessage(getPlayer1(), "&aYour opponent fell! You won the round.");
        }

        // Report scores
        MessageUtil.sendMessage(getPlayer1(), "&6Score: &eYou " + player1Score + " - " + player2Score + " Opponent");
        MessageUtil.sendMessage(getPlayer2(), "&6Score: &eYou " + player2Score + " - " + player1Score + " Opponent");

        checkGameEnd();
    }

    private void checkGameEnd() {
        if (player1Score >= 2) {
            endGame(player1UUID);
        } else if (player2Score >= 2) {
            endGame(player2UUID);
        } else {
            // Start a new round after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, this::startNewRound, 60L);
        }
    }

    private void startNewRound() {
        roundActive = false;
        setupSumoArena();
    }

    @Override
    public void endGame(UUID winnerUUID) {
        roundActive = false;

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player2 != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                String winnerName = winner.getName();
                MessageUtil.sendMessage(player1, "&6Sumo Mini-Game has ended! &aWinner: &e" + winnerName);
                MessageUtil.sendMessage(player2, "&6Sumo Mini-Game has ended! &aWinner: &e" + winnerName);

                // Final score announcement
                MessageUtil.sendMessage(player1, "&6Final score: &e" + player1Score + " - " + player2Score);
                MessageUtil.sendMessage(player2, "&6Final score: &e" + player2Score + " - " + player1Score);
            }
        }

        super.endGame(winnerUUID);

        restorePlayerInventories();
        PlayerMoveEvent.getHandlerList().unregister(this);
        BlockBreakEvent.getHandlerList().unregister(this);
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        EntityDamageEvent.getHandlerList().unregister(this);
        HandlerList.unregisterAll(this);
    }
}