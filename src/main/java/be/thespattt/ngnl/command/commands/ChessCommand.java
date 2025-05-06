package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.games.ChessMiniGame;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChessCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    public ChessCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        MiniGameBase game = plugin.getMiniGameEngine().getPlayerMiniGame(player.getUniqueId());
        if (game instanceof ChessMiniGame chessGame) {
            chessGame.reopenBoard(player.getUniqueId());
            player.sendMessage("§aChess board reopened.");
        } else {
            player.sendMessage("§cYou are not in a chess game.");
        }

        return true;
    }
}
