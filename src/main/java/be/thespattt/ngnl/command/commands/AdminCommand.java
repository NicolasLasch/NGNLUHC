package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for admin operations
 */
public class AdminCommand implements CommandExecutor {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public AdminCommand(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Check permission
        if (!sender.hasPermission("ngnl.admin")) {
            MessageUtil.sendMessage(sender, "&cYou don't have permission to use this command!");
            return true;
        }

        // Check argument count
        if (args.length < 1) {
            sendHelpMessage(sender);
            return true;
        }

        // Handle subcommands
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                handleStart(sender);
                break;

            case "end":
                handleEnd(sender);
                break;

            case "startarena":
                handleStartArena(sender);
                break;

            case "config":
                handleConfig(sender, args);
                break;

            case "reload":
                handleReload(sender);
                break;

            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    /**
     * Send help message
     *
     * @param sender Command sender
     */
    private void sendHelpMessage(CommandSender sender) {
        MessageUtil.sendMessage(sender, "&6===== No Game No Life Admin Commands =====");
        MessageUtil.sendMessageNoPrefix(sender, "&e/ngnladmin start &7- Start the game");
        MessageUtil.sendMessageNoPrefix(sender, "&e/ngnladmin end &7- End the game");
        MessageUtil.sendMessageNoPrefix(sender, "&e/ngnladmin startarena &7- Start the arena phase");
        MessageUtil.sendMessageNoPrefix(sender, "&e/ngnladmin config <set/get> <setting> [value] &7- Configure the game");
        MessageUtil.sendMessageNoPrefix(sender, "&e/ngnladmin reload &7- Reload the configuration");
        MessageUtil.sendMessageNoPrefix(sender, "&6=========================================");
    }

    /**
     * Handle start command
     *
     * @param sender Command sender
     */
    private void handleStart(CommandSender sender) {
        if (plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(sender, "&cThe game is already running!");
            return;
        }

        if (plugin.getGameManager().startGame()) {
            MessageUtil.sendMessage(sender, "&aGame started!");
        } else {
            MessageUtil.sendMessage(sender, "&cFailed to start the game. Check the console for errors.");
        }
    }

    /**
     * Handle end command
     *
     * @param sender Command sender
     */
    private void handleEnd(CommandSender sender) {
        if (!plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(sender, "&cThe game is not running!");
            return;
        }

        plugin.getGameManager().endGame(true);
        MessageUtil.sendMessage(sender, "&aGame ended!");
    }

    /**
     * Handle startarena command
     *
     * @param sender Command sender
     */
    private void handleStartArena(CommandSender sender) {
        if (!plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(sender, "&cThe game is not running!");
            return;
        }

        if (plugin.getGameManager().getGameState() != be.thespattt.ngnl.game.GameState.MINING_PHASE) {
            MessageUtil.sendMessage(sender, "&cThe game is not in the mining phase!");
            return;
        }

        if (plugin.getGameManager().forceArenaPhase()) {
            MessageUtil.sendMessage(sender, "&aArena phase started!");
        } else {
            MessageUtil.sendMessage(sender, "&cFailed to start arena phase.");
        }
    }

    /**
     * Handle config command
     *
     * @param sender Command sender
     * @param args Command arguments
     */
    private void handleConfig(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "&cUsage: /ngnladmin config <set/get> <setting> [value]");
            return;
        }

        String operation = args[1].toLowerCase();

