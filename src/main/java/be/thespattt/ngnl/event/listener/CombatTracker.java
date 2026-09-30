package be.thespattt.ngnl.event.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers who hit whom recently, to attribute kills and to know who is "in combat".
 */
public class CombatTracker {

    /** Time (ms) during which a damager is still considered responsible for a death. */
    private static final long KILL_ATTRIBUTION_MS = 15_000L;
    /** Time (ms) a player stays "in combat" after a hit. */
    private static final long COMBAT_DURATION_MS = 15_000L;

    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private final Map<UUID, Long> lastCombatTime = new HashMap<>();

    /**
     * Record a hit.
     *
     * @param victim  Player who was hit
     * @param damager Player who hit
     */
    public void setLastDamager(UUID victim, UUID damager) {
        long now = System.currentTimeMillis();
        lastDamager.put(victim, damager);
        lastDamageTime.put(victim, now);
        lastCombatTime.put(victim, now);
        lastCombatTime.put(damager, now);
    }

    /**
     * Get the player who recently hit a victim.
     *
     * @param victim Player who may have been hit
     * @return The damager, or null if nobody hit the victim in the last 15 seconds
     */
    public UUID getLastDamager(UUID victim) {
        Long time = lastDamageTime.get(victim);
        if (time == null || System.currentTimeMillis() - time > KILL_ATTRIBUTION_MS) {
            return null;
        }
        return lastDamager.get(victim);
    }

    /**
     * Forget the last damager of a player (after a duel started for example).
     *
     * @param victim Player to reset
     */
    public void clear(UUID victim) {
        lastDamager.remove(victim);
        lastDamageTime.remove(victim);
    }

    /**
     * Check whether a player fought in the last 15 seconds.
     *
     * @param playerId Player to check
     * @return True if in combat
     */
    public boolean isInCombat(UUID playerId) {
        Long lastTime = lastCombatTime.get(playerId);
        return lastTime != null && System.currentTimeMillis() - lastTime < COMBAT_DURATION_MS;
    }
}
