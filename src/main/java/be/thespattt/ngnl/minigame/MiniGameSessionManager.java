package be.thespattt.ngnl.minigame;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MiniGameSessionManager {

    private final Map<UUID, UUID> pendingSessions = new HashMap<>();

    public void registerPendingSession(UUID killer, UUID victim) {
        pendingSessions.put(killer, victim);
    }

    public UUID getPendingVictim(UUID killer) {
        return pendingSessions.get(killer);
    }

    public void clearPending(UUID killer) {
        pendingSessions.remove(killer);
    }
}
