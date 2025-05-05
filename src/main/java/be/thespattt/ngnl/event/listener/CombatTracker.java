package be.thespattt.ngnl.event.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTracker {
    private final Map<UUID, UUID> lastDamager = new HashMap<>();

    public void setLastDamager(UUID victim, UUID damager) {
        lastDamager.put(victim, damager);
    }

    public UUID getLastDamager(UUID victim) {
        return lastDamager.get(victim);
    }
}

