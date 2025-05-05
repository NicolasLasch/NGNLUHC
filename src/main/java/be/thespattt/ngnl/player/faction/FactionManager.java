package be.thespattt.ngnl.player.faction;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manager class for factions in the game
 */
public class FactionManager {

    private final NoGameNoLife plugin;
    private final Map<FactionType, Integer> factionMemberCounts = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public FactionManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Load faction data
     */
    public void loadFactions() {
        // Initialize faction member counts
        for (FactionType type : FactionType.values()) {
            factionMemberCounts.put(type, 0);
        }

        MessageUtil.logInfo("Loaded " + FactionType.values().length + " factions");
    }

    /**
     * Apply faction effects to a player
     *
     * @param player Player to apply effects to
     * @param factionType Faction type
     */
    public void applyFactionEffects(Player player, FactionType factionType) {
        if (player == null || factionType == null) {
            return;
        }

        // Remove existing effects
        removeFactionEffects(player);

        // Apply faction-specific effects
        switch (factionType) {
            case IMANITY:
                // Imanity has better negotiation, no specific effect
                break;

            case FLUGEL:
                // Flügel can fly temporarily in special zones
                if (isInSpecialZone(player)) {
                    player.setAllowFlight(true);
                }
                break;

            case WEREBEASTS:
                // Werebeasts have better night vision
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                break;

            case EX_MACHINA:
                // Ex-Machina have more efficient equipment repair
                // This is handled in event listeners
                break;

            case ELVES:
                // Elves have better enchantments (handled in events)
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
                break;

            case OLD_DEUS:
                // Old Deus have resistance to environmental damage
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                break;

            case OTHER:
                // Other factions have unique bonuses based on role
                break;
        }
    }

    /**
     * Remove faction effects from a player
     *
     * @param player Player to remove effects from
     */
    public void removeFactionEffects(Player player) {
        if (player == null) {
            return;
        }

        // Remove potential faction-specific effects
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.setAllowFlight(false);
    }

    /**
     * Check if a player is in a special zone for faction bonuses
     *
     * @param player Player to check
     * @return True if in special zone
     */
    private boolean isInSpecialZone(Player player) {
        // This would check world metadata or specific regions
        // For now, just return false
        return false;
    }

    /**
     * Get the number of players in a faction
     *
     * @param factionType Faction type
     * @return Number of players
     */
    public int getFactionMemberCount(FactionType factionType) {
        return factionMemberCounts.getOrDefault(factionType, 0);
    }

    /**
     * Update faction member counts
     */
    public void updateFactionMemberCounts() {
        // Reset counts
        for (FactionType type : FactionType.values()) {
            factionMemberCounts.put(type, 0);
        }

        // Count players by faction
        for (Player player : Bukkit.getOnlinePlayers()) {
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
            if (ngnlPlayer != null && ngnlPlayer.getFaction() != null) {
                FactionType faction = ngnlPlayer.getFaction();
                factionMemberCounts.put(faction, factionMemberCounts.getOrDefault(faction, 0) + 1);
            }
        }
    }

    /**
     * Apply faction-specific bonuses to all players
     */
    public void applyAllFactionEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
            if (ngnlPlayer != null && ngnlPlayer.getFaction() != null) {
                applyFactionEffects(player, ngnlPlayer.getFaction());
            }
        }
    }

    /**
     * Check if two players are in the same faction
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     * @return True if in the same faction
     */
    public boolean areSameFaction(UUID player1Id, UUID player2Id) {
        NGNLPlayer ngnlPlayer1 = plugin.getPlayerManager().getNGNLPlayer(player1Id);
        NGNLPlayer ngnlPlayer2 = plugin.getPlayerManager().getNGNLPlayer(player2Id);

        if (ngnlPlayer1 == null || ngnlPlayer2 == null) {
            return false;
        }

        if (ngnlPlayer1.getFaction() == null || ngnlPlayer2.getFaction() == null) {
            return false;
        }

        return ngnlPlayer1.getFaction() == ngnlPlayer2.getFaction();
    }
}