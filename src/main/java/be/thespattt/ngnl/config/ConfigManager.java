package be.thespattt.ngnl.config;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import java.io.File;

/**
 * Manager class for plugin configuration
 */
public class ConfigManager {

    private final NoGameNoLife plugin;
    private GameConfig gameConfig;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public ConfigManager(NoGameNoLife plugin) {
        this.plugin = plugin;

        // Create plugin directory if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Initialize configurations
        this.gameConfig = new GameConfig(plugin);
    }

    /**
     * Load all configurations
     */
    public void loadConfigs() {
        // Load main game configuration
        gameConfig.loadConfig();

        // Load other configurations as needed
        loadLocationConfig();
        loadRoleConfig();
        loadMessageConfig();

        MessageUtil.logInfo("All configurations loaded");
    }

    /**
     * Save all configurations
     */
    public void saveConfigs() {
        // Save main game configuration
        gameConfig.saveConfig();

        // Save other configurations as needed
        saveLocationConfig();
        saveRoleConfig();
        saveMessageConfig();

        MessageUtil.logInfo("All configurations saved");
    }

    /**
     * Load location configuration
     */
    private void loadLocationConfig() {
        // This would load a configuration file with spawn locations, etc.
        // For now, it's a placeholder for future implementation
    }

    /**
     * Save location configuration
     */
    private void saveLocationConfig() {
        // This would save a configuration file with spawn locations, etc.
        // For now, it's a placeholder for future implementation
    }

    /**
     * Load role configuration
     */
    private void loadRoleConfig() {
        // This would load a configuration file with role settings
        // For now, it's a placeholder for future implementation
    }

    /**
     * Save role configuration
     */
    private void saveRoleConfig() {
        // This would save a configuration file with role settings
        // For now, it's a placeholder for future implementation
    }

    /**
     * Load message configuration
     */
    private void loadMessageConfig() {
        // This would load a configuration file with customizable messages
        // For now, it's a placeholder for future implementation
    }

    /**
     * Save message configuration
     */
    private void saveMessageConfig() {
        // This would save a configuration file with customizable messages
        // For now, it's a placeholder for future implementation
    }

    /**
     * Create default config files if they don't exist
     */
    public void createDefaultConfigs() {
        // Save default config.yml
        if (!new File(plugin.getDataFolder(), "config.yml").exists()) {
            plugin.saveResource("config.yml", false);
        }

        // Save other default configurations as needed
        createDefaultFile("locations.yml");
        createDefaultFile("roles.yml");
        createDefaultFile("messages.yml");

        MessageUtil.logInfo("Default configuration files created");
    }

    /**
     * Create a default configuration file if it doesn't exist
     *
     * @param fileName Name of the file
     */
    private void createDefaultFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                if (plugin.getResource(fileName) != null) {
                    plugin.saveResource(fileName, false);
                } else {
                    file.createNewFile();
                }
            } catch (Exception e) {
                MessageUtil.logError("Failed to create default file: " + fileName, e);
            }
        }
    }

    /**
     * Get the game configuration
     *
     * @return GameConfig instance
     */
    public GameConfig getGameConfig() {
        return gameConfig;
    }

    /**
     * Reload all configurations
     */
    public void reloadConfigs() {
        loadConfigs();
        MessageUtil.logInfo("All configurations reloaded");
    }

    /**
     * Reload configuration
     * This method is used by the GUI system to reload just the game config
     */
    public void reloadConfig() {
        gameConfig.loadConfig();
        MessageUtil.logInfo("Game configuration reloaded");
    }

    /**
     * Save configuration
     * This method is used by the GUI system to save just the game config
     */
    public void saveConfig() {
        gameConfig.saveConfig();
        MessageUtil.logInfo("Game configuration saved");
    }
}