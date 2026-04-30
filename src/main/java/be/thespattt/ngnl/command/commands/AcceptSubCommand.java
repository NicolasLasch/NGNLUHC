package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcceptSubCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    public AcceptSubCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&cThis command can only be used by players.");
            return true;
        }

        if (!plugin.getMiniGameSelectionManager().processShiroAccept(player)) {
            MessageUtil.sendMessage(player, "&cNo Sora substitution request is waiting for you.");
        }
        return true;
    }
}
