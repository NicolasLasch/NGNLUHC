package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.minigame.games.RockPaperScissorsGame;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Command for players to challenge others to mini-games
 */
public class ChallengeCommand implements CommandExecutor, TabCompleter {

    private final NoGameNoLife plugin;

    // Map to track challenge requests (requester UUID -> target UUID)
    private final Map<UUID, UUID> challengeRequests = new HashMap<>();
    // Map to track challenge wagers (requester UUID -> wager)
    private final Map<UUID, String> challengeWagers = new HashMap<>();
    // Map to track request expiration times
    private final Map<UUID, Long> requestExpireTimes = new HashMap<>();
    // Request expiration time in seconds
    private static final int REQUEST_EXPIRE_TIME = 60;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public ChallengeCommand(NoGameNoLife plugin) {
        this.plugin = plugin;

        // Schedule task to clean up expired requests
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredRequests, 20L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, "&cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(player, "&cThere is no game in progress!");
            return true;
        }

        // Check if player is alive
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou are not alive in the current game!");
            return true;
        }

        // Check if pledges are active (challenges are part of pledges)
        if (!plugin.getGameManager().getGame().arePledgesActive()) {
            MessageUtil.sendMessage(player, "&cChallenges are only available during the mining phase!");
            return true;
        }

        // Command usage and handling
        if (args.length == 0) {
            MessageUtil.sendMessage(player, "&cUsage: /challenge <player> <wager> or /challenge accept <player>");
            return true;
        }

        // Handle challenge accept
        if (args[0].equalsIgnoreCase("accept")) {
            return handleAcceptChallenge(player, args);
        }

        // Handle challenge request
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /challenge <player> <wager>");
            MessageUtil.sendMessage(player, "&7Valid wagers: diamonds, levels, hearts, information");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cPlayer not found: " + args[0]);
            return true;
        }

        // Can't challenge yourself
        if (target.equals(player)) {
            MessageUtil.sendMessage(player, "&cYou cannot challenge yourself!");
            return true;
        }

        // Check if target is alive
        if (!plugin.getGameManager().isPlayerAlive(target.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c" + target.getName() + " is not alive in the current game!");
            return true;
        }

        // Validate wager
        String wager = args[1].toLowerCase();
        if (!isValidWager(wager)) {
            MessageUtil.sendMessage(player, "&cInvalid wager: " + wager);
            MessageUtil.sendMessage(player, "&7Valid wagers: diamonds, levels, hearts, information");
            return true;
        }

        // Send challenge request
        challengeRequests.put(player.getUniqueId(), target.getUniqueId());
        challengeWagers.put(player.getUniqueId(), wager);
        requestExpireTimes.put(player.getUniqueId(), System.currentTimeMillis() + (REQUEST_EXPIRE_TIME * 1000));

        MessageUtil.sendMessage(player, "&aChallenge request sent to " + target.getName() + " with wager: " + wager + "!");
        MessageUtil.sendMessage(player, "&7The request will expire in " + REQUEST_EXPIRE_TIME + " seconds.");

        MessageUtil.sendMessage(target, "&6" + player.getName() + " has challenged you to a mini-game!");
        MessageUtil.sendMessage(target, "&6Wager: &e" + wager);
        MessageUtil.sendMessage(target, "&6Type '/challenge accept " + player.getName() + "' to accept.");
        MessageUtil.sendMessage(target, "&7The request will expire in " + REQUEST_EXPIRE_TIME + " seconds.");

        return true;
    }

    /**
     * Handle challenge acceptance
     *
     * @param player Player accepting the challenge
     * @param args Command arguments
     * @return True if handled
     */
    private boolean handleAcceptChallenge(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /challenge accept <player>");
            return true;
        }

        Player challenger = Bukkit.getPlayer(args[1]);
        if (challenger == null) {
            MessageUtil.sendMessage(player, "&cPlayer not found: " + args[1]);
            return true;
        }

        // Check if there's a pending challenge
        if (!challengeRequests.containsKey(challenger.getUniqueId()) ||
                !challengeRequests.get(challenger.getUniqueId()).equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou don't have a pending challenge from " + challenger.getName() + "!");
            return true;
        }

        // Check if challenge has expired
        if (requestExpireTimes.containsKey(challenger.getUniqueId()) &&
                System.currentTimeMillis() > requestExpireTimes.get(challenger.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cThe challenge from " + challenger.getName() + " has expired!");
            challengeRequests.remove(challenger.getUniqueId());
            challengeWagers.remove(challenger.getUniqueId());
            requestExpireTimes.remove(challenger.getUniqueId());
            return true;
        }

        // All checks passed, start mini-game
        String wager = challengeWagers.get(challenger.getUniqueId());

        // Remove request
        challengeRequests.remove(challenger.getUniqueId());
        challengeWagers.remove(challenger.getUniqueId());
        requestExpireTimes.remove(challenger.getUniqueId());

        // Notify players
        MessageUtil.sendMessage(player, "&aYou have accepted the challenge from " + challenger.getName() + "!");
        MessageUtil.sendMessage(challenger, "&a" + player.getName() + " has accepted your challenge!");

        // Start rock-paper-scissors mini-game
        startRockPaperScissorsGame(challenger, player, wager);

        return true;
    }

    /**
     * Start a rock-paper-scissors mini-game
     *
     * @param challenger Player who initiated the challenge
     * @param target Player who accepted the challenge
     * @param wager Wager for the challenge
     */
    private void startRockPaperScissorsGame(Player challenger, Player target, String wager) {
        // Broadcast challenge
        MessageUtil.broadcast("&6" + challenger.getName() + " and " + target.getName() +
                " are starting a challenge with wager: " + wager + "!");

        // Create game instance
        RockPaperScissorsGame game = new RockPaperScissorsGame(plugin, challenger, target, wager);
        game.start();
    }

    /**
     * Check if a wager is valid
     *
     * @param wager Wager to check
     * @return True if wager is valid
     */
    private boolean isValidWager(String wager) {
        return wager.equals("diamonds") ||
                wager.equals("levels") ||
                wager.equals("hearts") ||
                wager.equals("information");
    }

    /**
     * Clean up expired challenge requests
     */
    private void cleanupExpiredRequests() {
        long currentTime = System.currentTimeMillis();

        // Find expired requests
        List<UUID> expiredRequests = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : requestExpireTimes.entrySet()) {
            if (currentTime > entry.getValue()) {
                expiredRequests.add(entry.getKey());
            }
        }

        // Remove expired requests
        for (UUID challengerId : expiredRequests) {
            UUID targetId = challengeRequests.get(challengerId);
            challengeRequests.remove(challengerId);
            challengeWagers.remove(challengerId);
            requestExpireTimes.remove(challengerId);

            // Notify players if online
            Player challenger = Bukkit.getPlayer(challengerId);
            if (challenger != null) {
                MessageUtil.sendMessage(challenger, "&cYour challenge request has expired!");
            }

            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                MessageUtil.sendMessage(target, "&cThe challenge request from " +
                        (challenger != null ? challenger.getName() : "a player") + " has expired!");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            // First argument
            completions.add("accept");

            // Add online players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) {
                    completions.add(onlinePlayer.getName());
                }
            }

            // Filter by input
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(args[0].toLowerCase()));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("accept")) {
                // Second argument for accept - show players with pending requests
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    UUID playerId = onlinePlayer.getUniqueId();

                    if (challengeRequests.containsKey(playerId) &&
                            challengeRequests.get(playerId).equals(player.getUniqueId())) {
                        completions.add(onlinePlayer.getName());
                    }
                }
            } else {
                // Second argument for challenge - wagers
                completions.add("diamonds");
                completions.add("levels");
                completions.add("hearts");
                completions.add("information");
            }

            // Filter by input
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(args[1].toLowerCase()));
        }

        return completions;
    }

    /**
     * Inner class for handling rock-paper-scissors mini-games
     */
}