package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.games.RockPaperScissorsGame;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Command for handling rock-paper-scissors mini-game
 */
public class RockPaperScissorsCommand implements CommandExecutor, TabCompleter {

    private final NoGameNoLife plugin;

    // Map to track active rock-paper-scissors games (gameId -> game instance)
    private final Map<String, RockPaperScissorsGame> activeGames = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public RockPaperScissorsCommand(NoGameNoLife plugin) {
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

        // Check if player is in an active rock-paper-scissors game
        RockPaperScissorsGame game = getPlayerGame(player.getUniqueId());

        if (game == null) {
            MessageUtil.sendMessage(player, "&cYou are not in an active rock-paper-scissors game!");
            return true;
        }

        // Check for valid choice
        if (args.length < 1) {
            MessageUtil.sendMessage(player, "&cUsage: /rps <rock|paper|scissors>");
            return true;
        }

        // Get choice
        String choice = args[0].toLowerCase();

        if (!choice.equals("rock") && !choice.equals("paper") && !choice.equals("scissors")) {
            MessageUtil.sendMessage(player, "&cInvalid choice. Please choose rock, paper, or scissors.");
            return true;
        }

        // Forward choice to game
        game.makeChoice(player, choice);

        return true;
    }

    /**
     * Register a rock-paper-scissors game
     *
     * @param gameId Unique game identifier
     * @param game Game instance
     */
    public void registerGame(String gameId, RockPaperScissorsGame game) {
        activeGames.put(gameId, game);
    }

    /**
     * Unregister a rock-paper-scissors game
     *
     * @param gameId Unique game identifier
     */
    public void unregisterGame(String gameId) {
        activeGames.remove(gameId);
    }

    /**
     * Get the rock-paper-scissors game for a player
     *
     * @param playerId UUID of the player
     * @return RockPaperScissorsGame instance or null if not found
     */
    public RockPaperScissorsGame getPlayerGame(UUID playerId) {
        for (RockPaperScissorsGame game : activeGames.values()) {
            if (game.hasPlayer(playerId)) {
                return game;
            }
        }

        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        if (args.length == 1) {
            // First argument - choice
            completions.add("rock");
            completions.add("paper");
            completions.add("scissors");

            // Filter by input
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(args[0].toLowerCase()));
        }

        return completions;
    }

    /**
     * Interface for games that need to check player presence
     */
    public interface PlayerGameChecker {
        /**
         * Check if a player is in the game
         *
         * @param playerId UUID of the player
         * @return True if player is in the game
         */
        boolean hasPlayer(UUID playerId);
    }
}