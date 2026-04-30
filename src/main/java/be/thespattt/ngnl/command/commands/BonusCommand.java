package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.duo.ShiroRole;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BonusCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    public BonusCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&cThis command can only be used by players.");
            return true;
        }

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || !(ngnlPlayer.getRole() instanceof ShiroRole shiroRole)) {
            MessageUtil.sendMessage(player, "&cOnly Shiro can use this command.");
            return true;
        }

        if (!shiroRole.giveBonusToSora()) {
            MessageUtil.sendMessage(player, "&cCould not give Sora a bonus right now.");
        }
        return true;
    }
}
