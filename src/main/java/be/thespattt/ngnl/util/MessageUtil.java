package be.thespattt.ngnl.util;

import be.thespattt.ngnl.NoGameNoLife;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Utility class for handling message formatting and logging
 */
public class MessageUtil {

    private static final String PREFIX = "&5[NGNL]&r ";

    /**
     * Send a message to a command sender with color translation
     *
     * @param sender Command sender to receive the message
     * @param message Message to send
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null) {
            return;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + message));
    }

    /**
     * Send a message to a player with color translation
     *
     * @param player Player to receive the message
     * @param message Message to send
     */
    public static void sendMessage(Player player, String message) {
        if (player == null) {
            return;
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + message));
    }

    /**
     * Send a message to a command sender without the plugin prefix
     *
     * @param sender Command sender to receive the message
     * @param message Message to send
     */
    public static void sendMessageNoPrefix(CommandSender sender, String message) {
        if (sender == null) {
            return;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Send a message to a player without the plugin prefix
     *
     * @param player Player to receive the message
     * @param message Message to send
     */
    public static void sendMessageNoPrefix(Player player, String message) {
        if (player == null) {
            return;
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Broadcast a message to all players
     *
     * @param message Message to broadcast
     */
    public static void broadcast(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + message));
    }

    /**
     * Broadcast a message to all players within a certain radius
     *
     * @param location Center location
     * @param radius Radius in blocks
     * @param message Message to broadcast
     */
    public static void broadcastNearby(Location location, double radius, String message) {
        String formattedMessage = ChatColor.translateAlternateColorCodes('&', PREFIX + message);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld()) &&
                    player.getLocation().distance(location) <= radius) {
                player.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * Broadcast a title to all players
     *
     * @param title Main title text
     * @param subtitle Subtitle text
     * @param fadeIn Fade in time in ticks
     * @param stay Stay time in ticks
     * @param fadeOut Fade out time in ticks
     */
    public static void broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        String formattedTitle = ChatColor.translateAlternateColorCodes('&', title);
        String formattedSubtitle = ChatColor.translateAlternateColorCodes('&', subtitle);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(formattedTitle, formattedSubtitle, fadeIn, stay, fadeOut);
        }
    }

    /**
     * Send a title to a player
     *
     * @param player Player to receive the title
     * @param title Main title text
     * @param subtitle Subtitle text
     * @param fadeIn Fade in time in ticks
     * @param stay Stay time in ticks
     * @param fadeOut Fade out time in ticks
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }

        String formattedTitle = ChatColor.translateAlternateColorCodes('&', title);
        String formattedSubtitle = ChatColor.translateAlternateColorCodes('&', subtitle);

        player.sendTitle(formattedTitle, formattedSubtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Log an info message to the console
     *
     * @param message Message to log
     */
    public static void logInfo(String message) {
        NoGameNoLife.getInstance().getLogger().info(message);
    }

    /**
     * Log a warning message to the console
     *
     * @param message Message to log
     */
    public static void logWarning(String message) {
        NoGameNoLife.getInstance().getLogger().warning(message);
    }

    /**
     * Log an error message to the console
     *
     * @param message Message to log
     */
    public static void logError(String message) {
        NoGameNoLife.getInstance().getLogger().log(Level.SEVERE, message);
    }

    /**
     * Log an error message with exception to the console
     *
     * @param message Message to log
     * @param throwable Exception to log
     */
    public static void logError(String message, Throwable throwable) {
        NoGameNoLife.getInstance().getLogger().log(Level.SEVERE, message, throwable);
    }

    /**
     * Format seconds into a readable time string (MM:SS)
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    public static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;

        return String.format("%02d:%02d", minutes, remainingSeconds);
    }

    /**
     * Format a longer duration (HH:MM:SS)
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    public static String formatLongTime(int seconds) {
        int hours = seconds / 3600;
        int remainingSeconds = seconds % 3600;
        int minutes = remainingSeconds / 60;
        remainingSeconds = remainingSeconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    /**
     * Format a message with a horizontal line border
     *
     * @param message Message to format
     * @param lineColor Color for the line
     * @param messageColor Color for the message
     * @return Formatted message with borders
     */
    public static String formatWithBorder(String message, ChatColor lineColor, ChatColor messageColor) {
        String line = lineColor + "----------------------------------------";
        return line + "\n" + messageColor + message + "\n" + line;
    }

    /**
     * Send a progress bar to a player
     *
     * @param player Player to receive the message
     * @param title Title of the progress bar
     * @param progress Current progress (0.0 to 1.0)
     * @param length Length of the progress bar
     * @param completeColor Color for completed progress
     * @param incompleteColor Color for incomplete progress
     */
    public static void sendProgressBar(Player player, String title, double progress,
                                       int length, ChatColor completeColor, ChatColor incompleteColor) {
        if (player == null) {
            return;
        }

        // Clamp progress between 0 and 1
        progress = Math.max(0.0, Math.min(1.0, progress));

        int completeLength = (int) (length * progress);
        int incompleteLength = length - completeLength;

        StringBuilder bar = new StringBuilder();
        bar.append(title).append(": ");

        // Add completed part
        bar.append(completeColor);
        for (int i = 0; i < completeLength; i++) {
            bar.append("█");
        }

        // Add incomplete part
        bar.append(incompleteColor);
        for (int i = 0; i < incompleteLength; i++) {
            bar.append("█");
        }

        // Add percentage
        bar.append(" ").append(ChatColor.GRAY).append(String.format("%.1f%%", progress * 100));

        sendMessageNoPrefix(player, bar.toString());
    }

    /**
     * Strip color codes from a string
     *
     * @param input String with color codes
     * @return String without color codes
     */
    public static String stripColor(String input) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', input));
    }
}