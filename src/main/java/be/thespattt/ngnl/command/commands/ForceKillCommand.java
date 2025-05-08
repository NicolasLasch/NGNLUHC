package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command for testing player elimination by forcing a player's health to 0
 */
public class ForceKillCommand implements CommandExecutor, TabCompleter {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public ForceKillCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission
        if (!sender.hasPermission("ngnl.admin.forcekill")) {
            MessageUtil.sendMessage(sender, "&cYou don't have permission to use this command!");
            return true;
        }

        // Check if args are valid
        if (args.length < 1) {
            MessageUtil.sendMessage(sender, "&cUsage: /forcekill <player> [hearts]");
            return true;
        }

        // Find the target player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "&cPlayer not found: " + args[0]);
            return true;
        }

        // Get the number of hearts to remove (default all)
        int heartsToRemove = 10; // Default to removing all hearts
        if (args.length > 1) {
            try {
                heartsToRemove = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(sender, "&cInvalid number of hearts: " + args[1]);
                return true;
            }
        }

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            // If no game is running, just set health to 0
            target.setHealth(0);
            MessageUtil.sendMessage(sender, "&aForced death of " + target.getName() + "!");
            return true;
        }

        // Check if player is alive in game
        if (!plugin.getGameManager().isPlayerAlive(target.getUniqueId())) {
            MessageUtil.sendMessage(sender, "&c" + target.getName() + " is already eliminated from the game!");
            return true;
        }

        // Get the sender as the killer (if it's a player)
        UUID killerId = null;
        if (sender instanceof Player) {
            killerId = ((Player) sender).getUniqueId();
        }

        plugin.getGameManager().handlePlayerElimination(target.getUniqueId(), killerId);
        MessageUtil.sendMessage(sender, "&aForced elimination of " + target.getName() + "!");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }

            // Filter by input
            return filterStartingWith(completions, args[0]);
        } else if (args.length == 2) {
            // Complete heart amounts
            completions.add("1");
            completions.add("2");
            completions.add("5");
            completions.add("10");

            return filterStartingWith(completions, args[1]);
        }

        return completions;
    }

    /**
     * Filter a list of strings by those starting with a prefix
     *
     * @param list List to filter
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