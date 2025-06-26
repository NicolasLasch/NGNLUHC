package be.thespattt.ngnl.event.listener;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.event.custom.MiniGameEndEvent;
import be.thespattt.ngnl.event.custom.MiniGameStartEvent;
import be.thespattt.ngnl.event.custom.PhaseChangeEvent;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import be.thespattt.ngnl.arena.ArenaCombatManager;

/**
 * Event listener for game-related events
 */
public class GameListener implements Listener {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public GameListener(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPhaseChange(PhaseChangeEvent event) {
        GameState oldState = event.getOldState();
        GameState newState = event.getNewState();

        // Handle phase transitions
        if (oldState == GameState.MINING_PHASE && newState == GameState.ARENA_PHASE) {
            // Mining phase to arena phase transition
            handleMiningToArenaTransition();
        } else if (oldState == GameState.STARTING && newState == GameState.MINING_PHASE) {
            // Starting to mining phase transition
            handleStartToMiningTransition();
        } else if (newState == GameState.ENDED) {
            // Game ending
            handleGameEnd();
        }
    }

    // Todo : Verify that it's of use
    /**
     * Handle transition from mining phase to arena phase
     * Not sure if needed...
     */
    private void handleMiningToArenaTransition() {
        // Deactivate pledges
        if (plugin.getCommandManager().getPledgeCommand() != null) {
            plugin.getCommandManager().getPledgeCommand().clearAllPledges();
        }

        plugin.getRoleManager().activateArenaPhaseAbilities();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
                if (plugin.getWorldManager().getRandomSpawnLocation(be.thespattt.ngnl.game.world.WorldType.ARENA) != null) {
                    player.teleport(plugin.getWorldManager().getRandomSpawnLocation(be.thespattt.ngnl.game.world.WorldType.ARENA));
                }
            } else {
                // Teleport spectators to arena center
                if (plugin.getWorldManager().getSpawnLocation(be.thespattt.ngnl.game.world.WorldType.ARENA) != null) {
                    player.teleport(plugin.getWorldManager().getSpawnLocation(be.thespattt.ngnl.game.world.WorldType.ARENA));
                }
            }
        }

