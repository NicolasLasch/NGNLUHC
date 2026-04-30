package be.thespattt.ngnl.event.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTracker {
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastCombatTime = new HashMap<>();

    public void setLastDamager(UUID victim, UUID damager) {
        lastDamager.put(victim, damager);
        lastCombatTime.put(victim, System.currentTimeMillis());
        lastCombatTime.put(damager, System.currentTimeMillis());
    }

    public UUID getLastDamager(UUID victim) {
        return lastDamager.get(victim);
    }

    public boolean isInCombat(UUID playerId) {
        Long lastTime = lastCombatTime.get(playerId);
        return lastTime != null && System.currentTimeMillis() - lastTime < 15_000L;
    }
}
