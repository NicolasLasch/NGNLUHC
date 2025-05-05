package be.thespattt.ngnl.role;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Abstract base class for all roles in the game
 */
public abstract class Role {

    protected final NoGameNoLife plugin;
    protected final UUID playerId;
    protected RoleType roleType;
    protected boolean arenaPhaseActive = false;

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

        // Send role information
        sendRoleInfo(player);

        // Additional setup when assigned
        onRoleSetup();
    }

    /**
     * Send role information to the player
     *
     * @param player The player
     */
    protected void sendRoleInfo(Player player) {
        // Send header
        MessageUtil.sendMessage(player, "&6======== Your Role: &e" + getDisplayName() + " &6========");

        // Send description
        for (String line : getDescription()) {
            MessageUtil.sendMessage(player, "&f" + line);
        }

        // Send objective
        MessageUtil.sendMessage(player, "&6Objective: &f" + getObjective());

        // Send footer
        MessageUtil.sendMessage(player, "&6==============================================");
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

        // Send arena phase information
        MessageUtil.sendMessage(player, "&c======== Arena Phase Abilities ========");
        for (String line : getArenaPhaseDescription()) {
            MessageUtil.sendMessage(player, "&f" + line);
        }
        MessageUtil.sendMessage(player, "&c=======================================");

        // Give arena phase items
        giveArenaPhaseItems(player);
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
        // Default empty implementation - to be overridden by specific roles
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
}