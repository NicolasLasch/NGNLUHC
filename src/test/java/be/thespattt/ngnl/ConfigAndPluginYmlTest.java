package be.thespattt.ngnl;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shipped configuration must carry the real game values (not the test values) and plugin.yml
 * must be valid.
 */
class ConfigAndPluginYmlTest {

    private YamlConfiguration yaml(String file) throws IOException {
        return YamlConfiguration.loadConfiguration(TestFiles.reader(file));
    }

    @Test
    void pluginYmlIsValid() throws IOException {
        YamlConfiguration plugin = yaml("src/main/resources/plugin.yml");
        assertEquals("be.thespattt.ngnl.NoGameNoLife", plugin.getString("main"));
        assertEquals("1.21", plugin.getString("api-version"));
        assertEquals("${version}", plugin.getString("version"), "the version is injected by Gradle");
        assertNull(plugin.get("softdepend"), "no external plugin is required any more");
        for (String command : plugin.getConfigurationSection("commands").getKeys(false)) {
            assertNotNull(plugin.getString("commands." + command + ".description"), command + " needs a description");
        }
    }

    @Test
    void configHasTheRealGameValues() throws IOException {
        YamlConfiguration config = yaml("src/main/resources/config.yml");
        assertEquals(20, config.getInt("game.episode_length"), "episodes last 20 minutes");
        assertEquals(8, config.getInt("game.arena_player_threshold"), "arena starts at 8 players");
        assertTrue(config.getBoolean("world.destroy_worlds_after_game"));
        assertEquals(60, config.getInt("game.aka_si_anse_appear_time"));
        assertEquals("ngnl_arena_city", config.getString("arena.world-name"));
    }

    @Test
    void resourcePackSettingsExist() throws IOException {
        YamlConfiguration config = yaml("src/main/resources/config.yml");
        assertTrue(config.isConfigurationSection("resourcepack"));
        assertTrue(config.isSet("resourcepack.self-host.port"));
        assertTrue(config.isSet("resourcepack.url"));
    }

    @Test
    void everyMiniGameIsEnabledByDefault() throws IOException {
        YamlConfiguration config = yaml("src/main/resources/config.yml");
        for (String game : config.getConfigurationSection("minigames.enabled").getKeys(false)) {
            assertTrue(config.getBoolean("minigames.enabled." + game), game + " should be enabled by default");
        }
    }
}
