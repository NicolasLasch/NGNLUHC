package be.thespattt.ngnl.role;

import be.thespattt.ngnl.player.faction.FactionType;

/**
 * Enum representing all available roles in the game
 */
public enum RoleType {

    // Imanity faction roles (Duo)
    SORA("Sora", true, "SHIRO", FactionType.IMANITY),
    SHIRO("Shiro", true, "SORA", FactionType.IMANITY),

    STEPHANIE("Stephanie Dola", true, "MAKOTO", FactionType.IMANITY),
    MAKOTO("Makoto Dola", true, "STEPHANIE", FactionType.IMANITY),

    RIKU("Riku Dola", true, "SCHWI", FactionType.IMANITY),

    // Imanity faction roles (Solo)
    CORONE("Corone Dola", false, null, FactionType.IMANITY),
    EINZIG("Einzig", false, null, FactionType.IMANITY),

    // Flügel faction roles (Solo)
    JIBRIL("Jibril", false, null, FactionType.FLUGEL),
    AZRIEL("Azriel", false, null, FactionType.FLUGEL),

    // Werebeasts faction roles
    IZUNA("Izuna Hatsuse", true, "INO", FactionType.WEREBEASTS),
    INO("Ino Hatsuse", true, "IZUNA", FactionType.WEREBEASTS),
    PLUM("Plum", false, null, FactionType.WEREBEASTS),

    // Ex-Machina faction roles
    SCHWI("Schwi Dola", true, "RIKU", FactionType.EX_MACHINA),
    SHI("Shi", true, "KU", FactionType.EX_MACHINA),
    KU("Kū", true, "SHI", FactionType.EX_MACHINA),

    // Elves faction roles
    KURAMI("Kurami Zell", true, "FEEL", FactionType.ELVES),
    FEEL("Feel Nilvalen", true, "KURAMI", FactionType.ELVES),
    FIEL("Fiel", true, "CHLAMMY", FactionType.ELVES),
    CHLAMMY("Chlammy", true, "FIEL", FactionType.ELVES),
    THINK("Think Nirvalen", false, null, FactionType.ELVES),

    // Old Deus faction roles
    ARTOSH("Artosh", false, null, FactionType.OLD_DEUS),
    OKEIN("Ōkein", false, null, FactionType.OLD_DEUS),
    KAINAS("Kainas", false, null, FactionType.OLD_DEUS),
    TETO("Teto", false, null, FactionType.OLD_DEUS),
    HOLOU("Holou", false, null, FactionType.OLD_DEUS),

    // Other faction roles
    IVAN("Ivan Zell", true, "NONNA", FactionType.OTHER),
    NONNA("Nonna Zell", true, "IVAN", FactionType.OTHER),
    MIKO("Miko", false, null, FactionType.OTHER),
    GHOST("179 Ghost", false, null, FactionType.OTHER);
    /**
     * Constructor
     *
     * @param displayName Display name for the role
     * @param isDuo Whether the role is part of a duo
     * @param partnerRole The partner role for duo roles
     * @param faction The faction this role belongs to
     */
    private final String displayName;
    private final boolean isDuo;
    private final String partnerRoleName; // Changed to String
    private RoleType partnerRole; // Will be resolved after initialization
    private final FactionType faction;

    RoleType(String displayName, boolean isDuo, String partnerRoleName, FactionType faction) {
        this.displayName = displayName;
        this.isDuo = isDuo;
        this.partnerRoleName = partnerRoleName;
        this.partnerRole = null; // Will be set later
        this.faction = faction;
    }

    // Add a static initialization block to resolve partner roles
    static {
        for (RoleType roleType : values()) {
            if (roleType.isDuo && roleType.partnerRoleName != null) {
                roleType.partnerRole = valueOf(roleType.partnerRoleName);
            }
        }
    }

    /**
     * Get the display name of the role
     *
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if the role is part of a duo
     *
     * @return True if this is a duo role
     */
    public boolean isDuo() {
        return isDuo;
    }

    /**
     * Get the partner's role type (for duo roles)
     *
     * @return The partner's RoleType, or null if not applicable
     */
    public RoleType getPartnerRoleType() {
        return partnerRole;
    }

    /**
     * Get the faction this role belongs to
     *
     * @return FactionType
     */
    public FactionType getFaction() {
        return faction;
    }

    /**
     * Get a RoleType by its name (case-insensitive)
     *
     * @param name Name of the role
     * @return RoleType or null if not found
     */
    public static RoleType getByName(String name) {
        for (RoleType roleType : values()) {
            if (roleType.name().equalsIgnoreCase(name)) {
                return roleType;
            }
        }
        return null;
    }
}