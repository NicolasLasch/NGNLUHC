package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.role.duo.ShiroRole;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DuoCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    public DuoCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(player, "&cThis command can only be used during the game!");
            return true;
        }

        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou can only use this command while alive!");
            return true;
        }

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null) {
            MessageUtil.sendMessage(player, "&cYou don't have a role!");
            return true;
        }

        if (args.length == 0) {
            showHelp(player, ngnlPlayer.getRole());
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "substitute":
                handleSubstitute(player, ngnlPlayer.getRole());
                break;

            case "acceptsub":
                handleAcceptSubstitution(player, ngnlPlayer.getRole());
                break;

            case "bonus":
                handleBonus(player, ngnlPlayer.getRole());
                break;

            case "cancel":
                handleCancelDefeat(player, ngnlPlayer.getRole());
                break;

            case "choosegame":
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "&cUsage: /duo choosegame <game_type>");
                    MessageUtil.sendMessage(player, "&eExample: /duo choosegame SPLEEF");
                    return true;
                }
                handleChooseGame(player, ngnlPlayer.getRole(), args[1]);
                break;

            case "message":
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "&cUsage: /duo message <message>");
                    return true;
                }

                StringBuilder message = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    message.append(args[i]).append(" ");
                }

                handleMessage(player, ngnlPlayer.getRole(), message.toString().trim());
                break;

            default:
                showHelp(player, ngnlPlayer.getRole());
                break;
        }

        return true;
    }

    private void showHelp(Player player, Role role) {
        MessageUtil.sendMessage(player, "&6=== Duo Commands ===");

        if (role.getRoleType() == RoleType.SORA) {
            MessageUtil.sendMessage(player, "&e/duo substitute &7- Request Shiro to substitute in mini-game");
        } else if (role.getRoleType() == RoleType.SHIRO) {
            MessageUtil.sendMessage(player, "&e/duo bonus &7- Give Sora a bonus during mini-game");
            MessageUtil.sendMessage(player, "&e/duo acceptsub &7- Accept Sora's substitution request");
        } else if (role.getRoleType() == RoleType.STEPHANIE) {
            MessageUtil.sendMessage(player, "&e/duo choosegame <type> &7- Choose mini-game type (once per game)");
        } else if (role.getRoleType() == RoleType.MAKOTO) {
            MessageUtil.sendMessage(player, "&e/duo cancel &7- Cancel mini-game defeat (once per game)");
        }

        if (role.isDuo()) {
            MessageUtil.sendMessage(player, "&e/duo message <msg> &7- Send a private message to your partner");
        }
    }

    private void handleSubstitute(Player player, Role role) {
        if (role.getRoleType() != RoleType.SORA) {
            MessageUtil.sendMessage(player, "&cOnly Sora can request substitution!");
            return;
        }

        if (!plugin.getMiniGameSelectionManager().processSoraSubstitution(player)) {
            MessageUtil.sendMessage(player, "&cYou cannot request substitution right now!");
        }
    }

    private void handleAcceptSubstitution(Player player, Role role) {
        if (role.getRoleType() != RoleType.SHIRO) {
            MessageUtil.sendMessage(player, "&cOnly Shiro can accept substitutions!");
            return;
        }

        if (!plugin.getMiniGameSelectionManager().processShiroAccept(player)) {
            MessageUtil.sendMessage(player, "&cNo substitution request to accept!");
        }
    }

    private void handleChooseGame(Player player, Role role, String gameTypeName) {
        if (role.getRoleType() != RoleType.STEPHANIE) {
            MessageUtil.sendMessage(player, "&cOnly Stephanie can choose mini-games!");
            return;
        }

        // NEW: Check if player is in a mini-game
        if (plugin.getMiniGameEngine().isPlayerInMiniGame(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou cannot use this command while in a mini-game!");
            return;
        }

        MiniGameType gameType = MiniGameType.getByName(gameTypeName);
        if (gameType == null) {
            MessageUtil.sendMessage(player, "&cInvalid mini-game type: " + gameTypeName);
            MessageUtil.sendMessage(player, "&eAvailable types: SPLEEF, TNT_RUN, PARKOUR, SUMO, MEMORY_GAME, etc.");
            return;
        }

        if (!plugin.getMiniGameSelectionManager().processStephanieChoice(player, gameType)) {
            MessageUtil.sendMessage(player, "&cYou cannot choose a mini-game right now!");
        }
    }

    private void handleCancelDefeat(Player player, Role role) {
        if (role.getRoleType() != RoleType.MAKOTO) {
            MessageUtil.sendMessage(player, "&cOnly Makoto can cancel defeats!");
            return;
        }

        // NEW: Check if player is in a mini-game
        if (plugin.getMiniGameEngine().isPlayerInMiniGame(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou cannot use this command while in a mini-game!");
            return;
        }

        if (!(role instanceof be.thespattt.ngnl.role.duo.MakotoRole)) {
            MessageUtil.sendMessage(player, "&cError: Invalid role type!");
            return;
        }

        be.thespattt.ngnl.role.duo.MakotoRole makotoRole = (be.thespattt.ngnl.role.duo.MakotoRole) role;

        if (makotoRole.cancelDefeat()) {
            plugin.getMiniGameStatsTracker().cancelLastLoss(player.getUniqueId());
            MessageUtil.sendMessage(player, "&aDefeat cancelled successfully!");
        } else {
            MessageUtil.sendMessage(player, "&cFailed to cancel defeat!");
        }
    }

    private void handleBonus(Player player, Role role) {
        if (role.getRoleType() != RoleType.SHIRO) {
            MessageUtil.sendMessage(player, "&cOnly Shiro can give bonuses!");
            return;
        }

        if (!(role instanceof ShiroRole)) {
            MessageUtil.sendMessage(player, "&cError: Invalid role type!");
            return;
        }

        ShiroRole shiroRole = (ShiroRole) role;

        if (shiroRole.giveBonusToSora()) {
            MessageUtil.sendMessage(player, "&aBonus given to Sora!");
        } else {
            MessageUtil.sendMessage(player, "&cFailed to give bonus to Sora!");
        }
    }

    private void handleMessage(Player player, Role role, String message) {
        if (!role.isDuo()) {
            MessageUtil.sendMessage(player, "&cYou don't have a duo partner!");
            return;
        }

        // Use the DuoRole's message system
        if (role instanceof be.thespattt.ngnl.role.duo.DuoRole) {
            be.thespattt.ngnl.role.duo.DuoRole duoRole = (be.thespattt.ngnl.role.duo.DuoRole) role;
            duoRole.sendDuoMessage(message);
        }
    }
}