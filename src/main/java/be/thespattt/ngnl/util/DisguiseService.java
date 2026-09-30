package be.thespattt.ngnl.util;

import be.thespattt.ngnl.NoGameNoLife;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lets a player look like another player (skin + name) for a while.
 * Uses the Paper profile API; everything is wrapped in try/catch so a failure only logs.
 */
public class DisguiseService {

    private final NoGameNoLife plugin;
    private final Map<UUID, PlayerProfile> originalProfiles = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public DisguiseService(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Disguise a player as another one.
     *
     * @param player Player who changes appearance
     * @param target Player to imitate
     * @return True if the disguise was applied
     */
    public boolean disguise(Player player, Player target) {
        try {
            originalProfiles.putIfAbsent(player.getUniqueId(), player.getPlayerProfile());
            PlayerProfile fake = Bukkit.createProfile(player.getUniqueId(), target.getName());
            fake.setProperties(target.getPlayerProfile().getProperties());
            player.setPlayerProfile(fake);
            refreshFor(player);
            return true;
        } catch (Throwable throwable) {
            MessageUtil.logError("Could not disguise " + player.getName(), throwable);
            return false;
        }
    }

    /**
     * Give a player his real appearance back.
     *
     * @param player Player to restore
     */
    public void restore(Player player) {
        PlayerProfile original = originalProfiles.remove(player.getUniqueId());
        if (original == null || !player.isOnline()) {
            return;
        }
        try {
            player.setPlayerProfile(original);
            refreshFor(player);
        } catch (Throwable throwable) {
            MessageUtil.logError("Could not restore " + player.getName(), throwable);
        }
    }

    /**
     * Check whether a player is currently disguised.
     *
     * @param playerId UUID of the player
     * @return True if disguised
     */
    public boolean isDisguised(UUID playerId) {
        return originalProfiles.containsKey(playerId);
    }

    /**
     * Restore every disguised player (plugin disable / game end).
     */
    public void restoreAll() {
        for (UUID playerId : new java.util.ArrayList<>(originalProfiles.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                restore(player);
            }
        }
        originalProfiles.clear();
    }

    /**
     * Hide then show a player to everybody so clients redraw his skin and name.
     *
     * @param player Player whose appearance changed
     */
    private void refreshFor(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    other.showPlayer(plugin, player);
                }
            }
        }, 2L);
    }
}