        if (operation.equals("get")) {
            if (args.length < 3) {
                MessageUtil.sendMessage(sender, "&cUsage: /ngnladmin config get <setting>");
                return;
            }

            handleConfigGet(sender, args[2]);
        } else if (operation.equals("set")) {
            if (args.length < 4) {
                MessageUtil.sendMessage(sender, "&cUsage: /ngnladmin config set <setting> <value>");
                return;
            }

            handleConfigSet(sender, args[2], args[3]);
        } else {
            MessageUtil.sendMessage(sender, "&cUnknown operation: " + operation);
        }
    }

    /**
     * Handle config get operation
     *
     * @param sender Command sender
     * @param setting Setting name
     */
    private void handleConfigGet(CommandSender sender, String setting) {
        switch (setting.toLowerCase()) {
            case "episodelength":
                MessageUtil.sendMessage(sender, "&aEpisode length: &e" + plugin.getConfigManager().getGameConfig().getEpisodeLength() + " minutes");
                break;

            case "arenathreshold":
                MessageUtil.sendMessage(sender, "&aArena player threshold: &e" + plugin.getConfigManager().getGameConfig().getArenaPlayerThreshold() + " players");
                break;

            case "minimumplayers":
                MessageUtil.sendMessage(sender, "&aMinimum players: &e" + plugin.getConfigManager().getGameConfig().getMinimumPlayers());
                break;

            case "randomroles":
                MessageUtil.sendMessage(sender, "&aRandom role assignment: &e" + plugin.getConfigManager().getGameConfig().isRandomRoleAssignment());
                break;

            case "allowalliances":
                MessageUtil.sendMessage(sender, "&aAllow solo alliances: &e" + plugin.getConfigManager().getGameConfig().isAllowSoloAlliances());
                break;

            case "forcepvp":
                MessageUtil.sendMessage(sender, "&aForce enable PvP: &e" + plugin.getConfigManager().getGameConfig().isForceEnablePvP());
                break;

            case "pvpepisode":
                MessageUtil.sendMessage(sender, "&aPvP enable episode: &e" + plugin.getConfigManager().getGameConfig().getPvpEnableEpisode());
                break;

            case "akasianseappeartime":
                MessageUtil.sendMessage(sender, "&aAka Si Anse appear time: &e" + plugin.getConfigManager().getGameConfig().getAkaSiAnseAppearTime() + " minutes");
                break;

            case "miningbordersize":
                MessageUtil.sendMessage(sender, "&aMining world border size: &e" + plugin.getConfigManager().getGameConfig().getMiningWorldBorderSize() + " blocks");
                break;

            case "arenabordersize":
                MessageUtil.sendMessage(sender, "&aArena world border size: &e" + plugin.getConfigManager().getGameConfig().getArenaWorldBorderSize() + " blocks");
                break;

            case "destroyworlds":
                MessageUtil.sendMessage(sender, "&aDestroy worlds after game: &e" + plugin.getConfigManager().getGameConfig().isDestroyWorldsAfterGame());
                break;

            default:
                MessageUtil.sendMessage(sender, "&cUnknown setting: " + setting);
                break;
        }
    }

    /**
     * Handle config set operation
     *
     * @param sender Command sender
     * @param setting Setting name
     * @param value Setting value
     */
    private void handleConfigSet(CommandSender sender, String setting, String value) {
        try {
            switch (setting.toLowerCase()) {
                case "episodelength":
                    int episodeLength = Integer.parseInt(value);
                    if (episodeLength < 1) {
                        MessageUtil.sendMessage(sender, "&cEpisode length must be at least 1 minute.");
                        return;
                    }
                    plugin.getConfigManager().getGameConfig().setEpisodeLength(episodeLength);
                    MessageUtil.sendMessage(sender, "&aEpisode length set to &e" + episodeLength + " minutes");
                    break;

                case "arenathreshold":
                    int arenaThreshold = Integer.parseInt(value);
                    if (arenaThreshold < 2) {
                        MessageUtil.sendMessage(sender, "&cArena player threshold must be at least 2 players.");
                        return;
                    }
                    plugin.getConfigManager().getGameConfig().setArenaPlayerThreshold(arenaThreshold);
                    MessageUtil.sendMessage(sender, "&aArena player threshold set to &e" + arenaThreshold + " players");
                    break;

                case "minimumplayers":
                    int minimumPlayers = Integer.parseInt(value);
                    if (minimumPlayers < 2) {
                        MessageUtil.sendMessage(sender, "&cMinimum players must be at least 2.");
                        return;
                    }
                    plugin.getConfigManager().getGameConfig().setMinimumPlayers(minimumPlayers);
                    MessageUtil.sendMessage(sender, "&aMinimum players set to &e" + minimumPlayers);
                    break;

                case "randomroles":
                    boolean randomRoles = Boolean.parseBoolean(value);
                    plugin.getConfigManager().getGameConfig().setRandomRoleAssignment(randomRoles);
                    MessageUtil.sendMessage(sender, "&aRandom role assignment set to &e" + randomRoles);
                    break;

                case "allowalliances":
                    boolean allowAlliances = Boolean.parseBoolean(value);
                    plugin.getConfigManager().getGameConfig().setAllowSoloAlliances(allowAlliances);
                    MessageUtil.sendMessage(sender, "&aAllow solo alliances set to &e" + allowAlliances);
                    break;

                case "forcepvp":
                    boolean forcePvP = Boolean.parseBoolean(value);
                    plugin.getConfigManager().getGameConfig().setForceEnablePvP(forcePvP);
                    MessageUtil.sendMessage(sender, "&aForce enable PvP set to &e" + forcePvP);
                    break;

                case "pvpepisode":
                    int pvpEpisode = Integer.parseInt(value);
                    if (pvpEpisode < 1) {
                        MessageUtil.sendMessage(sender, "&cPvP enable episode must be at least 1.");
                        return;
                    }
                    plugin.getConfigManager().getGameConfig().setPvpEnableEpisode(pvpEpisode);
                    MessageUtil.sendMessage(sender, "&aPvP enable episode set to &e" + pvpEpisode);
                    break;

                case "akasianseappeartime":
                    int akaSiAnseAppearTime = Integer.parseInt(value);
                    if (akaSiAnseAppearTime < 1) {
                        MessageUtil.sendMessage(sender, "&cAka Si Anse appear time must be at least 1 minute.");
                        return;
                    }
                    plugin.getConfigManager().getGameConfig().setAkaSiAnseAppearTime(akaSiAnseAppearTime);
                    MessageUtil.sendMessage(sender, "&aAka Si Anse appear time set to &e" + akaSiAnseAppearTime + " minutes");
                    break;

                case "miningbordersize":
                    int miningBorderSize = Integer.parseInt(value);
                    if (miningBorderSize < 100) {
                        MessageUtil.sendMessage(sender, "&cMining world border size must be at least 100 blocks.");
                        return;
                    }
                    plugin.getConfigManager().getGameConfig().setMiningWorldBorderSize(miningBorderSize);
                    MessageUtil.sendMessage(sender, "&aMining world border size set to &e" + miningBorderSize + " blocks");
                    break;

                case "arenabordersize":
                    int arenaBorderSize = Integer.parseInt(value);
                    if (arenaBorderSize < 50) {
                        MessageUtil.sendMessage(sender, "&cArena world border size must be at least 50 blocks.");
                        return;
                    }
                    plugin.getConfigManager().getGameConfig().setArenaWorldBorderSize(arenaBorderSize);
                    MessageUtil.sendMessage(sender, "&aArena world border size set to &e" + arenaBorderSize + " blocks");
                    break;

                case "destroyworlds":
                    boolean destroyWorlds = Boolean.parseBoolean(value);
                    plugin.getConfigManager().getGameConfig().setDestroyWorldsAfterGame(destroyWorlds);
                    MessageUtil.sendMessage(sender, "&aDestroy worlds after game set to &e" + destroyWorlds);
                    break;

                default:
                    MessageUtil.sendMessage(sender, "&cUnknown setting: " + setting);
                    break;
            }

            // Save config
            plugin.getConfigManager().saveConfigs();
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&cInvalid value format. Expected a number.");
        }
    }

    /**
     * Handle reload command
     *
     * @param sender Command sender
     */
    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reloadConfigs();
        MessageUtil.sendMessage(sender, "&aConfiguration reloaded!");
    }
}