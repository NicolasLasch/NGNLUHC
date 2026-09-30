package be.thespattt.ngnl.role;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.player.faction.FactionType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import be.thespattt.ngnl.util.ItemBuilder;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Abstract base class for all roles in the game
 */
public abstract class Role {

    protected final NoGameNoLife plugin;
    protected final UUID playerId;
    protected RoleType roleType;
    protected boolean arenaPhaseActive = false;
    private boolean arenaPhaseItemsGiven = false;
    /** Tasks started through the role helpers, cancelled when the role is cleaned up. */
    private final List<BukkitTask> ownedTasks = new ArrayList<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param playerId UUID of the player
     * @param roleType The role type
     */
    public Role(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.roleType = roleType;
    }

    /**
     * Called when the role is assigned to a player
     */
    public void onAssign() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Reveal the role through the card popup (falls back to chat text)
        plugin.getRoleCardManager().reveal(player, this);

        // Additional setup when assigned
        onRoleSetup();
    }

    /**
     * Called when the game ends or the role is removed: stops every task owned by the role
     */
    public void cleanup() {
        for (BukkitTask task : ownedTasks) {
            task.cancel();
        }
        ownedTasks.clear();
    }

    /**
     * Send role information to the player
     *
     * @param player The player
     */
    public void sendRoleInfo(Player player) {
        FactionType faction = getNGNLPlayer().getFaction();
        String factionName = (faction != null) ? faction.getColoredName() : "Unknown";

        // Send header
        MessageUtil.sendMessage(player,"&m                    ");
        MessageUtil.sendMessage(player,"");
        MessageUtil.sendMessage(player, "&7Your Are: &5&l" + getDisplayName());
        MessageUtil.sendMessage(player,"");
        MessageUtil.sendMessage(player, "&7Objective: &f" + getObjective());
        MessageUtil.sendMessage(player,"");
        MessageUtil.sendMessage(player, "&7Faction: &5" + factionName);
        MessageUtil.sendMessage(player,"");

        for (String line : getDescription()) {
            MessageUtil.sendMessage(player, "&f" + line);
        }
        MessageUtil.sendMessage(player,"");
        MessageUtil.sendMessage(player,"&m                    ");
    }

    /**
     * Role-specific setup
     */
    protected abstract void onRoleSetup();

    /**
     * Called when the arena phase starts
     */
    public void onArenaPhaseStart() {
        arenaPhaseActive = true;

        Player player = getPlayer();
        if (player == null) {
            return;
        }

        MessageUtil.sendMessage(player,"&7——————————————————————————————————————————————————");
        MessageUtil.sendMessage(player, "&fArena Phase Abilities");
        for (String line : getArenaPhaseDescription()) {
            MessageUtil.sendMessage(player, "&f" + line);
        }
        MessageUtil.sendMessage(player,"&7——————————————————————————————————————————————————");

        if (!arenaPhaseItemsGiven) {
            giveArenaPhaseItems(player);
            arenaPhaseItemsGiven = true;
        }
    }

    /**
     * Called when a mini-game starts with this player
     *
     * @param opponent UUID of the opponent
     * @param miniGameType Type of mini-game
     * @param isWinner True if this player won the PvP
     */
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        // Default empty implementation - to be overridden by specific roles
    }

    /**
     * Called when a mini-game ends with this player
     *
     * @param opponent UUID of the opponent
     * @param miniGameType Type of mini-game
     * @param isWinner True if this player won the mini-game
     */
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        //rien
    }

    /**
     * Called when the player dies
     *
     * @param killerId UUID of the killer (can be null)
     */
    public void onDeath(UUID killerId) {
        // Default empty implementation - to be overridden by specific roles
    }

    /**
     * Called when the player's partner dies (for duo roles)
     *
     * @param partnerId UUID of the partner
     * @param killerId UUID of the killer (can be null)
     */
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        // Default empty implementation - to be overridden by specific roles
    }

    /**
     * Give arena phase items to the player
     *
     * @param player The player
     */
    protected abstract void giveArenaPhaseItems(Player player);

    /**
     * Get the UUID of the player owning this role
     *
     * @return UUID of the player
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Get the player associated with this role
     *
     * @return The player, or null if offline
     */
    public Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    /**
     * Get the NGNLPlayer associated with this role
     *
     * @return The NGNLPlayer
     */
    public NGNLPlayer getNGNLPlayer() {
        return plugin.getPlayerManager().getNGNLPlayer(playerId);
    }

    /**
     * Get the role type
     *
     * @return The RoleType
     */
    public RoleType getRoleType() {
        return roleType;
    }

    /**
     * Send the three-name list given to duo members with their role:
     * one name is the partner, one is the closest player and one is random.
     *
     * @param player The player receiving the list
     */
    public void sendThreeNamesInformation(Player player) {
        List<String> names = new ArrayList<>();
        UUID partnerId = getPartnerUUID();
        Player partner = partnerId != null ? Bukkit.getPlayer(partnerId) : null;
        if (partner != null) {
            names.add(partner.getName());
        }

        Player closest = findClosestOtherPlayer(player, partnerId);
        if (closest != null) {
            names.add(closest.getName());
        }

        Player random = pickRandomOtherPlayer(player, names);
        if (random != null) {
            names.add(random.getName());
        }

        Collections.shuffle(names);
        MessageUtil.sendMessage(player, "&7Trois noms à connaître — &fl'un est ton allié, l'un est proche de toi, le dernier est aléatoire :");
        names.forEach(name -> MessageUtil.sendMessage(player, "&f - &e" + name));
    }

    /**
     * Find the closest alive player in the same world, ignoring one player
     *
     * @param player Reference player
     * @param ignoredId UUID to ignore (the partner), can be null
     * @return The closest player or null
     */
    private Player findClosestOtherPlayer(Player player, UUID ignoredId) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player other : player.getWorld().getPlayers()) {
            boolean ignored = other.getUniqueId().equals(player.getUniqueId()) || other.getUniqueId().equals(ignoredId);
            if (ignored || !plugin.getGameManager().isPlayerAlive(other.getUniqueId())) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    /**
     * Pick a random alive player that is not the reference player nor already listed
     *
     * @param player Reference player
     * @param alreadyListed Names already in the list
     * @return A random player or null
     */
    private Player pickRandomOtherPlayer(Player player, List<String> alreadyListed) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean excluded = online.getUniqueId().equals(player.getUniqueId()) || alreadyListed.contains(online.getName());
            if (!excluded && plugin.getGameManager().isPlayerAlive(online.getUniqueId())) {
                candidates.add(online);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(new Random().nextInt(candidates.size()));
    }

    /**
     * Get the display name of the role
     *
     * @return Display name
     */
    public String getDisplayName() {
        return roleType.getDisplayName();
    }

    /**
     * Check if the role is part of a duo
     *
     * @return True if this is a duo role
     */
    public boolean isDuo() {
        return roleType.isDuo();
    }

    /**
     * Get the partner's role type (for duo roles)
     *
     * @return The partner's RoleType, or null if not applicable
     */
    public RoleType getPartnerRoleType() {
        return roleType.getPartnerRoleType();
    }

    /**
     * Get the partner's UUID (for duo roles)
     *
     * @return The partner's UUID, or null if not applicable
     */
    public UUID getPartnerUUID() {
        if (!isDuo()) {
            return null;
        }

        return plugin.getRoleManager().getPlayerByRole(getPartnerRoleType());
    }

    /**
     * Check if arena phase abilities are active
     *
     * @return True if arena phase is active
     */
    public boolean isArenaPhaseActive() {
        return arenaPhaseActive;
    }

    /**
     * Get role description lines
     *
     * @return List of description lines
     */
    public abstract List<String> getDescription();

    /**
     * Get arena phase description lines
     *
     * @return List of arena phase description lines
     */
    public abstract List<String> getArenaPhaseDescription();

    /**
     * Get the objective of the role
     *
     * @return Objective description
     */
    public abstract String getObjective();

    /**
     * Handle item use for role-specific items
     *
     * @param item ItemStack used
     * @return True if item use was successful
     */
    public boolean onItemUse(ItemStack item) {
        // Default implementation does nothing
        // To be overridden by specific roles
        return false;
    }

    // ------------------------------------------------------------------ helpers shared by all roles

    /**
     * Start a repeating task that is automatically cancelled with the role
     *
     * @param task Task to run
     * @param delayTicks Delay before the first run
     * @param periodTicks Ticks between two runs
     * @return The scheduled task
     */
    protected BukkitTask runRepeating(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        ownedTasks.add(scheduled);
        return scheduled;
    }

    /**
     * Start a delayed task that is automatically cancelled with the role
     *
     * @param task Task to run
     * @param delayTicks Delay before the run
     * @return The scheduled task
     */
    protected BukkitTask runLater(Runnable task, long delayTicks) {
        BukkitTask scheduled = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        ownedTasks.add(scheduled);
        return scheduled;
    }

    /**
     * Check if the role's player is still alive in the game
     *
     * @return True if the player is alive
     */
    protected boolean isAlive() {
        return plugin.getGameManager().isPlayerAlive(playerId);
    }

    /**
     * Check a cooldown and start it when it is ready.
     * The cooldown is scaled by the configured ability cooldown multiplier.
     *
     * @param ability Name of the ability (unique for the player)
     * @param seconds Cooldown in seconds
     * @return True if the ability can be used (cooldown started), false if still cooling down
     */
    protected boolean tryUseCooldown(String ability, int seconds) {
        NGNLPlayer ngnlPlayer = getNGNLPlayer();
        Player player = getPlayer();
        if (ngnlPlayer == null || player == null) {
            return false;
        }

        int scaled = (int) Math.round(seconds * plugin.getConfigManager().getGameConfig().getAbilityCooldownMultiplier());
        if (ngnlPlayer.isAbilityOnCooldown(ability, scaled)) {
            int remaining = ngnlPlayer.getRemainingCooldown(ability, scaled);
            MessageUtil.sendMessage(player, "&cCooldown: &f" + formatCooldown(remaining));
            return false;
        }

        ngnlPlayer.useAbility(ability);
        return true;
    }

    /**
     * Forget the cooldown of an ability so it is immediately available again
     *
     * @param ability Name of the ability
     */
    protected void resetCooldown(String ability) {
        NGNLPlayer ngnlPlayer = getNGNLPlayer();
        if (ngnlPlayer != null) {
            ngnlPlayer.clearAbilityCooldown(ability);
        }
    }

    /**
     * Format a duration in seconds as m:ss
     *
     * @param seconds Duration in seconds
     * @return Formatted duration
     */
    protected String formatCooldown(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Build a role item carrying the role tag used to route right-clicks to this role
     *
     * @param material Item material
     * @param name Colored display name
     * @param lore Lore lines
     * @return The role item
     */
    protected ItemStack buildRoleItem(Material material, String name, String... lore) {
        return new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .glow(true)
                .setTag("role_item", roleType.name())
                .build();
    }

    /**
     * Give an item to the player, dropping it on the floor if the inventory is full
     *
     * @param player Receiving player
     * @param item Item to give
     */
    protected void giveItem(Player player, ItemStack item) {
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * List the alive players (other than the role's player) within a radius
     *
     * @param center Center of the search
     * @param radius Radius in blocks
     * @return Alive players in range, in the same world as the center
     */
    protected List<Player> nearbyAlivePlayers(Location center, double radius) {
        List<Player> result = new ArrayList<>();
        for (Player other : center.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(playerId) || !plugin.getGameManager().isPlayerAlive(other.getUniqueId())) {
                continue;
            }
            if (other.getLocation().distanceSquared(center) <= radius * radius) {
                result.add(other);
            }
        }
        return result;
    }

    /**
     * Find the closest alive player (other than the role's player and his partner/ally) in range
     *
     * @param range Maximum distance
     * @return The closest enemy or null
     */
    protected Player nearestEnemy(double range) {
        Player self = getPlayer();
        if (self == null) {
            return null;
        }
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player other : nearbyAlivePlayers(self.getLocation(), range)) {
            if (isFriendly(other.getUniqueId())) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(self.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    /**
     * Check if a player is the duo partner or the alliance partner of this role's player
     *
     * @param otherId UUID of the other player
     * @return True if the other player is on the same side
     */
    protected boolean isFriendly(UUID otherId) {
        if (otherId.equals(playerId) || otherId.equals(getPartnerUUID())) {
            return true;
        }
        NGNLPlayer ngnlPlayer = getNGNLPlayer();
        return ngnlPlayer != null && ngnlPlayer.hasAlliancePartner() && otherId.equals(ngnlPlayer.getAlliancePartner());
    }

    /**
     * Permanently add (or remove) hearts to the player's maximum health
     *
     * @param hearts Hearts to add (negative to remove)
     */
    protected void addMaxHearts(double hearts) {
        NGNLPlayer ngnlPlayer = getNGNLPlayer();
        if (ngnlPlayer != null) {
            ngnlPlayer.setMaxHealth(Math.max(2.0, ngnlPlayer.getMaxHealth() + hearts * 2));
        }
    }

    /**
     * Permanently set the player's maximum health
     *
     * @param hearts New number of hearts
     */
    protected void setMaxHearts(double hearts) {
        NGNLPlayer ngnlPlayer = getNGNLPlayer();
        if (ngnlPlayer != null) {
            ngnlPlayer.setMaxHealth(hearts * 2);
            Player player = getPlayer();
            if (player != null) {
                player.setHealth(Math.min(player.getMaxHealth(), hearts * 2));
            }
        }
    }

    /**
     * Give the role a chance to survive a lethal hit (Einzig's second life).
     * Called by the damage listener before a death happens; when it returns true the damage
     * is cancelled and the role has already restored the player.
     *
     * @return True if the role survived
     */
    public boolean tryCheatDeath() {
        return false;
    }

    /**
     * Whether this role is allowed to form an alliance with another solo player
     *
     * @return True if alliances are allowed (solo roles by default)
     */
    public boolean canFormAlliance() {
        return true;
    }

    /**
     * Give the player automatic regeneration for the whole game (Jibril, Corone)
     */
    protected void startNaturalRegeneration() {
        runRepeating(() -> {
            Player player = getPlayer();
            if (player != null && isAlive()) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.REGENERATION, 100, 0, false, false));
            }
        }, 20L, 20L * 20);
    }

    /**
     * Tell the role that another player died (used by roles reacting to kills/eliminations)
     *
     * @param victimId UUID of the eliminated player
     * @param killerId UUID of the killer (can be null)
     */
    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        // Default empty implementation - overridden by roles reacting to other eliminations
    }
}
