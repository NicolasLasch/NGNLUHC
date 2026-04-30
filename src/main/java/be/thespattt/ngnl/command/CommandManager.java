package be.thespattt.ngnl.command;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.command.commands.*;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.games.ChessMiniGame;
import be.thespattt.ngnl.util.MessageUtil;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

/**
 * Manager class for handling all commands in the plugin
 */
public class CommandManager {

    private final NoGameNoLife plugin;

    // Store specific commands for access from other parts of the plugin
    private PledgeCommand pledgeCommand;
    private AllianceCommand allianceCommand;
    private DuoCommand duoCommand;
    private RockPaperScissorsCommand rpsCommand;

    private AdminCommand adminCommand;
    private MiniGameCommand miniGameCommand;
    private ForceKillCommand forceKillCommand;
    private HealCommand healCommand;
    private RiteCommand riteCommand;
    private ForestCommand forestCommand;
    private TeleportCommand teleportCommand;

    private RoleCommand roleCommand;
    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public CommandManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all commands
     */
    public void registerCommands() {
        // Initialize command instances
        NGNLCommand ngnlCommand = new NGNLCommand(plugin);
        ChallengeCommand challengeCommand = new ChallengeCommand(plugin);
        this.duoCommand = new DuoCommand(plugin);
        this.allianceCommand = new AllianceCommand(plugin);
        this.pledgeCommand = new PledgeCommand(plugin);
        this.adminCommand = new AdminCommand(plugin);
        this.healCommand = new HealCommand(plugin);
        this.riteCommand = new RiteCommand(plugin);
        this.forestCommand = new ForestCommand(plugin);
        this.teleportCommand = new TeleportCommand(plugin);
        this.rpsCommand = new RockPaperScissorsCommand(plugin);
        this.miniGameCommand = new MiniGameCommand(plugin);
        this.forceKillCommand = new ForceKillCommand(plugin);
        this.roleCommand = new RoleCommand(plugin);
        // Register main command
        registerCommand("role", roleCommand);
        registerCommand("ngnl", ngnlCommand);
        registerCommand("ngnladmin", adminCommand);
        registerCommand("forcekill", forceKillCommand);
        // Register game-related commands
        registerCommand("challenge", challengeCommand);
        registerCommand("duo", duoCommand);
        registerCommand("alliance", allianceCommand);
        registerCommand("pledge", pledgeCommand);
        registerCommand("minigame", miniGameCommand);
        registerCommand("ngnlconfig", new be.thespattt.ngnl.gui.ConfigCommand(plugin));
        registerCommand("chess", new ChessCommand(plugin));
        // Exemple d'exécution de la commande /chess

        // Register role-specific commands
        registerCommand("heal", healCommand);
        registerCommand("rite", riteCommand);
        registerCommand("forest", forestCommand);
        registerCommand("teleport", teleportCommand);
        registerCommand("substitute", new SubstituteCommand(plugin));
        registerCommand("acceptsub", new AcceptSubCommand(plugin));
        registerCommand("bonus", new BonusCommand(plugin));

        // Register utility commands
        registerCommand("rps", rpsCommand);

        MessageUtil.logInfo("All commands registered");
    }

    /**
     * Register a command with its executor and tab completer
     *
     * @param commandName Name of the command
     * @param executor Command executor
     */
    private void registerCommand(String commandName, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(commandName);

        if (command == null) {
            MessageUtil.logWarning("Failed to register command: " + commandName + " (not found in plugin.yml)");
            return;
        }

        command.setExecutor(executor);

        // Register tab completer if executor implements TabCompleter
        if (executor instanceof TabCompleter) {
            command.setTabCompleter((TabCompleter) executor);
        }
    }

    /**
     * Get the pledge command
     *
     * @return PledgeCommand instance
     */
    public PledgeCommand getPledgeCommand() {
        return pledgeCommand;
    }

    /**
     * Get the alliance command
     *
     * @return AllianceCommand instance
     */
    public AllianceCommand getAllianceCommand() {
        return allianceCommand;
    }

    /**
     * Get the duo command
     *
     * @return DuoCommand instance
     */
    public DuoCommand getDuoCommand() {
        return duoCommand;
    }

    /**
     * Get the rock-paper-scissors command
     *
     * @return RockPaperScissorsCommand instance
     */
    public RockPaperScissorsCommand getRpsCommand() {
        return rpsCommand;
    }

    /**
     * Get the admin command
     *
     * @return AdminCommand instance
     */
    public AdminCommand getAdminCommand() {
        return adminCommand;
    }

    public MiniGameCommand getMiniGameCommand() {
        return miniGameCommand;
    }
}
