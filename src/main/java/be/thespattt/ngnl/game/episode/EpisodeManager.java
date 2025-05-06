package be.thespattt.ngnl.game.episode;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manager class for game episodes
 */
public class EpisodeManager {

    private final NoGameNoLife plugin;

    private int currentEpisode;
    private int episodeTimeRemaining;
    private BukkitTask episodeTask;
    private boolean episodeTimerActive;

    private long gameStartTime;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public EpisodeManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.currentEpisode = 0;
        this.episodeTimeRemaining = 0;
        this.episodeTimerActive = false;
    }

    /**
     * Start the episode timer
     */
    public void startEpisodeTimer() {
        // Don't start if already running
        if (episodeTimerActive) {
            return;
        }

        // Reset episode counter
        currentEpisode = 1;

        // Get episode length from config (in minutes)
        int episodeLength = plugin.getConfigManager().getGameConfig().getEpisodeLength();
        episodeTimeRemaining = episodeLength * 60; // Convert to seconds

        // Start timer task (runs every second)
        episodeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickEpisodeTimer, 20L, 20L);
        episodeTimerActive = true;

        // Broadcast episode start
        broadcastEpisodeStart();
    }

    /**
     * Stop the episode timer
     */
    public void stopEpisodeTimer() {
        if (episodeTask != null) {
            episodeTask.cancel();
            episodeTask = null;
        }

        episodeTimerActive = false;
    }

    /**
     * Timer tick method
     */
    private void tickEpisodeTimer() {
        // Decrement time remaining
        episodeTimeRemaining--;

        // Update scoreboard
        plugin.getGameManager().getGame().getScoreboardManager().updateScoreboardsForAllPlayers();

        // Check if episode is over
        if (episodeTimeRemaining <= 0) {
            endCurrentEpisode();
        }
    }

    /**
     * End the current episode and start the next one
     */
    private void endCurrentEpisode() {
        // Broadcast episode end
        broadcastEpisodeEnd();

        // Increment episode counter
        currentEpisode++;

        // Reset timer
        int episodeLength = plugin.getConfigManager().getGameConfig().getEpisodeLength();
        episodeTimeRemaining = episodeLength * 60; // Convert to seconds

        // Broadcast new episode start
        broadcastEpisodeStart();

        // Check for game state changes based on episode
        checkEpisodeTriggers();
    }

    /**
     * Check for game state changes based on current episode
     */
    private void checkEpisodeTriggers() {
        // Check if PvP should be enabled
        int pvpEpisode = 2;

        if (currentEpisode == 2) {
            // Assign roles and factions
            plugin.getRoleManager().assignRoles();

        }

        if (currentEpisode == pvpEpisode) {
            // Broadcast PvP enable
            MessageUtil.broadcast("&ePvP is now enabled!");

            // Play sound to all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.2f, 1.0f);
            }
        }

        // Other episode-based triggers could be added here
    }

    /**
     * Broadcast episode start
     */
    private void broadcastEpisodeStart() {
        MessageUtil.broadcast("&f&m     &r Episode &e" + currentEpisode + " &fhas started! &f&m     ");
    }

    /**
     * Broadcast episode end
     */
    private void broadcastEpisodeEnd() {
        // Play sound to all players
    }

    /**
     * Broadcast time warning
     *
     * @param minutes Minutes remaining
     */
    private void broadcastTimeWarning(double minutes) {
        String timeText;

        if (minutes >= 1) {
            timeText = (int) minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            timeText = (int) (minutes * 60) + " seconds";
        }

        // MessageUtil.broadcast("&6&lEPISODE " + currentEpisode + " ENDS IN " + timeText + "!");

    }

    /**
     * Get the current episode number
     *
     * @return Current episode
     */
    public int getCurrentEpisode() {
        return currentEpisode;
    }

    /**
     * Get time remaining in the current episode (in seconds)
     *
     * @return Time remaining
     */
    public int getEpisodeTimeRemaining() {
        return episodeTimeRemaining;
    }

    /**
     * Check if the episode timer is active
     *
     * @return True if active
     */
    public boolean isEpisodeTimerActive() {
        return episodeTimerActive;
    }

    /**
     * Format the remaining time as MM:SS
     *
     * @return Formatted time string
     */
    public String getFormattedTimeRemaining() {
        int minutes = episodeTimeRemaining / 60;
        int seconds = episodeTimeRemaining % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }

    public int getTotalElapsedSeconds() {
        return (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
    }

    public String formatSeconds(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    public void setGameStartTime(long startTime) {
        this.gameStartTime = startTime;
    }
}