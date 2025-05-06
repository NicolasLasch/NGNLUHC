package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for accessing the configuration GUI
 */
public class ConfigCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public ConfigCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, "&cThis command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check if player has permission
        if (!player.hasPermission("ngnl.admin")) {
            MessageUtil.sendMessage(player, "&cYou don't have permission to use this command.");
            return true;
        }

        // Open configuration GUI
        plugin.getConfigGUIManager().openMainScreen(player);
        MessageUtil.sendMessage(player, "&aOpening configuration menu...");

        return true;
    }
}