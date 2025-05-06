package be.thespattt.ngnl.config;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.faction.FactionType;
import be.thespattt.ngnl.role.RoleType;
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
    protected FileConfiguration config;

    // Game settings
    private int episodeLength;
    private int arenaPlayerThreshold;
    private int minimumPlayers;
    private boolean randomRoleAssignment;
    private boolean allowSoloAlliances;
    private boolean forceEnablePvP;
    private int akaSiAnseAppearTime;
    private int pvpEnableEpisode;

    // Additional game settings for GUI
    private int pvpEnabledTime;
    private boolean naturalRegen;
    private boolean alwaysDay;

    // World settings
    private int miningWorldBorderSize;
    private int arenaWorldBorderSize;
    private boolean destroyWorldsAfterGame;

    // Additional world settings for GUI
    private boolean borderShrinking;

    // Mini-game settings
    private Map<String, Boolean> enabledMiniGames;
    private int miniGameDefaultTime;

    // Additional mini-game settings for GUI
    private double miniGameWinnerHealthLoss;
    private double miniGameLoserHealthLoss;
    private double miniGameRematchChance;
    private boolean miniGameRandomStart;
    private boolean miniGameRerollAllowed;
    private boolean miniGameBookReward;

    // Arena settings
    private int arenaSize;
    private boolean arenaShrinking;
    private int arenaShrinkTime;
    private int arenaFinalSize;
    private boolean specialItemsEnabled;
    private int arenaGracePeriod;
    private double abilityCooldownMultiplier;
    private boolean arenaEventsEnabled;

    // Role settings
    private double roleAbilityStrength;
    private Map<String, Boolean> enabledRoles;

    // Faction settings
    private boolean factionBonusesEnabled;
    private boolean factionRevealEnabled;
    private boolean proximityBonusEnabled;
    private boolean betrayalPenaltyEnabled;
    private Map<String, Boolean> enabledFactions;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public GameConfig(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");

        // Initialize maps
        this.enabledMiniGames = new HashMap<>();
        this.enabledRoles = new HashMap<>();
        this.enabledFactions = new HashMap<>();
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

        // Load original game settings
        loadGameSettings();

        // Load world settings
        loadWorldSettings();

        // Load mini-game settings
        loadMiniGameSettings();

        // Load new GUI-specific settings
        loadGUISettings();

        MessageUtil.logInfo("Configuration loaded successfully");
    }

    /**
     * Save configuration to file
     */
    public void saveConfig() {
        // Save original game settings
        saveGameSettings();

        // Save world settings
        saveWorldSettings();

        // Save mini-game settings
        saveMiniGameSettings();

        // Save new GUI-specific settings
        saveGUISettings();

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
     * Load GUI-specific settings
     */
    private void loadGUISettings() {
        // Load additional game settings
        pvpEnabledTime = config.getInt("game.pvp-enabled-time", 20);
        naturalRegen = config.getBoolean("game.natural-regen", false);
        alwaysDay = config.getBoolean("world.always-day", false);

        // Load additional world settings
        borderShrinking = config.getBoolean("world.border-shrinking", true);

        // Load mini-game GUI settings
        miniGameWinnerHealthLoss = config.getDouble("mini-games.winner-health-loss", 3.5);
        miniGameLoserHealthLoss = config.getDouble("mini-games.loser-health-loss", 5.0);
        miniGameRematchChance = config.getDouble("mini-games.rematch-chance", 0.1);
        miniGameRandomStart = config.getBoolean("mini-games.random-start", true);
        miniGameRerollAllowed = config.getBoolean("mini-games.reroll-allowed", true);
        miniGameBookReward = config.getBoolean("mini-games.book-reward", true);

        // Load arena settings
        arenaSize = config.getInt("arena.size", 300);
        arenaShrinking = config.getBoolean("arena.shrinking", true);
        arenaShrinkTime = config.getInt("arena.shrink-time", 30);
        arenaFinalSize = config.getInt("arena.final-size", 100);
        specialItemsEnabled = config.getBoolean("arena.special-items", true);
        arenaGracePeriod = config.getInt("arena.grace-period", 30);
        abilityCooldownMultiplier = config.getDouble("arena.ability-cooldown-multiplier", 1.0);
        arenaEventsEnabled = config.getBoolean("arena.events-enabled", true);

        // Load role settings
        roleAbilityStrength = config.getDouble("roles.ability-strength", 1.0);

        // Load role enabled states
        enabledRoles.clear();
        for (RoleType roleType : RoleType.values()) {
            boolean enabled = config.getBoolean("roles.enabled." + roleType.name().toLowerCase(), true);
            enabledRoles.put(roleType.name().toLowerCase(), enabled);
        }

        // Load faction settings
        factionBonusesEnabled = config.getBoolean("factions.bonuses-enabled", true);
        factionRevealEnabled = config.getBoolean("factions.reveal-on-death", true);
        proximityBonusEnabled = config.getBoolean("factions.proximity-bonus", true);
        betrayalPenaltyEnabled = config.getBoolean("factions.betrayal-penalty", true);

        // Load faction enabled states
        enabledFactions.clear();
        for (FactionType factionType : FactionType.values()) {
            boolean enabled = config.getBoolean("factions.enabled." + factionType.name().toLowerCase(), true);
            enabledFactions.put(factionType.name().toLowerCase(), enabled);
        }
    }

    /**
     * Save GUI-specific settings
     */
    private void saveGUISettings() {
        // Save additional game settings
        config.set("game.pvp-enabled-time", pvpEnabledTime);
        config.set("game.natural-regen", naturalRegen);
        config.set("world.always-day", alwaysDay);

        // Save additional world settings
        config.set("world.border-shrinking", borderShrinking);

        // Save mini-game GUI settings
        config.set("mini-games.winner-health-loss", miniGameWinnerHealthLoss);
        config.set("mini-games.loser-health-loss", miniGameLoserHealthLoss);
        config.set("mini-games.rematch-chance", miniGameRematchChance);
        config.set("mini-games.random-start", miniGameRandomStart);
        config.set("mini-games.reroll-allowed", miniGameRerollAllowed);
        config.set("mini-games.book-reward", miniGameBookReward);

        // Save arena settings
        config.set("arena.size", arenaSize);
        config.set("arena.shrinking", arenaShrinking);
        config.set("arena.shrink-time", arenaShrinkTime);
        config.set("arena.final-size", arenaFinalSize);
        config.set("arena.special-items", specialItemsEnabled);
        config.set("arena.grace-period", arenaGracePeriod);
        config.set("arena.ability-cooldown-multiplier", abilityCooldownMultiplier);
        config.set("arena.events-enabled", arenaEventsEnabled);

        // Save role settings
        config.set("roles.ability-strength", roleAbilityStrength);

        // Save role enabled states
        for (Map.Entry<String, Boolean> entry : enabledRoles.entrySet()) {
            config.set("roles.enabled." + entry.getKey(), entry.getValue());
        }

        // Save faction settings
        config.set("factions.bonuses-enabled", factionBonusesEnabled);
        config.set("factions.reveal-on-death", factionRevealEnabled);
        config.set("factions.proximity-bonus", proximityBonusEnabled);
        config.set("factions.betrayal-penalty", betrayalPenaltyEnabled);

        // Save faction enabled states
        for (Map.Entry<String, Boolean> entry : enabledFactions.entrySet()) {
            config.set("factions.enabled." + entry.getKey(), entry.getValue());
        }
    }

    // Original getters and setters

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

    // New methods for GUI configuration

    /**
     * Get qualification player threshold
     * (Same as arena player threshold)
     */
    public int getQualificationPlayers() {
        return arenaPlayerThreshold;
    }

    /**
     * Set qualification player threshold
     * (Same as arena player threshold)
     */
    public void setQualificationPlayers(int players) {
        this.arenaPlayerThreshold = players;
    }

    /**
     * Get PvP enabled time in minutes
     */
    public int getPvpEnabledTime() {
        return pvpEnabledTime;
    }

    /**
     * Set PvP enabled time
     */
    public void setPvpEnabledTime(int time) {
        this.pvpEnabledTime = time;
    }

    /**
     * Get initial border size
     * (Same as mining world border size)
     */
    public int getInitialBorderSize() {
        return miningWorldBorderSize;
    }

    /**
     * Set initial border size
     * (Same as mining world border size)
     */
    public void setInitialBorderSize(int size) {
        this.miningWorldBorderSize = size;
    }

    /**
     * Check if border shrinking is enabled
     */
    public boolean isBorderShrinking() {
        return borderShrinking;
    }

    /**
     * Set border shrinking
     */
    public void setBorderShrinking(boolean shrinking) {
        this.borderShrinking = shrinking;
    }

    /**
     * Check if always day is enabled
     */
    public boolean isAlwaysDay() {
        return alwaysDay;
    }

    /**
     * Set always day
     */
    public void setAlwaysDay(boolean alwaysDay) {
        this.alwaysDay = alwaysDay;
    }

    /**
     * Check if natural regeneration is enabled
     */
    public boolean isNaturalRegenEnabled() {
        return naturalRegen;
    }

    /**
     * Set natural regeneration
     */
    public void setNaturalRegenEnabled(boolean enabled) {
        this.naturalRegen = enabled;
    }

    /**
     * Get role ability strength multiplier
     */
    public double getRoleAbilityStrength() {
        return roleAbilityStrength;
    }

    /**
     * Set role ability strength multiplier
     */
    public void setRoleAbilityStrength(double strength) {
        this.roleAbilityStrength = strength;
    }

    /**
     * Check if a role is enabled
     */
    public boolean isRoleEnabled(RoleType roleType) {
        return enabledRoles.getOrDefault(roleType.name().toLowerCase(), true);
    }

    /**
     * Set role enabled status
     */
    public void setRoleEnabled(RoleType roleType, boolean enabled) {
        enabledRoles.put(roleType.name().toLowerCase(), enabled);
    }

    /**
     * Get mini-game winner health loss
     */
    public double getMiniGameWinnerHealthLoss() {
        return miniGameWinnerHealthLoss;
    }

    /**
     * Set mini-game winner health loss
     */
    public void setMiniGameWinnerHealthLoss(double hearts) {
        this.miniGameWinnerHealthLoss = hearts;
    }

    /**
     * Get mini-game loser health loss
     */
    public double getMiniGameLoserHealthLoss() {
        return miniGameLoserHealthLoss;
    }

    /**
     * Set mini-game loser health loss
     */
    public void setMiniGameLoserHealthLoss(double hearts) {
        this.miniGameLoserHealthLoss = hearts;
    }

    /**
     * Get mini-game rematch chance
     */
    public double getMiniGameRematchChance() {
        return miniGameRematchChance;
    }

    /**
     * Set mini-game rematch chance
     */
    public void setMiniGameRematchChance(double chance) {
        this.miniGameRematchChance = chance;
    }

    /**
     * Check if mini-game random start is enabled
     */
    public boolean isMiniGameRandomStart() {
        return miniGameRandomStart;
    }

    /**
     * Set mini-game random start
     */
    public void setMiniGameRandomStart(boolean random) {
        this.miniGameRandomStart = random;
    }

    /**
     * Check if mini-game reroll is allowed
     */
    public boolean isMiniGameRerollAllowed() {
        return miniGameRerollAllowed;
    }

    /**
     * Set mini-game reroll allowed
     */
    public void setMiniGameRerollAllowed(boolean allowed) {
        this.miniGameRerollAllowed = allowed;
    }

    /**
     * Check if mini-game book reward is enabled
     */
    public boolean isMiniGameBookReward() {
        return miniGameBookReward;
    }

    /**
     * Set mini-game book reward
     */
    public void setMiniGameBookReward(boolean reward) {
        this.miniGameBookReward = reward;
    }

    /**
     * Check if a mini-game is enabled (using enum)
     */
    public boolean isMiniGameEnabled(MiniGameType gameType) {
        return enabledMiniGames.getOrDefault(gameType.name(), true);
    }

    /**
     * Set mini-game enabled status (using enum)
     */
    public void setMiniGameEnabled(MiniGameType gameType, boolean enabled) {
        enabledMiniGames.put(gameType.name(), enabled);
    }

    /**
     * Get arena size
     */
    public int getArenaSize() {
        return arenaSize;
    }

    /**
     * Set arena size
     */
    public void setArenaSize(int size) {
        this.arenaSize = size;
    }

    /**
     * Check if arena shrinking is enabled
     */
    public boolean isArenaShrinking() {
        return arenaShrinking;
    }

    /**
     * Set arena shrinking
     */
    public void setArenaShrinking(boolean shrinking) {
        this.arenaShrinking = shrinking;
    }

    /**
     * Get arena shrink time
     */
    public int getArenaShrinkTime() {
        return arenaShrinkTime;
    }

    /**
     * Set arena shrink time
     */
    public void setArenaShrinkTime(int minutes) {
        this.arenaShrinkTime = minutes;
    }

    /**
     * Get arena final size
     */
    public int getArenaFinalSize() {
        return arenaFinalSize;
    }

    /**
     * Set arena final size
     */
    public void setArenaFinalSize(int size) {
        this.arenaFinalSize = size;
    }

    /**
     * Check if special items are enabled
     */
    public boolean areSpecialItemsEnabled() {
        return specialItemsEnabled;
    }

    /**
     * Set special items enabled
     */
    public void setSpecialItemsEnabled(boolean enabled) {
        this.specialItemsEnabled = enabled;
    }

    /**
     * Get arena grace period
     */
    public int getArenaGracePeriod() {
        return arenaGracePeriod;
    }

    /**
     * Set arena grace period
     */
    public void setArenaGracePeriod(int seconds) {
        this.arenaGracePeriod = seconds;
    }

    /**
     * Get ability cooldown multiplier
     */
    public double getAbilityCooldownMultiplier() {
        return abilityCooldownMultiplier;
    }

    /**
     * Set ability cooldown multiplier
     */
    public void setAbilityCooldownMultiplier(double multiplier) {
        this.abilityCooldownMultiplier = multiplier;
    }

    /**
     * Check if arena events are enabled
     */
    public boolean areArenaEventsEnabled() {
        return arenaEventsEnabled;
    }

    /**
     * Set arena events enabled
     */
    public void setArenaEventsEnabled(boolean enabled) {
        this.arenaEventsEnabled = enabled;
    }

    /**
     * Check if faction bonuses are enabled
     */
    public boolean areFactionBonusesEnabled() {
        return factionBonusesEnabled;
    }

    /**
     * Set faction bonuses enabled
     */
    public void setFactionBonusesEnabled(boolean enabled) {
        this.factionBonusesEnabled = enabled;
    }

    /**
     * Check if faction reveal is enabled
     */
    public boolean isFactionRevealEnabled() {
        return factionRevealEnabled;
    }

    /**
     * Set faction reveal enabled
     */
    public void setFactionRevealEnabled(boolean enabled) {
        this.factionRevealEnabled = enabled;
    }

    /**
     * Check if proximity bonus is enabled
     */
    public boolean isProximityBonusEnabled() {
        return proximityBonusEnabled;
    }

    /**
     * Set proximity bonus enabled
     */
    public void setProximityBonusEnabled(boolean enabled) {
        this.proximityBonusEnabled = enabled;
    }

    /**
     * Check if betrayal penalty is enabled
     */
    public boolean isBetrayalPenaltyEnabled() {
        return betrayalPenaltyEnabled;
    }

    /**
     * Set betrayal penalty enabled
     */
    public void setBetrayalPenaltyEnabled(boolean enabled) {
        this.betrayalPenaltyEnabled = enabled;
    }

    /**
     * Check if a faction is enabled
     */
    public boolean isFactionEnabled(FactionType factionType) {
        return enabledFactions.getOrDefault(factionType.name().toLowerCase(), true);
    }

    /**
     * Set faction enabled status
     */
    public void setFactionEnabled(FactionType factionType, boolean enabled) {
        enabledFactions.put(factionType.name().toLowerCase(), enabled);
    }
}