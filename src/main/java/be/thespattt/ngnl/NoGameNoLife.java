package be.thespattt.ngnl;

import be.thespattt.ngnl.command.CommandManager;
import be.thespattt.ngnl.config.ConfigManager;
import be.thespattt.ngnl.event.listener.CombatTracker;
import be.thespattt.ngnl.event.listener.GameListener;
import be.thespattt.ngnl.event.listener.PlayerListener;
import be.thespattt.ngnl.event.listener.MiniGameListener;
import be.thespattt.ngnl.game.GameManager;
import be.thespattt.ngnl.game.world.WorldManager;
import be.thespattt.ngnl.item.ItemManager;
import be.thespattt.ngnl.minigame.*;
import be.thespattt.ngnl.player.PlayerManager;
import be.thespattt.ngnl.player.faction.FactionManager;
import be.thespattt.ngnl.role.RoleManager;
import be.thespattt.ngnl.command.commands.PledgeCommand;
import be.thespattt.ngnl.util.AdvancedCloneManager;
import be.thespattt.ngnl.util.MessageUtil;
import be.thespattt.ngnl.gui.ConfigGUIManager;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for No Game No Life UHC
 * Inspired by the anime and movie "No Game No Life"
 *
 * @author TheSpatt
 */
public class NoGameNoLife extends JavaPlugin {

    private static NoGameNoLife instance;

    // Managers
    private ConfigManager configManager;
    private GameManager gameManager;
    private PlayerManager playerManager;
    private RoleManager roleManager;
    private FactionManager factionManager;
    private MiniGameManager miniGameManager;
    private ItemManager itemManager;
    private WorldManager worldManager;
    private CommandManager commandManager;
    private CombatTracker combatTracker;
    private MiniGameSessionManager miniGameSessionManager;
    private ConfigGUIManager configGUIManager;
    private AdvancedCloneManager cloneManager;
    private MiniGameSelectionManager miniGameSelectionManager;
    private MiniGameStatsTracker miniGameStatsTracker;


    // Register commands
    private MiniGameEngine miniGameEngine;
    private NamespacedKey namespacedKey;


    @Override
    public void onEnable() {
        instance = this;

        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.worldManager = new WorldManager(this);
        this.playerManager = new PlayerManager(this);
        this.factionManager = new FactionManager(this);
        this.roleManager = new RoleManager(this);
        this.miniGameManager = new MiniGameManager(this);
        this.itemManager = new ItemManager(this);
        this.gameManager = new GameManager(this);
        this.commandManager = new CommandManager(this);
        this.combatTracker =  new CombatTracker();
        this.miniGameSessionManager = new MiniGameSessionManager();
        this.configGUIManager = new ConfigGUIManager(this);
        this.namespacedKey = new NamespacedKey(this, "ngnl");
        this.miniGameSessionManager = new MiniGameSessionManager();
        this.miniGameEngine = new MiniGameEngine(this);
        this.cloneManager = new AdvancedCloneManager(this);
        this.miniGameSelectionManager = new MiniGameSelectionManager(this);
        this.miniGameStatsTracker = new MiniGameStatsTracker(this);



        // Register event listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MiniGameListener(this), this);

        // Register commands
        commandManager.registerCommands();

        // Load configurations
        configManager.loadConfigs();

        // Initialize systems
        initializeSystems();

        MessageUtil.logInfo("No Game No Life UHC has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save data and clean up
        if (gameManager != null && gameManager.isGameRunning()) {
            gameManager.endGame(true);
        }

        // Save configurations
        if (configManager != null) {
            configManager.saveConfigs();
        }

        // Clean up worlds if needed
        if (worldManager != null) {
            worldManager.cleanup();
        }

        if (miniGameEngine != null) {
            miniGameEngine.cleanup();
        }

        if (cloneManager != null) {
            cloneManager.cleanup();
        }

        MessageUtil.logInfo("No Game No Life UHC has been disabled!");
    }

    /**
     * Initialize all plugin systems
     */
    private void initializeSystems() {
        // Load factions
        factionManager.loadFactions();

        // Load roles
        roleManager.loadRoles();

        // Load mini-games
        miniGameManager.loadMiniGames();

        // Load special items
        itemManager.loadItems();

        // Initialize worlds if needed
        worldManager.initializeWorlds();
    }

    /**
     * Get the plugin instance
     *
     * @return Plugin instance
     */
    public static NoGameNoLife getInstance() {
        return instance;
    }

    /**
     * Get the config manager
     *
     * @return ConfigManager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Get the game manager
     *
     * @return GameManager instance
     */
    public GameManager getGameManager() {
        return gameManager;
    }

    /**
     * Get the player manager
     *
     * @return PlayerManager instance
     */
    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * Get the role manager
     *
     * @return RoleManager instance
     */
    public RoleManager getRoleManager() {
        return roleManager;
    }

    /**
     * Get the faction manager
     *
     * @return FactionManager instance
     */
    public FactionManager getFactionManager() {
        return factionManager;
    }

    /**
     * Get the mini-game manager
     *
     * @return MiniGameManager instance
     */
    public MiniGameManager getMiniGameManager() {
        return miniGameManager;
    }

    /**
     * Get the item manager
     *
     * @return ItemManager instance
     */
    public ItemManager getItemManager() {
        return itemManager;
    }

    /**
     * Get the world manager
     *
     * @return WorldManager instance
     */
    public WorldManager getWorldManager() {
        return worldManager;
    }

    /**
     * Get the command manager
     *
     * @return CommandManager instance
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

    /**
     * Create a NamespacedKey for this plugin
     *
     * @param key Key name
     * @return NamespacedKey instance
     */
    public NamespacedKey getNamespacedKey(String key) {
        return new NamespacedKey(this, key);
    }

    public CombatTracker getCombatTracker() {
        return combatTracker;
    }

    public MiniGameSessionManager getMiniGameSessionManager() {
        return miniGameSessionManager;
    }
    /**
     * Get the config GUI manager
     *
     * @return Config GUI manager
     */
    public ConfigGUIManager getConfigGUIManager() {
        return configGUIManager;
    }

    public MiniGameEngine getMiniGameEngine() {
        return miniGameEngine;
    }

    public AdvancedCloneManager getCloneManager() {
        return cloneManager;
    }

    public MiniGameSelectionManager getMiniGameSelectionManager() {
        return miniGameSelectionManager;
    }

    public MiniGameStatsTracker getMiniGameStatsTracker() {
        return miniGameStatsTracker;
    }
}