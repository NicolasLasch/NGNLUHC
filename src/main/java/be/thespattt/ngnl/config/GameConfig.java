package be.thespattt.ngnl.config;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to handle game configuration settings
 */
public class GameConfig {

    private final NoGameNoLife plugin;
    private File configFile;
    private FileConfiguration config;

    // Game settings
    private int episodeLength;
    private int arenaPlayerThreshold;
    private int minimumPlayers;
    private boolean randomRoleAssignment;
    private boolean allowSoloAlliances;
    private boolean forceEnablePvP;
    private int akaSiAnseAppearTime;

    // World settings
    private int miningWorldBorderSize;
    private int arenaWorldBorderSize;
    private boolean destroyWorldsAfterGame;

    // Mini-game settings
    private Map<String, Boolean> enabledMiniGames;
    private int miniGameDefaultTime;

    private int pvpEnableEpisode;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public GameConfig(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");

        // Initialize map
        this.enabledMiniGames = new HashMap<>();
    }

    /**
     * Load configuration from file
     */
    public void loadConfig() {
        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }

        // Load configuration
        this.config = YamlConfiguration.loadConfiguration(configFile);

        // Load game settings
        loadGameSettings();

        // Load world settings
        loadWorldSettings();

        // Load mini-game settings
        loadMiniGameSettings();

