package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.DirectionArrow;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Abstract base class for all duo roles
 */
public abstract class DuoRole extends Role {

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type
     */
    public DuoRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);

        // Ensure this is a duo role
        if (!roleType.isDuo()) {
            throw new IllegalArgumentException("Role " + roleType + " is not a duo role");
        }
    }

    @Override
    public String getObjective() {
        return null;
    }

    /**
     * Send a private message to the partner
     *
     * @param message Message to send
     * @return True if message was sent
     */
    public boolean sendDuoMessage(String message) {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }

        // Get partner
        UUID partnerUUID = getPartnerUUID();
        if (partnerUUID == null) {
            MessageUtil.sendMessage(player, "&cYou don't have a partner to send a message to.");
            return false;
        }

        Player partner = Bukkit.getPlayer(partnerUUID);
        if (partner == null) {
            MessageUtil.sendMessage(player, "&cYour partner is offline.");
            return false;
        }

        // Check if duo messaging is enabled (e.g., once per episode)
        if (!canSendDuoMessage()) {
            MessageUtil.sendMessage(player, "&cYou have already used your duo message for this episode.");
            return false;
        }

        // Send message to partner
        MessageUtil.sendMessage(partner, "&9[Duo] " + player.getName() + ": &f" + message);
        MessageUtil.sendMessage(player, "&9[Duo] &7Message sent to " + partner.getName() + ": &f" + message);

        // Mark message as used
        markDuoMessageUsed();

        return true;
    }

    /**
     * Check if player can send a duo message
     *
     * @return True if message can be sent
     */
    protected boolean canSendDuoMessage() {
        // By default, allow one message per episode
        int currentEpisode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        int lastMessageEpisode = getNGNLPlayer().getLastDuoMessageEpisode();

        return lastMessageEpisode < currentEpisode;
    }

    /**
     * Mark duo message as used for the current episode
     */
    protected void markDuoMessageUsed() {
        int currentEpisode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        getNGNLPlayer().setLastDuoMessageEpisode(currentEpisode);
    }

    /**
     * Check if the player's partner is alive
     *
     * @return True if partner is alive
     */
    public boolean isPartnerAlive() {
        UUID partnerUUID = getPartnerUUID();
        if (partnerUUID == null) {
            return false;
        }

        return plugin.getGameManager().isPlayerAlive(partnerUUID);
    }

    /**
     * Get the partner player object
     *
     * @return Player object or null if offline/not found
     */
    public Player getPartnerPlayer() {
        UUID partnerUUID = getPartnerUUID();
        if (partnerUUID == null) {
            return null;
        }

        return Bukkit.getPlayer(partnerUUID);
    }

    /**
     * Permanently show an action-bar arrow pointing to the partner (roles that know
     * their partner's position from the start).
     *
     * @param partnerName Name displayed when the partner is unavailable
     */
    protected void trackPartnerWithArrow(String partnerName) {
        runRepeating(() -> {
            Player player = getPlayer();
            if (player == null || !isAlive()) {
                return;
            }
            Player partner = isPartnerAlive() ? getPartnerPlayer() : null;
            DirectionArrow.show(player, partner, partnerName, 30);
        }, 20L, 20L);
    }
}
