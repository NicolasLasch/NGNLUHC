package be.thespattt.ngnl.role;

/**
 * Implemented by roles that have temporary restrictions or protections in combat
 * (cannot attack, immune to falls, effect ended by combat...). The listeners query it generically.
 */
public interface CombatRestrictions {

    /**
     * Whether the role's player is currently forbidden to attack.
     *
     * @return True if attacks must be blocked
     */
    default boolean isAttackBlocked() {
        return false;
    }

    /**
     * Message shown when an attack is blocked.
     *
     * @return Legacy colored message
     */
    default String attackBlockedMessage() {
        return "&cTu ne peux pas attaquer pour le moment.";
    }

    /**
     * Whether the role's player currently takes no fall damage.
     *
     * @return True if fall damage must be cancelled
     */
    default boolean isFallImmune() {
        return false;
    }

    /**
     * Called whenever the role's player deals or takes damage.
     */
    default void onCombatInvolvement() {
        // Nothing by default
    }
}
