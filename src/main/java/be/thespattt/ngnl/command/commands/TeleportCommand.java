package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.role.solo.HolouRole;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeleportCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    public TeleportCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null || ngnlPlayer.getRole().getRoleType() != RoleType.HOLOU) {
            MessageUtil.sendMessage(player, "&cOnly Holou can use this command.");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /teleport <player1> <player2>");
            return true;
        }

        Player first = Bukkit.getPlayerExact(args[0]);
        Player second = Bukkit.getPlayerExact(args[1]);
        if (first == null || second == null) {
            MessageUtil.sendMessage(player, "&cBoth target players must be online.");
            return true;
        }

        if (ngnlPlayer.getRole() instanceof HolouRole holouRole) {
            holouRole.swapPlayers(first, second);
        }
        return true;
    }
}
