package be.thespattt.ngnl.game.scoreboard;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.player.faction.FactionType;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manager class for player scoreboards
 */
public class NGNLScoreboardManager {

    private final NoGameNoLife plugin;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Integer> playerScoreboardPages = new HashMap<>();
    private BukkitTask rotationTask;
    private static final int ROLES_PER_PAGE = 5;
    private static final int PAGE_ROTATION_SECONDS = 5;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public NGNLScoreboardManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        startRotationTask();
    }

    /**
     * Start the scoreboard rotation task
     */
    private void startRotationTask() {
        if (rotationTask != null) {
            rotationTask.cancel();
        }

        rotationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Rotate all player scoreboards
            for (UUID playerId : playerScoreboardPages.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    // Get total pages
                    int totalPages = getTotalRolePages();
                    if (totalPages > 1) {
                        // Increment page
                        int currentPage = playerScoreboardPages.get(playerId);
                        int nextPage = (currentPage + 1) % totalPages;
                        playerScoreboardPages.put(playerId, nextPage);

                        // Update scoreboard with new page
                        updateScoreboard(player);
                    }
                }
            }
        }, 20L * PAGE_ROTATION_SECONDS, 20L * PAGE_ROTATION_SECONDS);
    }

    /**
     * Get the total number of role pages
     *
     * @return Number of pages
     */
    private int getTotalRolePages() {
        List<RoleType> activeRoles = getActiveRoles();
        return (int) Math.ceil((double) activeRoles.size() / ROLES_PER_PAGE);
    }

    /**
     * Get all active roles in the game
     *
     * @return List of active role types
     */
    private List<RoleType> getActiveRoles() {
        List<RoleType> activeRoles = new ArrayList<>();

        // Only show roles if game is running
        if (plugin.getGameManager().isGameRunning()) {
            // Get all alive players
            for (UUID playerId : plugin.getGameManager().getGame().getAlivePlayers()) {
                NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
                if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
                    RoleType roleType = ngnlPlayer.getRole().getRoleType();
                    if (!activeRoles.contains(roleType)) {
                        activeRoles.add(roleType);
                    }
                }
            }
        }

        return activeRoles;
    }

    /**
     * Create a scoreboard for a player
     *
     * @param player Player to create scoreboard for
     */
    public void createScoreboard(Player player) {
        // Get Bukkit scoreboard manager
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();

        // Create new scoreboard
        Scoreboard scoreboard = manager.getNewScoreboard();

        // Create objective
        Objective objective = scoreboard.registerNewObjective("ngnl", "dummy");
        objective.setDisplayName(ChatColor.GOLD + "No Game No Life UHC");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Store scoreboard
        playerScoreboards.put(player.getUniqueId(), scoreboard);

        // Initialize page to 0
        playerScoreboardPages.put(player.getUniqueId(), 0);

        // Update scoreboard
        updateScoreboard(player);

        // Set player's scoreboard
        player.setScoreboard(scoreboard);
    }

    /**
     * Update a player's scoreboard
     *
     * @param player Player to update scoreboard for
     */
    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());

        if (scoreboard == null) {
            createScoreboard(player);
            return;
        }

        // Get the objective
        Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);

        if (objective == null) {
            objective = scoreboard.registerNewObjective("ngnl", "dummy");
            objective.setDisplayName(ChatColor.GOLD + "No Game No Life UHC");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Clear existing scores
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        // Get current game state
        GameState gameState = plugin.getGameManager().getGameState();

        // Add game state
        Score stateScore = objective.getScore(ChatColor.YELLOW + "Game: " + ChatColor.WHITE + gameState.getDisplayName());
        stateScore.setScore(15);

        // Add empty line
        Score emptyLine1 = objective.getScore(ChatColor.RESET + " ");
        emptyLine1.setScore(14);

        // Add episode info
        if (gameState == GameState.MINING_PHASE || gameState == GameState.ARENA_PHASE) {
            int currentEpisode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
            String timeRemaining = plugin.getGameManager().getGame().getEpisodeManager().getFormattedTimeRemaining();

            Score episodeScore = objective.getScore(ChatColor.YELLOW + "Episode: " + ChatColor.WHITE + currentEpisode);
            episodeScore.setScore(13);

            Score timeScore = objective.getScore(ChatColor.YELLOW + "Time: " + ChatColor.WHITE + timeRemaining);
            timeScore.setScore(12);
        } else {
            Score waitingScore = objective.getScore(ChatColor.YELLOW + "Waiting for game to start");
            waitingScore.setScore(13);
        }

        // Add player info
        if (gameState == GameState.MINING_PHASE || gameState == GameState.ARENA_PHASE) {
            // Add empty line
            Score emptyLine2 = objective.getScore(ChatColor.RESET + "  ");
            emptyLine2.setScore(11);

            // Add player count
            int alivePlayers = plugin.getGameManager().getGame().getAlivePlayers().size();
            Score playersScore = objective.getScore(ChatColor.YELLOW + "Players: " + ChatColor.WHITE + alivePlayers);
            playersScore.setScore(10);

            // Add border size
            int borderSize = 0;
            if (gameState == GameState.MINING_PHASE && plugin.getWorldManager().getMiningWorld() != null) {
                borderSize = (int) plugin.getWorldManager().getMiningWorld().getWorldBorder().getSize() / 2;
            } else if (gameState == GameState.ARENA_PHASE && plugin.getWorldManager().getArenaWorld() != null) {
                borderSize = (int) plugin.getWorldManager().getArenaWorld().getWorldBorder().getSize() / 2;
            }

            Score borderScore = objective.getScore(ChatColor.YELLOW + "Border: " + ChatColor.WHITE + borderSize);
            borderScore.setScore(9);

            // Add role info for this player
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
            if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
                Role role = ngnlPlayer.getRole();

                // Add empty line
                Score emptyLine3 = objective.getScore(ChatColor.RESET + "   ");
                emptyLine3.setScore(8);

                // Add role info
                Score roleScore = objective.getScore(ChatColor.YELLOW + "Role: " + ChatColor.WHITE + role.getDisplayName());
                roleScore.setScore(7);

                // Add faction info
                FactionType faction = role.getRoleType().getFaction();
                if (faction != null) {
                    Score factionScore = objective.getScore(ChatColor.YELLOW + "Faction: " + faction.getColor() + faction.getDisplayName());
                    factionScore.setScore(6);
                }

                // Add duo partner info
                if (role.isDuo()) {
                    UUID partnerUUID = role.getPartnerUUID();
                    if (partnerUUID != null) {
                        Player partnerPlayer = Bukkit.getPlayer(partnerUUID);
                        String partnerName = partnerPlayer != null ? partnerPlayer.getName() : "Unknown";
                        boolean partnerAlive = plugin.getGameManager().isPlayerAlive(partnerUUID);

                        String partnerStatus = partnerAlive ? ChatColor.GREEN + partnerName : ChatColor.RED + partnerName;
                        Score partnerScore = objective.getScore(ChatColor.YELLOW + "Partner: " + partnerStatus);
                        partnerScore.setScore(5);
                    }
                }

                // Add alliance info
                if (ngnlPlayer.hasAlliancePartner()) {
                    UUID allianceUUID = ngnlPlayer.getAlliancePartner();
                    if (allianceUUID != null) {
                        Player alliancePlayer = Bukkit.getPlayer(allianceUUID);
                        String allianceName = alliancePlayer != null ? alliancePlayer.getName() : "Unknown";
                        boolean allianceAlive = plugin.getGameManager().isPlayerAlive(allianceUUID);

                        String allianceStatus = allianceAlive ? ChatColor.GREEN + allianceName : ChatColor.RED + allianceName;
                        Score allianceScore = objective.getScore(ChatColor.YELLOW + "Alliance: " + allianceStatus);
                        allianceScore.setScore(4);
                    }
                }
            }

            // Add empty line
            Score emptyLine4 = objective.getScore(ChatColor.RESET + "    ");
            emptyLine4.setScore(3);

            // Add active roles page
            addActiveRolesPage(player, objective);
        }

        // Add footer
        Score footerScore = objective.getScore(ChatColor.GOLD + "ngnl.be.thespattt.net");
        footerScore.setScore(0);

        // Set player's scoreboard
        player.setScoreboard(scoreboard);
    }

    /**
     * Add active roles page to scoreboard
     *
     * @param player Player to add page for
     * @param objective Scoreboard objective
     */
    private void addActiveRolesPage(Player player, Objective objective) {
        List<RoleType> activeRoles = getActiveRoles();

        if (activeRoles.isEmpty()) {
            return;
        }

        // Get current page
        Integer page = playerScoreboardPages.get(player.getUniqueId());
        if (page == null) {
            page = 0;
            playerScoreboardPages.put(player.getUniqueId(), 0);
        }

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) activeRoles.size() / ROLES_PER_PAGE);

        // Add page info
        if (totalPages > 1) {
            Score pageScore = objective.getScore(ChatColor.YELLOW + "Roles Page: " + ChatColor.WHITE + (page + 1) + "/" + totalPages);
            pageScore.setScore(2);
        } else {
            Score rolesTitle = objective.getScore(ChatColor.YELLOW + "Active Roles:");
            rolesTitle.setScore(2);
        }

        // Add roles for this page
        int startIndex = page * ROLES_PER_PAGE;
        int endIndex = Math.min(startIndex + ROLES_PER_PAGE, activeRoles.size());

        for (int i = startIndex; i < endIndex; i++) {
            RoleType roleType = activeRoles.get(i);
            FactionType faction = roleType.getFaction();
            String factionColor = faction != null ? faction.getColor().toString() : ChatColor.WHITE.toString();

            // Get count of this role type
            int count = countPlayersWithRole(roleType);

            Score roleScore = objective.getScore(factionColor + roleType.getDisplayName() + ChatColor.GRAY + " (" + count + ")");
            roleScore.setScore(1);
        }
    }

    /**
     * Count players with a specific role type
     *
     * @param roleType Role type to count
     * @return Count of players with this role
     */
    private int countPlayersWithRole(RoleType roleType) {
        int count = 0;

        for (UUID playerId : plugin.getGameManager().getGame().getAlivePlayers()) {
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
            if (ngnlPlayer != null && ngnlPlayer.getRole() != null && ngnlPlayer.getRole().getRoleType() == roleType) {
                count++;
            }
        }

        return count;
    }

    /**
     * Update scoreboards for all players
     */
    public void updateScoreboardsForAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }

    /**
     * Remove a player's scoreboard
     *
     * @param player Player to remove scoreboard for
     */
    public void removeScoreboard(Player player) {
        playerScoreboards.remove(player.getUniqueId());
        playerScoreboardPages.remove(player.getUniqueId());
    }

    /**
     * Clear all scoreboards
     */
    public void clearScoreboards() {
        playerScoreboards.clear();
        playerScoreboardPages.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (rotationTask != null) {
            rotationTask.cancel();
            rotationTask = null;
        }
    }
}