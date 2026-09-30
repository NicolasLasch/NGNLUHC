package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Command for players to view their role information
 */
public class RoleCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public RoleCommand(NoGameNoLife plugin) {
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

        // Check if player is in the game
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou are not in the current game or you are dead!");
            return true;
        }

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null) {
            MessageUtil.sendMessage(player, "&cCould not find your player data!");
            return true;
        }

        // Check if player has a role
        Role role = ngnlPlayer.getRole();
        if (role == null) {
            MessageUtil.sendMessage(player, "&cYou don't have a role assigned yet!");
            return true;
        }

        // Reopen the role card popup (picture if the resource pack is loaded, text otherwise)
        plugin.getRoleCardManager().open(player, role, 0);

        return true;
    }
}