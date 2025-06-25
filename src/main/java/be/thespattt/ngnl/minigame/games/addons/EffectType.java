package be.thespattt.ngnl.minigame.games.addons;

/**
 * Types of effects that card combinations can have
 */
public enum EffectType {
    DAMAGE,     // Direct damage to opponent
    HEAL,       // Restore HP to self
    SHIELD,     // Block incoming damage
    REFLECT,    // Mirror damage back to attacker
    STATUS,     // Apply ongoing effects
    NONE        // No special effect
}