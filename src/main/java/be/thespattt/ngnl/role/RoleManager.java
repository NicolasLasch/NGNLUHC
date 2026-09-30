package be.thespattt.ngnl.role;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.config.GameConfig;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.duo.*;
import be.thespattt.ngnl.role.solo.*;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manager class for roles in the game
 */
public class RoleManager {

    private final NoGameNoLife plugin;
    private final Map<UUID, Role> playerRoles = new HashMap<>();
    private final Map<RoleType, UUID> assignedRoles = new HashMap<>();

    /** Duo roles that learn their partner's identity directly instead of receiving three names. */
    private static final Set<RoleType> KNOWS_PARTNER = EnumSet.of(
            RoleType.SORA, RoleType.SHIRO, RoleType.FIEL, RoleType.CHLAMMY,
            RoleType.SHI, RoleType.KU, RoleType.IVAN);

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public RoleManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Load available roles
     */
    public void loadRoles() {
        // Nothing to do here, roles are defined in RoleType enum
        MessageUtil.logInfo("Loaded " + RoleType.values().length + " roles");
    }

    /**
     * Assign roles to players
     */
    public void assignRoles() {
        // Clear existing data (and stop the tasks of previous roles)
        clearRoles();

        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                players.add(player);
            }
        }

        if (players.isEmpty()) {
            MessageUtil.logWarning("No players to assign roles to!");
            return;
        }

        // Get configuration
        GameConfig config = plugin.getConfigManager().getGameConfig();
        boolean assignRandomly = config.isRandomRoleAssignment();

        if (assignRandomly) {
            assignRolesRandomly(players);
        } else {
            assignRolesFromConfig(players);
        }
    }

    /**
     * Assign roles randomly to players.
     * Every role is registered first, then all of them are revealed, so roles that
     * "know" another role at the start always find it assigned.
     *
     * @param players List of players
     */
    private void assignRolesRandomly(List<Player> players) {
        List<RoleType> selected = selectRolesForPlayerCount(players.size());
        if (selected == null) {
            return;
        }

        Collections.shuffle(selected);
        for (int i = 0; i < players.size(); i++) {
            registerRole(players.get(i).getUniqueId(), selected.get(i));
        }

        revealAllRoles();
        MessageUtil.logInfo("Randomly assigned " + players.size() + " roles");
    }

    /**
     * Pick exactly one role per player, mixing duos (two roles) and solos (one role)
     *
     * @param playerCount Number of players to give a role to
     * @return The selected roles, or null if there are not enough enabled roles
     */
    private List<RoleType> selectRolesForPlayerCount(int playerCount) {
        List<List<RoleType>> units = buildRoleUnits();
        Collections.shuffle(units);

        List<RoleType> selected = new ArrayList<>();
        for (List<RoleType> unit : units) {
            if (selected.size() + unit.size() <= playerCount) {
                selected.addAll(unit);
            }
        }

        if (selected.size() < playerCount) {
            MessageUtil.logWarning("Not enough enabled roles for all players! Available: "
                    + selected.size() + ", Players: " + playerCount);
            return null;
        }
        return selected;
    }

    /**
     * Group the enabled roles in assignable units: a solo role alone, a duo as a pair
     *
     * @return List of units (each unit is one solo role or both roles of a duo)
     */
    private List<List<RoleType>> buildRoleUnits() {
        GameConfig config = plugin.getConfigManager().getGameConfig();
        List<List<RoleType>> units = new ArrayList<>();
        Set<RoleType> used = new HashSet<>();

        for (RoleType roleType : RoleType.values()) {
            if (!config.isRoleEnabled(roleType) || used.contains(roleType)) {
                continue;
            }
            if (!roleType.isDuo()) {
                units.add(new ArrayList<>(List.of(roleType)));
                continue;
            }
            RoleType partner = roleType.getPartnerRoleType();
            if (config.isRoleEnabled(partner)) {
                units.add(new ArrayList<>(List.of(roleType, partner)));
                used.add(partner);
            }
        }
        return units;
    }

    /**
     * Assign roles from configuration
     *
     * @param players List of players
     */
    private void assignRolesFromConfig(List<Player> players) {
        // This would read role assignments from config
        // For now, just assign randomly
        assignRolesRandomly(players);
    }

    /**
     * Assign a specific role to a player and reveal it immediately (admin command).
     * A player who already has a role gets it replaced; during the arena phase the finale powers are given at once.
     *
     * @param playerId UUID of the player
     * @param roleType Role type to assign
     * @return True if assignment was successful
     */
    public boolean assignRoleToPlayer(UUID playerId, RoleType roleType) {
        UUID currentHolder = assignedRoles.get(roleType);
        if (currentHolder != null && !currentHolder.equals(playerId)) {
            return false;
        }
        removeRole(playerId);

        Role role = registerRole(playerId, roleType);
        if (role == null) {
            return false;
        }
        role.onAssign();
        if (plugin.getGameManager().getGameState() == be.thespattt.ngnl.game.GameState.ARENA_PHASE) {
            role.onArenaPhaseStart();
        }
        return true;
    }

    /**
     * Remove the role of a player (the role is cleaned up and can be assigned again).
     *
     * @param playerId UUID of the player
     */
    private void removeRole(UUID playerId) {
        Role old = playerRoles.remove(playerId);
        if (old != null) {
            old.cleanup();
            assignedRoles.remove(old.getRoleType());
            NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(playerId);
            if (data != null) {
                data.setRole(null);
            }
        }
    }

    /**
     * Create a role and register it for a player without revealing it
     *
     * @param playerId UUID of the player
     * @param roleType Role type to assign
     * @return The created role, or null if the player or the role is already taken
     */
    private Role registerRole(UUID playerId, RoleType roleType) {
        if (playerRoles.containsKey(playerId) || assignedRoles.containsKey(roleType)) {
            return null;
        }

        Role role = createRoleInstance(playerId, roleType);
        if (role == null) {
            return null;
        }

        playerRoles.put(playerId, role);
        assignedRoles.put(roleType, playerId);

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (ngnlPlayer != null) {
            ngnlPlayer.setRole(role);
        }
        return role;
    }

    /**
     * Reveal every registered role to its player (card popup + role setup),
     * then give duo members their list of three names.
     */
    private void revealAllRoles() {
        for (Role role : new ArrayList<>(playerRoles.values())) {
            role.onAssign();
        }
        for (Role role : new ArrayList<>(playerRoles.values())) {
            sendDuoNameListIfNeeded(role);
            plugin.getSpecialItemManager().informGodsAboutSuniaster(role);
            plugin.getFactionManager().informFlugelAboutLibrary(role);
        }
        plugin.getFactionManager().start();
    }

    /**
     * Give the three-name list to duo roles that do not know their partner from the start
     *
     * @param role Role that may receive the list
     */
    private void sendDuoNameListIfNeeded(Role role) {
        Player player = role.getPlayer();
        if (player != null && role.isDuo() && !KNOWS_PARTNER.contains(role.getRoleType())) {
            role.sendThreeNamesInformation(player);
        }
    }

    /**
     * Create a role instance based on role type
     *
     * @param playerId UUID of the player
     * @param roleType Role type to create
     * @return Role instance or null if creation failed
     */
    private Role createRoleInstance(UUID playerId, RoleType roleType) {
        switch (roleType) {
            // Duo roles
            case SORA:
                return new SoraRole(plugin, playerId);
            case SHIRO:
                return new ShiroRole(plugin, playerId);
            case STEPHANIE:
                return new StephanieRole(plugin, playerId);
            case MAKOTO:
                return new MakotoRole(plugin, playerId);
            case KURAMI:
                return new KuramiRole(plugin, playerId, RoleType.KURAMI);
            case FEEL:
                return new FeelRole(plugin, playerId, RoleType.FEEL);
            case RIKU:
                return new RikuRole(plugin, playerId, RoleType.RIKU);
            case SCHWI:
                return new SchwiRole(plugin, playerId, RoleType.SCHWI);
            case IZUNA:
                return new IzunaRole(plugin, playerId, RoleType.IZUNA);
            case INO:
                return new InoRole(plugin, playerId, RoleType.INO);
            case NONNA:
                return new NonnaRole(plugin, playerId, RoleType.NONNA);
            case IVAN:
                return new IvanRole(plugin, playerId, RoleType.IVAN);
            case FIEL:
                return new FielRole(plugin, playerId, RoleType.FIEL);
            case CHLAMMY:
                return new ChlammyRole(plugin, playerId, RoleType.CHLAMMY);
            case SHI:
                return new ShiRole(plugin, playerId, RoleType.SHI);
            case KU:
                return new KuRole(plugin, playerId, RoleType.KU);

            // Solo roles
            case JIBRIL:
                return new JibrilRole(plugin, playerId, RoleType.JIBRIL);
            case CORONE:
                return new CoroneRole(plugin, playerId, RoleType.CORONE);
            case MIKO:
                return new MikoRole(plugin, playerId, RoleType.MIKO);
            case TETO:
                return new TetoRole(plugin, playerId, RoleType.TETO);
            case ARTOSH:
                return new ArtoshRole(plugin, playerId, RoleType.ARTOSH);
            case OKEIN:
                return new OkeinRole(plugin, playerId, RoleType.OKEIN);
            case KAINAS:
                return new KainasRole(plugin, playerId, RoleType.KAINAS);
            case HOLOU:
                return new HolouRole(plugin, playerId, RoleType.HOLOU);
            case EINZIG:
                return new EinzigRole(plugin, playerId, RoleType.EINZIG);
            case THINK:
                return new ThinkRole(plugin, playerId, RoleType.THINK);
            case GHOST:
                return new GhostRole(plugin, playerId, RoleType.GHOST);
            case AZRIEL:
                return new AzrielRole(plugin, playerId, RoleType.AZRIEL);
            case PLUM:
                return new PlumRole(plugin, playerId, RoleType.PLUM);

            default:
                return null;
        }
    }

    /**
     * Activate arena phase abilities for all roles
     */
    public void activateArenaPhaseAbilities() {
        for (Role role : new ArrayList<>(playerRoles.values())) {
            if (plugin.getGameManager().isPlayerAlive(role.getPlayerId())) {
                role.onArenaPhaseStart();
            }
        }
    }

    /**
     * Get a player's role
     *
     * @param playerId UUID of the player
     * @return Role or null if not found
     */
    public Role getPlayerRole(UUID playerId) {
        return playerRoles.get(playerId);
    }

    /**
     * Get the player assigned to a specific role
     *
     * @param roleType Role type to look for
     * @return UUID of the player or null if not assigned
     */
    public UUID getPlayerByRole(RoleType roleType) {
        return assignedRoles.get(roleType);
    }

    /**
     * Check if a player has a role
     *
     * @param playerId UUID of the player
     * @return True if player has a role
     */
    public boolean hasRole(UUID playerId) {
        return playerRoles.containsKey(playerId);
    }

    /**
     * Clear all role assignments
     */
    public void clearRoles() {
        plugin.getFactionManager().stop();
        for (Role role : playerRoles.values()) {
            role.cleanup();
        }
        playerRoles.clear();
        assignedRoles.clear();
    }

    public Collection<Role> getAllRoles() {
        return new ArrayList<>(playerRoles.values());
    }
}
