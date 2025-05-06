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
        int rolePages = (int) Math.ceil((double) activeRoles.size() / ROLES_PER_PAGE);
        return Math.max(1, rolePages) + 1;
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
        objective.setDisplayName(ChatColor.DARK_PURPLE + "No Game No Life UHC");
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

        Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            objective = scoreboard.registerNewObjective("ngnl", "dummy");
            objective.setDisplayName(ChatColor.LIGHT_PURPLE + "No Game No Life UHC");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Clear existing scores
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int page = playerScoreboardPages.getOrDefault(player.getUniqueId(), 0);

        if (page == 0) {
            // Page principale : infos générales

            // Ligne vide
            Score emptyLine1 = objective.getScore(ChatColor.RESET + " ");
            emptyLine1.setScore(16);

            // Game state
            GameState gameState = plugin.getGameManager().getGameState();
            Score stateScore = objective.getScore(ChatColor.WHITE + "Game: " + ChatColor.DARK_PURPLE + gameState.getDisplayName());
            stateScore.setScore(15);

            // Ligne vide
            Score emptyLine2 = objective.getScore(ChatColor.RESET + "  ");
            emptyLine2.setScore(14);

            if (gameState == GameState.MINING_PHASE || gameState == GameState.ARENA_PHASE) {
                int currentEpisode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
                int totalSeconds = plugin.getGameManager().getGame().getEpisodeManager().getTotalElapsedSeconds();
                String formattedTime = plugin.getGameManager().getGame().getEpisodeManager().formatSeconds(totalSeconds);

                Score episodeScore = objective.getScore(ChatColor.WHITE + "   Episode: " + ChatColor.DARK_PURPLE + currentEpisode);
                episodeScore.setScore(13);

                Score timeScore = objective.getScore(ChatColor.WHITE + "   Time: " + ChatColor.DARK_PURPLE + formattedTime);
                timeScore.setScore(12);
            } else {
                Score waitingScore = objective.getScore(ChatColor.WHITE + "   Waiting for game to start");
                waitingScore.setScore(13);
            }

            // Joueurs
            if (gameState == GameState.MINING_PHASE || gameState == GameState.ARENA_PHASE) {
                Score emptyLine3 = objective.getScore(ChatColor.RESET + "   ");
                emptyLine3.setScore(11);

                int alivePlayers = plugin.getGameManager().getGame().getAlivePlayers().size();
                Score playersScore = objective.getScore(ChatColor.WHITE + "   Players: " + ChatColor.DARK_PURPLE + alivePlayers);
                playersScore.setScore(10);

                int borderSize = 0;
                if (gameState == GameState.MINING_PHASE && plugin.getWorldManager().getMiningWorld() != null) {
                    borderSize = (int) plugin.getWorldManager().getMiningWorld().getWorldBorder().getSize() / 2;
                } else if (gameState == GameState.ARENA_PHASE && plugin.getWorldManager().getArenaWorld() != null) {
                    borderSize = (int) plugin.getWorldManager().getArenaWorld().getWorldBorder().getSize() / 2;
                }

                Score borderScore = objective.getScore(ChatColor.WHITE + "   Border: ±" + ChatColor.DARK_PURPLE + borderSize);
                borderScore.setScore(9);

                // Rôle du joueur
                NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
                if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
                    Role role = ngnlPlayer.getRole();

                    Score emptyLine4 = objective.getScore(ChatColor.RESET + "    ");
                    emptyLine4.setScore(8);

                    Score roleScore = objective.getScore(ChatColor.WHITE + "   Role: " + ChatColor.DARK_PURPLE + role.getDisplayName());
                    roleScore.setScore(7);

                    if (role.getRoleType().getFaction() != null) {
                        Score factionScore = objective.getScore(ChatColor.WHITE + "   Faction: " + ChatColor.DARK_PURPLE + role.getRoleType().getFaction().getDisplayName());
                        factionScore.setScore(6);
                    }

                    if (role.isDuo()) {
                        UUID partnerUUID = role.getPartnerUUID();
                        if (partnerUUID != null) {
                            Player partnerPlayer = Bukkit.getPlayer(partnerUUID);
                            String partnerName = partnerPlayer != null ? partnerPlayer.getName() : "Unknown";
                            Score partnerScore = objective.getScore(ChatColor.WHITE + "   Partner: " + ChatColor.DARK_PURPLE + partnerName);
                            partnerScore.setScore(5);
                        }
                    }

                    if (ngnlPlayer.hasAlliancePartner()) {
                        UUID allianceUUID = ngnlPlayer.getAlliancePartner();
                        if (allianceUUID != null) {
                            Player alliancePlayer = Bukkit.getPlayer(allianceUUID);
                            String allianceName = alliancePlayer != null ? alliancePlayer.getName() : "Unknown";
                            Score allianceScore = objective.getScore(ChatColor.WHITE + "   Alliance: " + ChatColor.DARK_PURPLE + allianceName);
                            allianceScore.setScore(4);
                        }
                    }
                }
            }

            // Footer
            Score footerScore = objective.getScore(ChatColor.DARK_PURPLE + "ngnl.be.thespattt.net");
            footerScore.setScore(0);

        } else {
            // Pages 1+ : rôles actifs
            Score title = objective.getScore(ChatColor.DARK_PURPLE + "Active Roles");
            title.setScore(15);
            addActiveRolesPage(player, objective);
        }

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
        // Add roles for this page
        // Add roles for this page
        int startIndex = page * ROLES_PER_PAGE;
        int endIndex = Math.min(startIndex + ROLES_PER_PAGE, activeRoles.size());

        int currentScore = 14;
        for (int i = startIndex; i < endIndex; i++) {
            RoleType roleType = activeRoles.get(i);
            Score roleScore = objective.getScore(ChatColor.WHITE + roleType.getDisplayName());
            roleScore.setScore(currentScore--);
        }
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