package be.thespattt.ngnl.player.faction;

import org.bukkit.ChatColor;

/**
 * Enum representing all factions (races) in the game
 */
public enum FactionType {

    IMANITY("Imanity", ChatColor.RED,
            "Humans who excel at negotiation and strategic thinking despite lacking magical abilities.",
            "Better capacity for negotiation/commerce with other players"),

    FLUGEL("Flügel", ChatColor.LIGHT_PURPLE,
            "Immortal winged beings created by the Old Deus as living weapons.",
            "Access to special zones with enchantment bonuses"),

    WEREBEASTS("Werebeasts", ChatColor.GOLD,
            "Animal-human hybrids with enhanced senses and physical abilities.",
            "Enhanced enemy detection within a limited radius"),

    EX_MACHINA("Ex-Machina", ChatColor.AQUA,
            "Mechanical lifeforms with advanced computational abilities.",
            "More efficient equipment repair"),

    ELVES("Elves", ChatColor.GREEN,
            "Magical beings with a strong connection to natural energy.",
            "Better efficiency with enchantments and potions"),

    OLD_DEUS("Old Deus", ChatColor.DARK_PURPLE,
            "Ancient godlike beings of immense power that created the world.",
            "Additional resistance to environmental damage"),

    OTHER("Other", ChatColor.GRAY,
            "Various races that don't fit into the main categories.",
            "Unique abilities determined by role");

    private final String displayName;
    private final ChatColor color;
    private final String description;
    private final String advantage;

    /**
     * Constructor
     *
     * @param displayName Display name of the faction
     * @param color Chat color associated with the faction
     * @param description Brief description of the faction
     * @param advantage The faction's gameplay advantage
     */
    FactionType(String displayName, ChatColor color, String description, String advantage) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
        this.advantage = advantage;
    }

    /**
     * Get the display name of the faction
     *
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the chat color associated with the faction
     *
     * @return ChatColor
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Get a colored display name
     *
     * @return Colored display name
     */
    public String getColoredName() {
        return color + displayName + ChatColor.RESET;
    }

    /**
     * Get the description of the faction
     *
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the faction's gameplay advantage
     *
     * @return Advantage description
     */
    public String getAdvantage() {
        return advantage;
    }

    /**
     * Get a FactionType by name (case-insensitive)
     *
     * @param name Name of the faction
     * @return FactionType or null if not found
     */
    public static FactionType getByName(String name) {
        for (FactionType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}