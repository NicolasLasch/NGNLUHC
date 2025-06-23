package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpleefMiniGame extends MiniGameBase implements Listener {
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean roundActive = false;
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    private final Map<UUID, GameMode> playerGameModes = new HashMap<>();

    // Key for the custom spleef shovel
    private static final NamespacedKey SPLEEF_KEY = new NamespacedKey("ngnl", "spleef_tool");

    public SpleefMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.SPLEEF, player1WonPvP);
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        storePlayerInventories();
        setPlayersGameMode(GameMode.SURVIVAL);
        givePlayersTools();
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

    private void givePlayersTools() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        // Create a basic shovel with efficiency
        ItemStack shovel = createSpleefShovel();

        if (player1 != null) player1.getInventory().addItem(shovel.clone());
        if (player2 != null) player2.getInventory().addItem(shovel.clone());
    }

    private ItemStack createSpleefShovel() {
        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        ItemMeta shovelMeta = shovel.getItemMeta();

        if (shovelMeta != null) {
            // Set display name and lore
            shovelMeta.displayName(Component.text("Spleef Shovel").color(NamedTextColor.AQUA));
            shovelMeta.setUnbreakable(true);

            // Add lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Use this to break snow blocks").color(NamedTextColor.GRAY));
            shovelMeta.lore(lore);

            // Hide flags
            shovelMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);

            // Store custom tag for event handling
            PersistentDataContainer container = shovelMeta.getPersistentDataContainer();
            container.set(SPLEEF_KEY, PersistentDataType.INTEGER, 1);

            shovel.setItemMeta(shovelMeta);
            shovel.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        }

        return shovel;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Only handle participants
        if (!isParticipant(player)) return;

        Material blockType = event.getBlock().getType();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Only allow breaking snow blocks with the spleef shovel
        if (blockType != Material.SNOW_BLOCK && blockType != Material.SNOW) {
            event.setCancelled(true);
            return;
        }

        // Verify they're using the spleef shovel
        if (item.getType() != Material.DIAMOND_SHOVEL) {
            event.setCancelled(true);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(SPLEEF_KEY, PersistentDataType.INTEGER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Prevent player vs player damage during the minigame
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            if (isParticipant(damager) || isParticipant(victim)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!roundActive) return;

        // Check if it's a snowball
        if (!(event.getEntity() instanceof Snowball)) return;

        Snowball snowball = (Snowball) event.getEntity();

        // Check if it was thrown by a participant
        if (!(snowball.getShooter() instanceof Player)) return;

        Player shooter = (Player) snowball.getShooter();

        if (!isParticipant(shooter)) return;

        // Check if it hit another player
        if (event.getHitEntity() instanceof Player) {
            Player hitPlayer = (Player) event.getHitEntity();

            if (isParticipant(hitPlayer) && hitPlayer != shooter) {
                // Apply knockback to the hit player
                Vector knockback = snowball.getVelocity().normalize().multiply(1.5);
                hitPlayer.setVelocity(hitPlayer.getVelocity().add(knockback));

                hitPlayer.playSound(hitPlayer.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

                hitPlayer.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, hitPlayer.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
            }
        }
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

    private void setupSpleefArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);

        Location center = new Location(world, x, 72, z);
        preloadChunksAndThen(world, center, 64, () -> {
            buildSleefArena(world, center);
            teleportPlayersToArena(center);
        });
    }

    private void buildSleefArena(World world, Location center) {
        buildFloor(world, center);
        buildWalls(world, center);
    }

    private void buildFloor(World world, Location center) {
        for (int offsetX = -6; offsetX <= 6; offsetX++) {
            for (int offsetZ = -6; offsetZ <= 6; offsetZ++) {
                world.getBlockAt(center.clone().add(offsetX, 0, offsetZ)).setType(Material.SNOW_BLOCK);
                world.getBlockAt(center.clone().add(offsetX, -1, offsetZ)).setType(Material.WATER);
                world.getBlockAt(center.clone().add(offsetX, -2, offsetZ)).setType(Material.STONE);
            }
        }
    }

    private void buildWalls(World world, Location center) {
        for (int offsetX = -6; offsetX <= 6; offsetX++) {
            for (int height = -2; height <= 5; height++) {
                world.getBlockAt(center.clone().add(offsetX, height, -6)).setType(Material.GLASS);
                world.getBlockAt(center.clone().add(offsetX, height, 6)).setType(Material.GLASS);
            }
        }

        for (int offsetZ = -6; offsetZ <= 6; offsetZ++) {
            for (int height = -2; height <= 5; height++) {
                world.getBlockAt(center.clone().add(-6, height, offsetZ)).setType(Material.GLASS);
                world.getBlockAt(center.clone().add(6, height, offsetZ)).setType(Material.GLASS);
            }
        }
    }

    private void teleportPlayersToArena(Location center) {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) player1.teleport(center.clone().add(-4, 2, 0));
        if (player2 != null) player2.teleport(center.clone().add(4, 2, 0));

        roundActive = true;
        MessageUtil.sendMessage(player1, "&6Spleef Mini-Game: Best of Three! Make your opponent fall!");
        MessageUtil.sendMessage(player2, "&6Spleef Mini-Game: Best of Three! Make your opponent fall!");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!roundActive) return;

        Player player = event.getPlayer();

        if (isParticipant(player)) {
            Material blockType = player.getLocation().getBlock().getType();

            if (blockType == Material.WATER ||
                    player.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType() == Material.WATER) {

                Bukkit.getScheduler().runTask(plugin, () -> {
                    handlePlayerFell(player);
                });
            }
        }
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
    }

    private void handlePlayerFell(Player player) {
        if (player.getUniqueId().equals(player1UUID)) {
            player2Score++;
            MessageUtil.sendMessage(player, "&cYou fell! Your opponent won the round.");
            MessageUtil.sendMessage(getPlayer2(), "&aYour opponent fell! You won the round.");
        } else if (player.getUniqueId().equals(player2UUID)) {
            player1Score++;
            MessageUtil.sendMessage(player, "&cYou fell! Your opponent won the round.");
            MessageUtil.sendMessage(getPlayer1(), "&aYour opponent fell! You won the round.");
        }
        checkGameEnd();
    }

    private void checkGameEnd() {
        if (player1Score >= 2) endGame(player1UUID);
        else if (player2Score >= 2) endGame(player2UUID);
        else startNewRound();
    }

    private void startNewRound() {
        roundActive = false;
        setupSpleefArena();

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        MessageUtil.sendMessage(player1, "&eNew round! " + player1Score + " - " + player2Score);
        MessageUtil.sendMessage(player2, "&eNew round! " + player1Score + " - " + player2Score);
    }

    @Override
    public void endGame(UUID winnerUUID) {
        roundActive = false;
        super.endGame(winnerUUID);

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player2 != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                MessageUtil.sendMessage(player1, "&6Spleef Mini-Game has ended! Winner: " + winner.getName());
                MessageUtil.sendMessage(player2, "&6Spleef Mini-Game has ended! Winner: " + winner.getName());
            }
        }

        restorePlayerInventories();
        PlayerMoveEvent.getHandlerList().unregister(this);
        BlockBreakEvent.getHandlerList().unregister(this);
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        ProjectileHitEvent.getHandlerList().unregister(this);
        // HandlerList.unregisterAll(this);
    }
}