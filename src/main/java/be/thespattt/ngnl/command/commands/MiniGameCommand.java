package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Command for managing mini-games
 */
public class MiniGameCommand implements CommandExecutor, TabCompleter {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public MiniGameCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
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

        // Handle subcommands
        if (args.length < 1) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "select":
                return handleSelectCommand(player, args);

            case "accept":
                return handleAcceptCommand(player, args);

            case "decline":
                return handleDeclineCommand(player, args);

            case "forfeit":
                return handleForfeitCommand(player);

            case "help":
                sendHelpMessage(player);
                return true;

            default:
                MessageUtil.sendMessage(player, "&cUnknown subcommand: " + subCommand);
                sendHelpMessage(player);
                return true;
        }
    }

    /**
     * Handle the select subcommand
     *
     * @param player Player using the command
     * @param args   Command arguments
     * @return True if handled
     */
    private boolean handleSelectCommand(Player player, String[] args) {
        // Check if there are enough arguments
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /minigame select <type>");
            return true;
        }

        // Check if player has a pending opponent
        UUID victimId = plugin.getMiniGameSessionManager().getPendingVictim(player.getUniqueId());
        if (victimId == null) {
            MessageUtil.sendMessage(player, "&cYou don't have a pending mini-game!");
            return true;
        }

        // Get the mini-game type
        String typeString = args[1].toUpperCase();
        MiniGameType miniGameType = MiniGameType.getByName(typeString);

        if (miniGameType == null) {
            MessageUtil.sendMessage(player, "&cInvalid mini-game type: " + args[1]);
            MessageUtil.sendMessage(player, "&7Available types: " + String.join(", ", getAvailableMiniGameTypes()));
            return true;
        }

        // Get opponent
        Player victim = Bukkit.getPlayer(victimId);
        if (victim == null) {
            MessageUtil.sendMessage(player, "&cYour opponent is no longer online!");
            plugin.getMiniGameSessionManager().clearPending(player.getUniqueId());
            return true;
        }

        boolean success = plugin.getMiniGameEngine().startGame(miniGameType, player, victim);

        if (success) {
            plugin.getMiniGameSessionManager().clearPending(player.getUniqueId());

            MessageUtil.sendMessage(player, "&aStarting mini-game: &e" + miniGameType.getDisplayName());
            MessageUtil.sendMessage(victim, "&aStarting mini-game: &e" + miniGameType.getDisplayName());
        } else {
            MessageUtil.sendMessage(player, "&cFailed to start the mini-game!");
        }

        return true;
    }

    private boolean handleAcceptCommand(Player player, String[] args) {
        // This would handle accepting a mini-game invite
        // For now, let's just mention that this is not needed in the current implementation
        MessageUtil.sendMessage(player, "&aMinigames start automatically after selecting a type.");
        return true;
    }

    /**
     * Handle the decline subcommand
     *
     * @param player Player using the command
     * @param args   Command arguments
     * @return True if handled
     */
    private boolean handleDeclineCommand(Player player, String[] args) {
        UUID victimId = plugin.getMiniGameSessionManager().getPendingVictim(player.getUniqueId());
        if (victimId == null) {
            MessageUtil.sendMessage(player, "&cYou don't have a pending mini-game!");
            return true;
        }

        plugin.getMiniGameSessionManager().clearPending(player.getUniqueId());

        MessageUtil.sendMessage(player, "&aYou have declined the mini-game.");

        Player victim = Bukkit.getPlayer(victimId);
        if (victim != null) {
            MessageUtil.sendMessage(victim, "&c" + player.getName() + " has declined the mini-game.");

            double heartsToLose = 5.0;
            plugin.getGameManager().removePlayerHearts(player.getUniqueId(), heartsToLose);

            MessageUtil.sendMessage(player, "&cYou lost " + heartsToLose + " hearts for declining the mini-game!");
            MessageUtil.sendMessage(victim, "&a" + player.getName() + " lost " + heartsToLose + " hearts for declining the mini-game!");
        }

        return true;
    }

    private boolean handleForfeitCommand(Player player) {
        // Check if player is in a mini-game
        if (!plugin.getMiniGameEngine().isPlayerInMiniGame(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou are not in a mini-game!");
            return true;
        }

        // Get the mini-game
        be.thespattt.ngnl.minigame.MiniGameBase miniGame = plugin.getMiniGameEngine().getPlayerMiniGame(player.getUniqueId());

        // Determine winner (the other player)
        UUID winnerId = player.getUniqueId().equals(miniGame.getPlayer1UUID()) ?
                miniGame.getPlayer2UUID() : miniGame.getPlayer1UUID();

        // Notify players
        MessageUtil.sendMessage(player, "&cYou have forfeited the mini-game!");

        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null) {
            MessageUtil.sendMessage(winner, "&a" + player.getName() + " has forfeited the mini-game! You win!");
        }

        // End the game
        String gameId = miniGame.getPlayer1UUID().toString() + "-" + miniGame.getPlayer2UUID().toString();
        plugin.getMiniGameEngine().endMiniGame(gameId, winnerId);

        return true;
    }

    /**
     * Send the help message to a player
     *
     * @param player Player to send the message to
     */
    private void sendHelpMessage(Player player) {
        MessageUtil.sendMessage(player, "&6=== MiniGame Commands ===");
        MessageUtil.sendMessage(player, "&e/minigame select <type> &7- Select a mini-game type");
        MessageUtil.sendMessage(player, "&e/minigame forfeit &7- Forfeit the current mini-game");
        MessageUtil.sendMessage(player, "&e/minigame help &7- Show this help message");
        MessageUtil.sendMessage(player, "&6Available mini-game types: &7" + String.join(", ", getAvailableMiniGameTypes()));
    }

    /**
     * Get a list of available mini-game types
     *
     * @return List of mini-game type names
     */
    private List<String> getAvailableMiniGameTypes() {
        List<String> types = new ArrayList<>();

        for (MiniGameType type : MiniGameType.values()) {
            types.add(type.name());
        }

        return types;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands
            completions.addAll(Arrays.asList("select", "forfeit", "help"));

            // Filter by input
            return filterStartingWith(completions, args[0]);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("select")) {
                // Second argument for select - mini-game types
                for (MiniGameType type : MiniGameType.values()) {
                    completions.add(type.name());
                }

                return filterStartingWith(completions, args[1]);
            }
        }

        return completions;
    }

    /**
     * Filter a list of strings by those starting with a prefix
     *
     * @param list   List to filter
     * @param prefix Prefix to filter by
     * @return Filtered list
     */
    private List<String> filterStartingWith(List<String> list, String prefix) {
        List<String> filtered = new ArrayList<>();

        for (String item : list) {
            if (item.toLowerCase().startsWith(prefix.toLowerCase())) {
                filtered.add(item);
            }
        }

        return filtered;
    }
}