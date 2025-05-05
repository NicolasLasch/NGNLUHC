package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main command handler for the plugin
 */
public class NGNLCommand implements CommandExecutor, TabCompleter {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public NGNLCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // Main command handler
        switch (args[0].toLowerCase()) {
            case "start":
                return handleStart(sender);

            case "stop":
                return handleStop(sender);

            case "arena":
                return handleArena(sender);

            case "roles":
                return handleRoles(sender);

            case "settings":
                return handleSettings(sender, args);

            case "reload":
                return handleReload(sender);

            case "setrole":
                return handleSetRole(sender, args);

            case "info":
                return handleInfo(sender);

            default:
                MessageUtil.sendMessage(sender, "&cUnknown sub-command: " + args[0]);
                sendHelp(sender);
                return true;
        }
    }

    /**
     * Handle the 'start' sub-command
     *
     * @param sender Command sender
     * @return True if handled
     */
    private boolean handleStart(CommandSender sender) {
        if (!hasPermission(sender, "ngnl.admin.start")) {
            return true;
        }

        if (plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(sender, "&cThe game is already running!");
            return true;
        }

        if (plugin.getGameManager().startGame()) {
            MessageUtil.sendMessage(sender, "&aGame started successfully!");
        } else {
            MessageUtil.sendMessage(sender, "&cFailed to start the game. Check console for details.");
        }

        return true;
    }

    /**
     * Handle the 'stop' sub-command
     *
     * @param sender Command sender
     * @return True if handled
     */
    private boolean handleStop(CommandSender sender) {
        if (!hasPermission(sender, "ngnl.admin.stop")) {
            return true;
        }

        if (!plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(sender, "&cThe game is not running!");
            return true;
        }

        plugin.getGameManager().endGame(true);
        MessageUtil.sendMessage(sender, "&aGame stopped successfully!");

        return true;
    }

    /**
     * Handle the 'arena' sub-command
     *
     * @param sender Command sender
     * @return True if handled
     */
    private boolean handleArena(CommandSender sender) {
        if (!hasPermission(sender, "ngnl.admin.arena")) {
            return true;
        }

        if (!plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(sender, "&cThe game is not running!");
            return true;
        }

        GameState state = plugin.getGameManager().getGameState();

        if (state != GameState.MINING_PHASE) {
            MessageUtil.sendMessage(sender, "&cArena phase can only be started during the mining phase!");
            return true;
        }

        if (plugin.getGameManager().forceArenaPhase()) {
            MessageUtil.sendMessage(sender, "&aArena phase started successfully!");
        } else {
            MessageUtil.sendMessage(sender, "&cFailed to start arena phase. Check console for details.");
        }

        return true;
    }

    /**
     * Handle the 'roles' sub-command
     *
     * @param sender Command sender
     * @return True if handled
     */
    private boolean handleRoles(CommandSender sender) {
        if (!hasPermission(sender, "ngnl.admin.roles")) {
            return true;
        }

        MessageUtil.sendMessage(sender, "&6=== Available Roles ===");

        // Group roles by faction
        for (be.thespattt.ngnl.player.faction.FactionType factionType : be.thespattt.ngnl.player.faction.FactionType.values()) {
            // Get roles in this faction
            List<RoleType> factionRoles = Arrays.stream(RoleType.values())
                    .filter(roleType -> roleType.getFaction() == factionType)
                    .collect(Collectors.toList());

            if (!factionRoles.isEmpty()) {
                MessageUtil.sendMessage(sender, factionType.getColoredName() + " Roles:");

                for (RoleType roleType : factionRoles) {
                    String partnerInfo = roleType.isDuo() ?
                            " (Duo with " + roleType.getPartnerRoleType().getDisplayName() + ")" :
                            " (Solo)";

                    MessageUtil.sendMessage(sender, "  &e" + roleType.getDisplayName() + "&7" + partnerInfo);
                }
            }
        }

        return true;
    }

    /**
     * Handle the 'settings' sub-command
     *
     * @param sender Command sender
     * @param args Command arguments
     * @return True if handled
     */
    private boolean handleSettings(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "ngnl.admin.settings")) {
            return true;
        }

        // If player, open settings GUI
        if (sender instanceof Player && args.length == 1) {
            // TODO: Implement settings GUI
            MessageUtil.sendMessage(sender, "&aOpening settings GUI...");

            return true;
        }

        // Otherwise, handle setting update via command
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&cUsage: /ngnl settings <setting> <value>");
            return true;
        }

        String setting = args[1].toLowerCase();
        String value = args[2];

        // Handle different settings
        switch (setting) {
            case "episodelength":
                try {
                    int length = Integer.parseInt(value);
                    if (length < 1 || length > 60) {
                        MessageUtil.sendMessage(sender, "&cEpisode length must be between 1 and 60 minutes");
                        return true;
                    }

                    plugin.getConfigManager().getGameConfig().setEpisodeLength(length);
                    plugin.getConfigManager().saveConfigs();
                    MessageUtil.sendMessage(sender, "&aEpisode length set to " + length + " minutes");
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(sender, "&cInvalid number: " + value);
                }
                break;

            case "arenaplayers":
                try {
                    int players = Integer.parseInt(value);
                    if (players < 2 || players > 16) {
                        MessageUtil.sendMessage(sender, "&cArena player threshold must be between 2 and 16 players");
                        return true;
                    }

                    plugin.getConfigManager().getGameConfig().setArenaPlayerThreshold(players);
                    plugin.getConfigManager().saveConfigs();
                    MessageUtil.sendMessage(sender, "&aArena player threshold set to " + players + " players");
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(sender, "&cInvalid number: " + value);
                }
                break;

            case "minplayers":
                try {
                    int players = Integer.parseInt(value);
                    if (players < 2) {
                        MessageUtil.sendMessage(sender, "&cMinimum players must be at least 2");
                        return true;
                    }

                    plugin.getConfigManager().getGameConfig().setMinimumPlayers(players);
                    plugin.getConfigManager().saveConfigs();
                    MessageUtil.sendMessage(sender, "&aMinimum players set to " + players);
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(sender, "&cInvalid number: " + value);
                }
                break;

            case "randomroles":
                boolean randomRoles = Boolean.parseBoolean(value);
                plugin.getConfigManager().getGameConfig().setRandomRoleAssignment(randomRoles);
                plugin.getConfigManager().saveConfigs();
                MessageUtil.sendMessage(sender, "&aRandom role assignment set to " + randomRoles);
                break;

            case "soloalliances":
                boolean soloAlliances = Boolean.parseBoolean(value);
                plugin.getConfigManager().getGameConfig().setAllowSoloAlliances(soloAlliances);
                plugin.getConfigManager().saveConfigs();
                MessageUtil.sendMessage(sender, "&aSolo alliances set to " + soloAlliances);
                break;

            default:
                MessageUtil.sendMessage(sender, "&cUnknown setting: " + setting);
                break;
        }

        return true;
    }

    /**
     * Handle the 'reload' sub-command
     *
     * @param sender Command sender
     * @return True if handled
     */
    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, "ngnl.admin.reload")) {
            return true;
        }

        if (plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(sender, "&cCannot reload while a game is running!");
            return true;
        }

        plugin.getConfigManager().reloadConfigs();
        MessageUtil.sendMessage(sender, "&aConfiguration reloaded successfully!");

        return true;
    }

    /**
     * Handle the 'setrole' sub-command
     *
     * @param sender Command sender
     * @param args Command arguments
     * @return True if handled
     */
    private boolean handleSetRole(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "ngnl.admin.setrole")) {
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&cUsage: /ngnl setrole <player> <role>");
            return true;
        }

        // Get player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "&cPlayer not found: " + args[1]);
            return true;
        }

        // Get role
        RoleType roleType = RoleType.getByName(args[2].toUpperCase());
        if (roleType == null) {
            MessageUtil.sendMessage(sender, "&cInvalid role: " + args[2]);
            return true;
        }

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(sender, "&cCannot set roles while the game is not running!");
            return true;
        }

        // Assign role
        if (plugin.getRoleManager().assignRoleToPlayer(target.getUniqueId(), roleType)) {
            MessageUtil.sendMessage(sender, "&aAssigned role " + roleType.getDisplayName() + " to " + target.getName());
        } else {
            MessageUtil.sendMessage(sender, "&cFailed to assign role. Role may already be assigned or player may already have a role.");
        }

        return true;
    }

    /**
     * Handle the 'info' sub-command
     *
     * @param sender Command sender
     * @return True if handled
     */
    private boolean handleInfo(CommandSender sender) {
        if (!hasPermission(sender, "ngnl.admin.info")) {
            return true;
        }

        MessageUtil.sendMessage(sender, "&6=== No Game No Life UHC Info ===");
        MessageUtil.sendMessage(sender, "&eVersion: &f" + plugin.getDescription().getVersion());
        MessageUtil.sendMessage(sender, "&eAuthor: &fTheSpatt");

        // Game info
        if (plugin.getGameManager().isGameRunning()) {
            GameState state = plugin.getGameManager().getGameState();
            int alivePlayers = plugin.getGameManager().getGame().getAlivePlayers().size();
            int currentEpisode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();

            MessageUtil.sendMessage(sender, "&eGame State: &f" + state.getDisplayName());
            MessageUtil.sendMessage(sender, "&ePlayers Alive: &f" + alivePlayers);
            MessageUtil.sendMessage(sender, "&eEpisode: &f" + currentEpisode);
        } else {
            MessageUtil.sendMessage(sender, "&eGame State: &fNot running");
        }

        return true;
    }

    /**
     * Send help information to a command sender
     *
     * @param sender Command sender
     */
    private void sendHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, "&6=== No Game No Life UHC Commands ===");
        MessageUtil.sendMessage(sender, "&e/ngnl start &7- Start the game");
        MessageUtil.sendMessage(sender, "&e/ngnl stop &7- Stop the game");
        MessageUtil.sendMessage(sender, "&e/ngnl arena &7- Force start the arena phase");
        MessageUtil.sendMessage(sender, "&e/ngnl roles &7- List all available roles");
        MessageUtil.sendMessage(sender, "&e/ngnl settings &7- Open settings GUI");
        MessageUtil.sendMessage(sender, "&e/ngnl settings <setting> <value> &7- Change a setting");
        MessageUtil.sendMessage(sender, "&e/ngnl reload &7- Reload configuration");
        MessageUtil.sendMessage(sender, "&e/ngnl setrole <player> <role> &7- Set a player's role");
        MessageUtil.sendMessage(sender, "&e/ngnl info &7- Show plugin information");
    }

    /**
     * Check if a sender has a permission
     *
     * @param sender Command sender
     * @param permission Permission to check
     * @return True if sender has permission
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }

        MessageUtil.sendMessage(sender, ChatColor.RED + "You don't have permission to use this command!");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - sub-commands
            String[] subCommands = {"start", "stop", "arena", "roles", "settings", "reload", "setrole", "info"};
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            // Second argument - depends on first argument
            switch (args[0].toLowerCase()) {
                case "settings":
                    String[] settings = {"episodelength", "arenaplayers", "minplayers", "randomroles", "soloalliances"};
                    for (String setting : settings) {
                        if (setting.startsWith(args[1].toLowerCase())) {
                            completions.add(setting);
                        }
                    }
                    break;

                case "setrole":
                    // Player names
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                    break;
            }
        } else if (args.length == 3) {
            // Third argument - depends on first two arguments
            if (args[0].equalsIgnoreCase("setrole")) {
                // Role names
                for (RoleType roleType : RoleType.values()) {
                    if (roleType.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(roleType.name().toLowerCase());
                    }
                }
            } else if (args[0].equalsIgnoreCase("settings")) {
                // Setting values
                switch (args[1].toLowerCase()) {
                    case "randomroles":
                    case "soloalliances":
                        completions.add("true");
                        completions.add("false");
                        break;

                    case "episodelength":
                        completions.add("10");
                        completions.add("15");
                        completions.add("20");
                        completions.add("30");
                        break;

                    case "arenaplayers":
                        completions.add("4");
                        completions.add("6");
                        completions.add("8");
                        completions.add("10");
                        break;

                    case "minplayers":
                        completions.add("2");
                        completions.add("4");
                        completions.add("6");
                        completions.add("8");
                        break;
                }
            }
        }

        return completions;
    }
}