        int borderSize = plugin.getConfigManager().getGameConfig().getArenaWorldBorderSize();
        if (plugin.getWorldManager().getArenaWorld() != null) {
            plugin.getWorldManager().getArenaWorld().getWorldBorder().setSize(borderSize * 2);
        }
        plugin.getGameManager().getGame().getScoreboardManager().updateScoreboardsForAllPlayers();
    }

    /**
     * Handle transition from starting to mining phase
     */
    private void handleStartToMiningTransition() {
        // Start episode timer
        plugin.getGameManager().getGame().getEpisodeManager().startEpisodeTimer();

        // Set world border for mining world
        int borderSize = plugin.getConfigManager().getGameConfig().getMiningWorldBorderSize();
        if (plugin.getWorldManager().getMiningWorld() != null) {
            plugin.getWorldManager().getMiningWorld().getWorldBorder().setSize(borderSize * 2);
        }

        // Schedule Aka Si Anse appearance
        int akaSiAnseTime = plugin.getConfigManager().getGameConfig().getAkaSiAnseAppearTime();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getGameManager().isGameRunning() &&
                    plugin.getGameManager().getGameState() == GameState.MINING_PHASE) {
                spawnAkaSiAnse();
            }
        }, akaSiAnseTime * 60 * 20L); // Convert minutes to ticks

        // Broadcast game start
        MessageUtil.broadcastTitle("&6&lGAME START", "&eGood luck and have fun!", 10, 70, 20);
        MessageUtil.broadcast("&7&m                    ");
        MessageUtil.broadcast("&fGAME HAS STARTED!");
        MessageUtil.broadcast("&fRemember: In this world, &5games &fdecide everything!");
        MessageUtil.broadcast("&7&m                    ");
    }

    /**
     * Handle game end
     */
    private void handleGameEnd() {
        // Stop episode timer
        plugin.getGameManager().getGame().getEpisodeManager().stopEpisodeTimer();

        // Reset player states
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setMaxHealth(20.0);
            player.setHealth(20.0);
            player.getInventory().clear();

            // Teleport to lobby
            if (plugin.getWorldManager().getSpawnLocation(be.thespattt.ngnl.game.world.WorldType.WAITING) != null) {
                player.teleport(plugin.getWorldManager().getSpawnLocation(be.thespattt.ngnl.game.world.WorldType.WAITING));
            }
        }

        // Clean up game data
        plugin.getPlayerManager().clearAllPlayers();
        plugin.getRoleManager().clearRoles();

        // Destroy worlds if configured
        if (plugin.getConfigManager().getGameConfig().isDestroyWorldsAfterGame()) {
            plugin.getWorldManager().destroyGameWorlds();
        }

        // Broadcast game end
        MessageUtil.broadcastTitle("&c&lGAME OVER", "&eThank you for playing!", 10, 70, 20);
    }

    @EventHandler
    public void onMiniGameStart(MiniGameStartEvent event) {
        // Forward mini-game start event to roles
        Player player1 = Bukkit.getPlayer(event.getPlayer1Id());
        Player player2 = Bukkit.getPlayer(event.getPlayer2Id());

        if (player1 != null) {
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player1.getUniqueId());
            if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
                ngnlPlayer.getRole().onMiniGameStart(event.getPlayer2Id(), event.getMiniGameType(), true);
            }
        }

        if (player2 != null) {
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player2.getUniqueId());
            if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
                ngnlPlayer.getRole().onMiniGameStart(event.getPlayer1Id(), event.getMiniGameType(), false);
            }
        }

        // Broadcast mini-game start
        MessageUtil.broadcast("&6A mini-game has started: &e" + event.getMiniGameType().getDisplayName());
        if (player1 != null && player2 != null) {
            MessageUtil.broadcast("&6" + player1.getName() + " vs " + player2.getName());
        }
    }

    @EventHandler
    public void onMiniGameEnd(MiniGameEndEvent event) {
        // Pass
        return;
    }

    /**
     * Give a random reward book to a player
     *
     * @param player Player to receive the book
     */
    private void giveRandomRewardBook(Player player) {
        // TODO: Implement random reward book
        MessageUtil.sendMessage(player, "&aYou received a random reward book!");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // Check if player is alive
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            return;
        }

        // Check if item is null
        if (item == null) {
            return;
        }

        // Check if item has meta
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Check for special items
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Check for role items
        if (container.has(
                plugin.getNamespacedKey("role_item"),
                PersistentDataType.STRING)) {
            // Handle role item
            String roleId = container.get(
                    plugin.getNamespacedKey("role_item"),
                    PersistentDataType.STRING);

            // Forward to role handler
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
            if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
                // Check if this item is for this role
                if (ngnlPlayer.getRole().getRoleType().name().equals(roleId)) {
                    // Use item
                    event.setCancelled(true);
                    ngnlPlayer.getRole().onItemUse(item);
                } else {
                    // Wrong role
                    event.setCancelled(true);
                    MessageUtil.sendMessage(player, "&cThis item can only be used by " + roleId + "!");
                }
            }
        }

        // Check for special game items
        if (container.has(
                plugin.getNamespacedKey("special_item"),
                PersistentDataType.STRING)) {
            // Handle special item
            String itemId = container.get(
                    plugin.getNamespacedKey("special_item"),
                    PersistentDataType.STRING);

            // Forward to item handler
            event.setCancelled(true);
            handleSpecialItem(player, itemId, item);
        }

        if (plugin.getGameManager().getGameState() == GameState.ARENA_PHASE) {

            String arenaWorldName = plugin.getConfigManager().getGameConfig().getArenaWorldName();
            if (player.getWorld().getName().equals(arenaWorldName)) {
                if (item.getType() == Material.BOW ||
                        item.getType() == Material.CROSSBOW ||
                        item.getType().name().contains("SWORD")) {
                    event.setCancelled(true);
                    MessageUtil.sendMessage(player, "&cUtilisez votre Love Gun !");
                    return;
                }
            }
        }
    }

    /**
     * Handle use of special game items
     *
     * @param player Player using the item
     * @param itemId Item identifier
     * @param item ItemStack
     */
    private void handleSpecialItem(Player player, String itemId, ItemStack item) {
        // Handle different special items
        switch (itemId) {
            case "aka_si_anse":
                // Handle Aka Si Anse
                useAkaSiAnse(player, item);
                break;

            case "suniaster":
                // Handle Suniaster
                useSuniaster(player, item);
                break;

            // Add more special items as needed

            default:
                MessageUtil.sendMessage(player, "&cUnknown special item: " + itemId);
                break;
        }
    }

    /**
     * Use the Aka Si Anse item
     *
     * @param player Player using the item
     * @param item ItemStack
     */
    private void useAkaSiAnse(Player player, ItemStack item) {
        // TODO: Implement Aka Si Anse functionality
        MessageUtil.sendMessage(player, "&aYou used the Aka Si Anse!");

        // For now, just consume the item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }
    }

    /**
     * Use the Suniaster item
     *
     * @param player Player using the item
     * @param item ItemStack
     */
    private void useSuniaster(Player player, ItemStack item) {
        // TODO: Implement Suniaster functionality
        MessageUtil.sendMessage(player, "&aYou used the Suniaster!");

        // For now, just consume the item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            // Only allow ops to break blocks outside of the game
            if (!player.isOp()) {
                event.setCancelled(true);
            }
            return;
        }

        // Check if player is alive
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Check game state
        GameState gameState = plugin.getGameManager().getGameState();

        if (gameState == GameState.ARENA_PHASE) {
            if (block.getType() == Material.GRASS_BLOCK) {
                event.setCancelled(true);
                return;
            }
        }

        // Handle faction-specific mining bonuses
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
            // Check faction
            switch (ngnlPlayer.getRole().getRoleType().getFaction()) {
                case EX_MACHINA:
                    // Ex-Machina get extra drops from ores
                    if (block.getType().name().contains("ORE")) {
                        // Increase drops by 1
                        event.setDropItems(false);
                        block.getDrops(player.getInventory().getItemInMainHand(), player)
                                .forEach(drop -> {
                                    drop.setAmount(drop.getAmount() + 1);
                                    player.getWorld().dropItemNaturally(block.getLocation(), drop);
                                });
                    }
                    break;

                // Add more faction-specific bonuses as needed
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if player
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // Check if inventory has a title (custom GUI)
        if (event.getView().getTitle() == null) {
            return;
        }

        // Handle different GUIs
        if (event.getView().getTitle().contains("Mini-Game Selection")) {
            event.setCancelled(true);
            handleMiniGameSelectionClick(player, event.getCurrentItem());
        } else if (event.getView().getTitle().contains("Role Info")) {
            event.setCancelled(true);
            // No action needed, just displaying info
        }
    }

    /**
     * Handle clicks in the mini-game selection GUI
     *
     * @param player Player clicking
     * @param item Clicked item
     */
    private void handleMiniGameSelectionClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        String displayName = item.getItemMeta().getDisplayName();

        // Get clicked mini-game type
        be.thespattt.ngnl.minigame.MiniGameType selectedType = null;

        for (be.thespattt.ngnl.minigame.MiniGameType type : be.thespattt.ngnl.minigame.MiniGameType.values()) {
            if (displayName.contains(type.getDisplayName())) {
                selectedType = type;
                break;
            }
        }

        if (selectedType == null) {
            return;
        }

        // Get player's scheduled mini-game
        UUID opponentId = plugin.getGameManager().getGame().getScheduledMiniGameOpponent(player.getUniqueId());

        if (opponentId == null) {
            player.closeInventory();
            MessageUtil.sendMessage(player, "&cYou don't have a scheduled mini-game!");
            return;
        }

        // Set mini-game type
        plugin.getGameManager().getGame().setScheduledMiniGameType(player.getUniqueId(), opponentId, selectedType);

        // Close inventory
        player.closeInventory();

        // Notify player
        MessageUtil.sendMessage(player, "&aYou selected the mini-game: &e" + selectedType.getDisplayName());

        // Start mini-game
        plugin.getMiniGameManager().startScheduledMiniGame(player.getUniqueId(), opponentId);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // Check if this is a game world
        String worldName = event.getWorld().getName();

        if (worldName.equals(plugin.getWorldManager().getMiningWorldName())) {
            // Setup mining world
            plugin.getWorldManager().setupMiningWorld(event.getWorld());
        }
    }

    /**
     * Spawn the Aka Si Anse special item
     */
    private void spawnAkaSiAnse() {
        // Generate a random location
        if (plugin.getWorldManager().getMiningWorld() == null) {
            return;
        }

        // Get a random location within the world border
        int borderSize = (int) plugin.getWorldManager().getMiningWorld().getWorldBorder().getSize() / 2;
        int x = (int) (Math.random() * borderSize * 2) - borderSize;
        int z = (int) (Math.random() * borderSize * 2) - borderSize;

        // Find a safe location
        int y = plugin.getWorldManager().getMiningWorld().getHighestBlockYAt(x, z);

        // Broadcast location (approximate)
        MessageUtil.broadcast("&c&l=== SPECIAL ITEM SPAWNED ===");
        MessageUtil.broadcast("&cThe Aka Si Anse has appeared in the world!");
        MessageUtil.broadcast("&cLocation (approximate): X: " + (x - 50 + (int) (Math.random() * 100)) +
                ", Z: " + (z - 50 + (int) (Math.random() * 100)));
        MessageUtil.broadcast("&c&l=========================");

        // Spawn the guardian NPC
        // TODO: Implement guardian NPC

        // For now, just log the exact location
        MessageUtil.logInfo("Aka Si Anse spawned at: X: " + x + ", Y: " + y + ", Z: " + z);
    }

    @EventHandler
    public void onDamageByPlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player damager) {
            plugin.getCombatTracker().setLastDamager(victim.getUniqueId(), damager.getUniqueId());
        }
    }

    @EventHandler
    public void onMiniGameRoomCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim && event.getDamager() instanceof Player)) return;

        Location loc = victim.getLocation();
        World world = Bukkit.getWorld("arena_minigame");

        if (world != null && loc.getWorld().equals(world) && loc.getY() >= 70 && loc.getY() <= 75) {
            event.setCancelled(true);
        }
    }

}