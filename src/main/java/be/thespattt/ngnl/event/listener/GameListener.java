package be.thespattt.ngnl.event.listener;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.event.custom.PhaseChangeEvent;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.duo.StephanieRole;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Event listener for game-related events: phase changes, right-clicks on role/special items,
 * mining bonuses and the mini-game selection GUI.
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

    // ------------------------------------------------------------------ phases

    /**
     * Pledges disappear when the arena phase starts.
     *
     * @param event Phase change event
     */
    @EventHandler
    public void onPhaseChange(PhaseChangeEvent event) {
        boolean arenaStarts = event.getOldState() == GameState.MINING_PHASE && event.getNewState() == GameState.ARENA_PHASE;
        if (arenaStarts && plugin.getCommandManager().getPledgeCommand() != null) {
            plugin.getCommandManager().getPledgeCommand().clearAllPledges();
        }
    }

    // ------------------------------------------------------------------ item use

    /**
     * Route right-clicks on role items and special items to their owners, and block traditional
     * weapons in the arena.
     *
     * @param event Interact event
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isRightClickWithMainHand(event)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !plugin.getGameManager().isGameRunning()
                || !plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(plugin.getNamespacedKey("role_item"), PersistentDataType.STRING)) {
            handleRoleItem(event, player, item, container.get(plugin.getNamespacedKey("role_item"), PersistentDataType.STRING));
            return;
        }
        if (container.has(plugin.getNamespacedKey("special_item"), PersistentDataType.STRING)) {
            event.setCancelled(true);
            String itemId = container.get(plugin.getNamespacedKey("special_item"), PersistentDataType.STRING);
            plugin.getSpecialItemManager().getEffects().use(player, itemId, item);
            return;
        }
        blockTraditionalWeaponsInArena(event, player, item);
    }

    /**
     * Only a right-click made with the main hand counts as "using" an item.
     *
     * @param event Interact event
     * @return True for main-hand right-clicks
     */
    private boolean isRightClickWithMainHand(PlayerInteractEvent event) {
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        return rightClick && event.getHand() == EquipmentSlot.HAND;
    }

    /**
     * Forward a role item to the role it belongs to (or refuse it to other players).
     *
     * @param event  Interact event
     * @param player Player using the item
     * @param item   Item used
     * @param roleId Name of the RoleType the item belongs to
     */
    private void handleRoleItem(PlayerInteractEvent event, Player player, ItemStack item, String roleId) {
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        if (ngnlPlayer.getRole().getRoleType().name().equals(roleId)) {
            ngnlPlayer.getRole().onItemUse(item);
        } else {
            MessageUtil.sendMessage(player, "&cCet objet ne peut être utilisé que par " + roleId + " !");
        }
    }

    /**
     * Traditional weapons are disabled in the arena: only the Love Gun and role items work.
     *
     * @param event  Interact event
     * @param player Player using the item
     * @param item   Item used
     */
    private void blockTraditionalWeaponsInArena(PlayerInteractEvent event, Player player, ItemStack item) {
        if (plugin.getGameManager().getGameState() != GameState.ARENA_PHASE) {
            return;
        }
        String arenaWorldName = plugin.getConfigManager().getGameConfig().getArenaWorldName();
        boolean weapon = item.getType() == Material.BOW || item.getType() == Material.CROSSBOW
                || item.getType().name().contains("SWORD");
        if (player.getWorld().getName().equals(arenaWorldName) && weapon) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, "&cUtilisez votre Love Gun !");
        }
    }

    /**
     * Stephanie's Love Gun snowball hitting a player.
     *
     * @param event Projectile hit event
     */
    @EventHandler
    public void onStephanieLoveGunHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        PersistentDataContainer container = projectile.getPersistentDataContainer();
        String shooterId = container.get(plugin.getNamespacedKey("stephanie_love_gun"), PersistentDataType.STRING);
        if (shooterId == null || !(event.getHitEntity() instanceof Player target)) {
            return;
        }

        Player shooter = Bukkit.getPlayer(UUID.fromString(shooterId));
        if (shooter == null) {
            return;
        }

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(shooter.getUniqueId());
        if (ngnlPlayer != null && ngnlPlayer.getRole() instanceof StephanieRole stephanieRole) {
            stephanieRole.useLoveGun(target);
        }
        projectile.remove();
    }

    // ------------------------------------------------------------------ mining

    /**
     * Block breaking rules and faction mining bonuses.
     *
     * @param event Block break event
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!plugin.getGameManager().isGameRunning()) {
            // Only allow ops to break blocks outside of the game
            if (!player.isOp()) {
                event.setCancelled(true);
            }
            return;
        }
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getGameManager().getGameState() == GameState.ARENA_PHASE && block.getType() == Material.GRASS_BLOCK) {
            event.setCancelled(true);
            return;
        }
        applyFactionMiningBonus(event, player, block);
    }

    /**
     * Ex-Machina members get one extra drop from ores.
     *
     * @param event  Block break event
     * @param player Miner
     * @param block  Broken block
     */
    private void applyFactionMiningBonus(BlockBreakEvent event, Player player, Block block) {
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null
                || ngnlPlayer.getRole().getRoleType().getFaction() != be.thespattt.ngnl.player.faction.FactionType.EX_MACHINA
                || !block.getType().name().contains("ORE")) {
            return;
        }
        event.setDropItems(false);
        block.getDrops(player.getInventory().getItemInMainHand(), player).forEach(drop -> {
            drop.setAmount(drop.getAmount() + 1);
            player.getWorld().dropItemNaturally(block.getLocation(), drop);
        });
    }

    // ------------------------------------------------------------------ GUIs

    /**
     * Clicks in the mini-game selection GUI.
     *
     * @param event Click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !plugin.getGameManager().isGameRunning()) {
            return;
        }
        if (event.getView().getTitle().contains("Mini-Game Selection")) {
            event.setCancelled(true);
            handleMiniGameSelectionClick(player, event.getCurrentItem());
        }
    }

    /**
     * Start the mini-game clicked in the selection GUI.
     *
     * @param player Player clicking
     * @param item   Clicked item
     */
    private void handleMiniGameSelectionClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String displayName = item.getItemMeta().getDisplayName();
        MiniGameType selectedType = null;
        for (MiniGameType type : MiniGameType.values()) {
            if (displayName.contains(type.getDisplayName())) {
                selectedType = type;
                break;
            }
        }
        if (selectedType == null) {
            return;
        }

        UUID opponentId = plugin.getGameManager().getGame().getScheduledMiniGameOpponent(player.getUniqueId());
        if (opponentId == null) {
            player.closeInventory();
            MessageUtil.sendMessage(player, "&cYou don't have a scheduled mini-game!");
            return;
        }

        plugin.getGameManager().getGame().setScheduledMiniGameType(player.getUniqueId(), opponentId, selectedType);
        player.closeInventory();
        MessageUtil.sendMessage(player, "&aYou selected the mini-game: &e" + selectedType.getDisplayName());
        plugin.getMiniGameManager().startScheduledMiniGame(player.getUniqueId(), opponentId);
    }

    // ------------------------------------------------------------------ combat tracking

    /**
     * Remember who hit whom (melee and projectiles) to attribute kills.
     *
     * @param event Damage event
     */
    @EventHandler
    public void onDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player damager = null;
        if (event.getDamager() instanceof Player direct) {
            damager = direct;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            damager = shooter;
        }
        if (damager != null && !damager.equals(victim)) {
            plugin.getCombatTracker().setLastDamager(victim.getUniqueId(), damager.getUniqueId());
        }
    }
}
