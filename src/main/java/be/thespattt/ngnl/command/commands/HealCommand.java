package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.role.duo.RikuRole;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HealCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    public HealCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null || ngnlPlayer.getRole().getRoleType() != RoleType.RIKU) {
            MessageUtil.sendMessage(player, "&cOnly Riku can use this command.");
            return true;
        }

        if (ngnlPlayer.getRole() instanceof RikuRole rikuRole) {
            rikuRole.transferHealthToSchwi();
        }
        return true;
    }
}
