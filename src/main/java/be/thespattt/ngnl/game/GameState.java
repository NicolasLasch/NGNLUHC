package be.thespattt.ngnl.game;

/**
 * Enum representing the different states of the game
 */
public enum GameState {

    /**
     * Game is waiting to start
     */
    WAITING("Waiting"),

    /**
     * Game is in the starting sequence
     */
    STARTING("Starting"),

    /**
     * Game is in the mining/qualification phase
     */
    MINING_PHASE("Mining Phase"),

    /**
     * Game is in the arena phase
     */
    ARENA_PHASE("Arena Phase"),

    /**
     * Game has ended
     */
    ENDED("Ended");

    private final String displayName;

    /**
     * Constructor
     *
     * @param displayName Display name for the game state
     */
    GameState(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Get the display name of the game state
     *
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }
}