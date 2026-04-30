package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.role.solo.ThinkRole;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RiteCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    public RiteCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null || ngnlPlayer.getRole().getRoleType() != RoleType.THINK) {
            MessageUtil.sendMessage(player, "&cOnly Think Nirvalen can use this command.");
            return true;
        }

        if (!(ngnlPlayer.getRole() instanceof ThinkRole thinkRole)) {
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendMessage(player, "&cUsage: /rite <1|2>");
            return true;
        }

        if ("1".equals(args[0])) {
            thinkRole.useStealthRite();
        } else if ("2".equals(args[0])) {
            thinkRole.useLevitationRite();
        } else {
            MessageUtil.sendMessage(player, "&cUsage: /rite <1|2>");
        }
        return true;
    }
}
