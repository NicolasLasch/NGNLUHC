package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpleefMiniGame extends MiniGameBase implements Listener {
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean roundActive = false;
    private final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> playerArmorContents = new HashMap<>();
    public SpleefMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.SPLEEF, player1WonPvP);
    }
    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        storePlayerInventories();
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
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }
    private void givePlayersTools() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        ItemMeta shovelMeta = shovel.getItemMeta();
        if (shovelMeta != null) {
            shovelMeta.setDisplayName(ChatColor.AQUA + "Spleef Shovel");
            shovelMeta.setUnbreakable(true);
            shovel.setItemMeta(shovelMeta);
            shovel.addEnchantment(Enchantment.EFFICIENCY, 5);
        }

        if (player1 != null) player1.getInventory().addItem(shovel.clone());

        if (player2 != null) player2.getInventory().addItem(shovel.clone());
    }
    private void restorePlayerInventories() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && playerInventories.containsKey(player1UUID)) getPreviousInventoryContent(player1);

        if (player2 != null && playerInventories.containsKey(player2UUID)) getPreviousInventoryContent(player2);
    }
    private void getPreviousInventoryContent(Player player){
        player.getInventory().clear();
        player.getInventory().setContents(playerInventories.get(player.getUniqueId()));
        player.getInventory().setArmorContents(playerArmorContents.get(player.getUniqueId()));
        playerInventories.remove(player.getUniqueId());
        playerArmorContents.remove(player.getUniqueId());
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
    private Set<Chunk> loadingChunks(Location center, World world, int chunkRadius){
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
        preloadChunksAndThen(plugin.getWorldManager().getMinigameWorld(), center, 64, () -> {
            buildSleefArena(world, center);
            teleportPlayersToArena(center);
        });

        buildSleefArena(world, center);
        teleportPlayersToArena(center);
    }
    private void buildSleefArena(World world, Location center){
        buildFloor(world, center);
        buildWalls(world, center);
    }
    private void buildFloor(World world, Location center){
        for (int offsetX = -6; offsetX <= 6; offsetX++) {
            for (int offsetZ = -6; offsetZ <= 6; offsetZ++) {
                world.getBlockAt(center.clone().add(offsetX, 0, offsetZ)).setType(Material.SNOW_BLOCK);
                world.getBlockAt(center.clone().add(offsetX, -1, offsetZ)).setType(Material.WATER);
                world.getBlockAt(center.clone().add(offsetX, -2, offsetZ)).setType(Material.STONE);
            }
        }
    }
    private void buildWalls(World world, Location center){
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
        roundActive = true;

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        MessageUtil.sendMessage(player1, "&eNew round! " + player1Score + " - " + player2Score);
        MessageUtil.sendMessage(player2, "&eNew round! " + player1Score + " - " + player2Score);
    }
    @Override
    public void endGame(UUID winnerUUID) {
        super.endGame(winnerUUID);

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        MessageUtil.sendMessage(player1, "&6Spleef Mini-Game has ended! Winner: " + Bukkit.getPlayer(winnerUUID).getName());
        MessageUtil.sendMessage(player2, "&6Spleef Mini-Game has ended! Winner: " + Bukkit.getPlayer(winnerUUID).getName());

        restorePlayerInventories();

        PlayerMoveEvent.getHandlerList().unregister(this);
    }
}