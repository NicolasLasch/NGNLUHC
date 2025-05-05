package be.thespattt.ngnl.minigame;

/**
 * Enum representing all mini-games in the game
 */
public enum MiniGameType {

    SPEED_BEDWARS("Speed Bedwars", "1v1 Bedwars with pre-built bridges and protected beds"),
    SPLEEF("Spleef", "Break blocks beneath your opponent to make them fall"),
    TNT_RUN("TNT Run", "Blocks disappear as you run, avoid falling"),
    PARKOUR("Parkour", "Complete a parkour course faster than your opponent"),
    BLOC_PARTY("Bloc Party", "Stand on the correct block shown on screen"),
    SUMO("Sumo", "Knock your opponent off the platform"),
    SPLEGG("Splegg", "Shoot eggs to remove blocks and make opponent fall"),
    FLOOR_IS_LAVA("The Floor is Lava", "Escape the rising lava and survive longer than your opponent"),
    DES_A_COUDRE("Dés à Coudre", "Jump into a pool of water from increasingly higher platforms"),
    ANVIL_RAIN("Anvil Rain", "Avoid falling anvils and push your opponent into danger"),
    WORD_CHAIN_BATTLE("Word Chain Battle", "The last letter of a word must be the first letter of the next word"),
    LOGICAL_DEDUCTION("Logical Deduction", "Solve a mystery based on logical clues"),
    MENTAL_CHESS("Mental Chess", "Simplified chess with time constraints"),
    MEMORY_GAME("Memory Game", "Memorize and reproduce patterns with increasing difficulty"),
    MATERIALIZATION_SHIRITORI("Materialization Shiritori", "Special wordplay game where named objects appear");

    private final String displayName;
    private final String description;

    /**
     * Constructor
     *
     * @param displayName Display name of the mini-game
     * @param description Brief description of the mini-game
     */
    MiniGameType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Get the display name of the mini-game
     *
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the description of the mini-game
     *
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get a random mini-game type
     *
     * @return Random MiniGameType
     */
    public static MiniGameType getRandom() {
        MiniGameType[] types = values();
        return types[(int) (Math.random() * types.length)];
    }

    /**
     * Get a MiniGameType by name (case-insensitive)
     *
     * @param name Name of the mini-game
     * @return MiniGameType or null if not found
     */
    public static MiniGameType getByName(String name) {
        for (MiniGameType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}