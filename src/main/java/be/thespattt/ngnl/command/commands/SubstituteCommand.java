package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SubstituteCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    public SubstituteCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&cThis command can only be used by players.");
            return true;
        }

        if (!plugin.getMiniGameSelectionManager().processSoraSubstitution(player)) {
            MessageUtil.sendMessage(player, "&cYou cannot request Shiro right now.");
        }
        return true;
    }
}