        MessageUtil.logInfo("Configuration loaded successfully");
    }

    /**
     * Save configuration to file
     */
    public void saveConfig() {
        // Save game settings
        saveGameSettings();

        // Save world settings
        saveWorldSettings();

        // Save mini-game settings
        saveMiniGameSettings();

        // Save file
        try {
            config.save(configFile);
            MessageUtil.logInfo("Configuration saved successfully");
        } catch (IOException e) {
            MessageUtil.logError("Failed to save configuration", e);
        }
    }

    /**
     * Load game settings from config
     */
    private void loadGameSettings() {
        episodeLength = config.getInt("game.episode_length", 20);
        arenaPlayerThreshold = config.getInt("game.arena_player_threshold", 8);
        minimumPlayers = config.getInt("game.minimum_players", 4);
        randomRoleAssignment = config.getBoolean("game.random_role_assignment", true);
        allowSoloAlliances = config.getBoolean("game.allow_solo_alliances", true);
        forceEnablePvP = config.getBoolean("game.force_enable_pvp", false);
        akaSiAnseAppearTime = config.getInt("game.aka_si_anse_appear_time", 60);
        pvpEnableEpisode = config.getInt("game.pvp_enable_episode", 2);
    }

    /**
     * Save game settings to config
     */
    private void saveGameSettings() {
        config.set("game.episode_length", episodeLength);
        config.set("game.arena_player_threshold", arenaPlayerThreshold);
        config.set("game.minimum_players", minimumPlayers);
        config.set("game.random_role_assignment", randomRoleAssignment);
        config.set("game.allow_solo_alliances", allowSoloAlliances);
        config.set("game.force_enable_pvp", forceEnablePvP);
        config.set("game.aka_si_anse_appear_time", akaSiAnseAppearTime);
        config.set("game.pvp_enable_episode", pvpEnableEpisode);
    }

    /**
     * Load world settings from config
     */
    private void loadWorldSettings() {
        miningWorldBorderSize = config.getInt("world.mining_world_border_size", 1000);
        arenaWorldBorderSize = config.getInt("world.arena_world_border_size", 300);
        destroyWorldsAfterGame = config.getBoolean("world.destroy_worlds_after_game", false);
    }

    /**
     * Save world settings to config
     */
    private void saveWorldSettings() {
        config.set("world.mining_world_border_size", miningWorldBorderSize);
        config.set("world.arena_world_border_size", arenaWorldBorderSize);
        config.set("world.destroy_worlds_after_game", destroyWorldsAfterGame);
    }

    /**
     * Load mini-game settings from config
     */
    private void loadMiniGameSettings() {
        enabledMiniGames.clear();

        // Default mini-game settings
        List<String> defaultMiniGames = new ArrayList<>();
        defaultMiniGames.add("SPEED_BEDWARS");
        defaultMiniGames.add("SPLEEF");
        defaultMiniGames.add("TNT_RUN");
        defaultMiniGames.add("PARKOUR");
        defaultMiniGames.add("BLOC_PARTY");
        defaultMiniGames.add("SUMO");
        defaultMiniGames.add("SPLEGG");
        defaultMiniGames.add("FLOOR_IS_LAVA");
        defaultMiniGames.add("DES_A_COUDRE");
        defaultMiniGames.add("ANVIL_RAIN");
        defaultMiniGames.add("WORD_CHAIN_BATTLE");
        defaultMiniGames.add("LOGICAL_DEDUCTION");
        defaultMiniGames.add("MENTAL_CHESS");
        defaultMiniGames.add("MEMORY_GAME");
        defaultMiniGames.add("MATERIALIZATION_SHIRITORI");

        // Set default enabled status for all mini-games
        for (String miniGame : defaultMiniGames) {
            boolean enabled = config.getBoolean("minigames.enabled." + miniGame, true);
            enabledMiniGames.put(miniGame, enabled);
        }

        // Load mini-game time
        miniGameDefaultTime = config.getInt("minigames.default_time", 300);
    }

    /**
     * Save mini-game settings to config
     */
    private void saveMiniGameSettings() {
        for (Map.Entry<String, Boolean> entry : enabledMiniGames.entrySet()) {
            config.set("minigames.enabled." + entry.getKey(), entry.getValue());
        }

        config.set("minigames.default_time", miniGameDefaultTime);
    }

    /**
     * Get episode length in minutes
     *
     * @return Episode length
     */
    public int getEpisodeLength() {
        return episodeLength;
    }

    /**
     * Set episode length in minutes
     *
     * @param episodeLength New episode length
     */
    public void setEpisodeLength(int episodeLength) {
        this.episodeLength = episodeLength;
    }

    /**
     * Get arena player threshold
     *
     * @return Number of players remaining to trigger arena phase
     */
    public int getArenaPlayerThreshold() {
        return arenaPlayerThreshold;
    }

    /**
     * Set arena player threshold
     *
     * @param arenaPlayerThreshold New threshold
     */
    public void setArenaPlayerThreshold(int arenaPlayerThreshold) {
        this.arenaPlayerThreshold = arenaPlayerThreshold;
    }

    /**
     * Get minimum number of players required to start the game
     *
     * @return Minimum players
     */
    public int getMinimumPlayers() {
        return minimumPlayers;
    }

    /**
     * Set minimum number of players required to start the game
     *
     * @param minimumPlayers New minimum
     */
    public void setMinimumPlayers(int minimumPlayers) {
        this.minimumPlayers = minimumPlayers;
    }

    /**
     * Check if roles should be assigned randomly
     *
     * @return True if random assignment is enabled
     */
    public boolean isRandomRoleAssignment() {
        return randomRoleAssignment;
    }

    /**
     * Set random role assignment
     *
     * @param randomRoleAssignment New setting
     */
    public void setRandomRoleAssignment(boolean randomRoleAssignment) {
        this.randomRoleAssignment = randomRoleAssignment;
    }

    /**
     * Check if solo roles can form alliances
     *
     * @return True if solo alliances are allowed
     */
    public boolean isAllowSoloAlliances() {
        return allowSoloAlliances;
    }

    /**
     * Set allow solo alliances
     *
     * @param allowSoloAlliances New setting
     */
    public void setAllowSoloAlliances(boolean allowSoloAlliances) {
        this.allowSoloAlliances = allowSoloAlliances;
    }

    /**
     * Check if PvP should be force enabled
     *
     * @return True if PvP should be force enabled
     */
    public boolean isForceEnablePvP() {
        return forceEnablePvP;
    }

    /**
     * Set force enable PvP
     *
     * @param forceEnablePvP New setting
     */
    public void setForceEnablePvP(boolean forceEnablePvP) {
        this.forceEnablePvP = forceEnablePvP;
    }

    /**
     * Get Aka Si Anse appear time in minutes
     *
     * @return Appear time
     */
    public int getAkaSiAnseAppearTime() {
        return akaSiAnseAppearTime;
    }

    /**
     * Set Aka Si Anse appear time
     *
     * @param akaSiAnseAppearTime New time in minutes
     */
    public void setAkaSiAnseAppearTime(int akaSiAnseAppearTime) {
        this.akaSiAnseAppearTime = akaSiAnseAppearTime;
    }

    /**
     * Get mining world border size
     *
     * @return Border size in blocks
     */
    public int getMiningWorldBorderSize() {
        return miningWorldBorderSize;
    }

    /**
     * Set mining world border size
     *
     * @param miningWorldBorderSize New size in blocks
     */
    public void setMiningWorldBorderSize(int miningWorldBorderSize) {
        this.miningWorldBorderSize = miningWorldBorderSize;
    }

    /**
     * Get arena world border size
     *
     * @return Border size in blocks
     */
    public int getArenaWorldBorderSize() {
        return arenaWorldBorderSize;
    }

    /**
     * Set arena world border size
     *
     * @param arenaWorldBorderSize New size in blocks
     */
    public void setArenaWorldBorderSize(int arenaWorldBorderSize) {
        this.arenaWorldBorderSize = arenaWorldBorderSize;
    }

    /**
     * Check if worlds should be destroyed after game
     *
     * @return True if worlds should be destroyed
     */
    public boolean isDestroyWorldsAfterGame() {
        return destroyWorldsAfterGame;
    }

    /**
     * Set destroy worlds after game
     *
     * @param destroyWorldsAfterGame New setting
     */
    public void setDestroyWorldsAfterGame(boolean destroyWorldsAfterGame) {
        this.destroyWorldsAfterGame = destroyWorldsAfterGame;
    }

    /**
     * Check if a mini-game is enabled
     *
     * @param miniGameName Name of the mini-game
     * @return True if enabled
     */
    public boolean isMiniGameEnabled(String miniGameName) {
        return enabledMiniGames.getOrDefault(miniGameName, true);
    }

    /**
     * Set mini-game enabled status
     *
     * @param miniGameName Name of the mini-game
     * @param enabled New status
     */
    public void setMiniGameEnabled(String miniGameName, boolean enabled) {
        enabledMiniGames.put(miniGameName, enabled);
    }

    /**
     * Get default mini-game time limit in seconds
     *
     * @return Time limit
     */
    public int getMiniGameDefaultTime() {
        return miniGameDefaultTime;
    }

    /**
     * Set default mini-game time limit
     *
     * @param miniGameDefaultTime New time limit in seconds
     */
    public void setMiniGameDefaultTime(int miniGameDefaultTime) {
        this.miniGameDefaultTime = miniGameDefaultTime;
    }

    /**
     * Get a list of all enabled mini-games
     *
     * @return List of enabled mini-game names
     */
    public List<String> getEnabledMiniGames() {
        List<String> enabled = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : enabledMiniGames.entrySet()) {
            if (entry.getValue()) {
                enabled.add(entry.getKey());
            }
        }
        return enabled;
    }

    /**
     * Get the episode when PvP is enabled
     *
     * @return PvP enable episode
     */
    public int getPvpEnableEpisode() {
        return pvpEnableEpisode;
    }

    /**
     * Set the episode when PvP is enabled
     *
     * @param pvpEnableEpisode PvP enable episode
     */
    public void setPvpEnableEpisode(int pvpEnableEpisode) {
        this.pvpEnableEpisode = pvpEnableEpisode;
    }
}