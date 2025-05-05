package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.duo.DuoRole;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for duo partners to communicate
 */
public class DuoCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public DuoCommand(NoGameNoLife plugin) {
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

        // Check if player has a role
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null) {
            MessageUtil.sendMessage(player, "&cYou don't have a role assigned!");
            return true;
        }

        Role role = ngnlPlayer.getRole();

        // Check if player has a duo role
        if (!(role instanceof DuoRole)) {
            MessageUtil.sendMessage(player, "&cYou don't have a duo partner to send messages to!");
            return true;
        }

        DuoRole duoRole = (DuoRole) role;

        // Check if there's a message
        if (args.length == 0) {
            MessageUtil.sendMessage(player, "&cUsage: /duo <message>");
            return true;
        }

        // Build message
        StringBuilder messageBuilder = new StringBuilder();
        for (String arg : args) {
            messageBuilder.append(arg).append(" ");
        }
        String message = messageBuilder.toString().trim();

        // Send message
        if (duoRole.sendDuoMessage(message)) {
            // Message sent successfully
            return true;
        } else {
            // Check if it's due to cooldown
            int currentEpisode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
            int lastMessageEpisode = ngnlPlayer.getLastDuoMessageEpisode();

            if (lastMessageEpisode == currentEpisode) {
                MessageUtil.sendMessage(player, "&cYou have already sent a duo message this episode!");
                MessageUtil.sendMessage(player, "&cYou can send another message in the next episode.");
            }
            // Other error messages would be handled in the sendDuoMessage method
        }

        return true;
    }
}