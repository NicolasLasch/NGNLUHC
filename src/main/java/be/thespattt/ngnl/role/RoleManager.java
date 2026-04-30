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
        // Clear existing data
        playerRoles.clear();
        assignedRoles.clear();

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
     * Assign roles randomly to players
     *
     * @param players List of players
     */
    private void assignRolesRandomly(List<Player> players) {
        int playerCount = players.size();
        List<RoleType> availableRoles = new ArrayList<>();

        List<RoleType> enabledRoles = new ArrayList<>();
        for (RoleType roleType : RoleType.values()) {
            if (plugin.getConfigManager().getGameConfig().isRoleEnabled(roleType)) {
                enabledRoles.add(roleType);
            }
        }

        List<RoleType> soloRoles = new ArrayList<>();
        List<RoleType> duoRoles = new ArrayList<>();

        for (RoleType roleType : enabledRoles) {
            if (roleType.isDuo()) {
                if (enabledRoles.contains(roleType.getPartnerRoleType()) &&
                        !duoRoles.contains(roleType) &&
                        !duoRoles.contains(roleType.getPartnerRoleType())) {
                    duoRoles.add(roleType);
                }
            } else {
                soloRoles.add(roleType);
            }
        }

        Collections.shuffle(duoRoles);
        Collections.shuffle(soloRoles);
        availableRoles.addAll(soloRoles);

        for (RoleType duoRole : duoRoles) {
            if (availableRoles.size() + 2 <= playerCount) {
                availableRoles.add(duoRole);
                availableRoles.add(duoRole.getPartnerRoleType());
            }
        }

        Collections.shuffle(availableRoles);

        if (availableRoles.size() < playerCount) {
            MessageUtil.logWarning("Not enough available roles for all players! " +
                    "Available: " + availableRoles.size() + ", Players: " + playerCount);
            return;
        }

        for (int i = 0; i < Math.min(playerCount, availableRoles.size()); i++) {
            Player player = players.get(i);
            RoleType roleType = availableRoles.get(i);

            assignRoleToPlayer(player.getUniqueId(), roleType);
        }

        MessageUtil.logInfo("Randomly assigned " + Math.min(playerCount, availableRoles.size()) +
                " roles to " + playerCount + " players");
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
     * Assign a specific role to a player
     *
     * @param playerId UUID of the player
     * @param roleType Role type to assign
     * @return True if assignment was successful
     */
    public boolean assignRoleToPlayer(UUID playerId, RoleType roleType) {
        // Check if player already has a role
        if (playerRoles.containsKey(playerId)) {
            return false;
        }

        // Check if role is already assigned
        if (assignedRoles.containsKey(roleType)) {
            return false;
        }

        // Create role instance
        Role role = createRoleInstance(playerId, roleType);
        if (role == null) {
            return false;
        }

        // Register player and role
        playerRoles.put(playerId, role);
        assignedRoles.put(roleType, playerId);

        // Update NGNLPlayer
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (ngnlPlayer != null) {
            ngnlPlayer.setRole(role);
        }

        // Initialize role
        role.onAssign();

        return true;
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
        for (Role role : playerRoles.values()) {
            role.onArenaPhaseStart();
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
        playerRoles.clear();
        assignedRoles.clear();
    }

    public Collection<Role> getAllRoles() {
        return new ArrayList<>(playerRoles.values());
    }
}
