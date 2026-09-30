package be.thespattt.ngnl;

import be.thespattt.ngnl.role.RoleType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Static checks on the sources: every role can be instantiated, every command is declared, and
 * nothing depends on a library that is not shipped.
 */
class SourceConsistencyTest {

    @Test
    void roleManagerCreatesEveryRole() throws IOException {
        String source = TestFiles.read("src/main/java/be/thespattt/ngnl/role/RoleManager.java");
        for (RoleType role : RoleType.values()) {
            assertTrue(source.contains("case " + role.name() + ":"),
                    "RoleManager.createRoleInstance has no case for " + role);
        }
    }

    @Test
    void everyRegisteredCommandIsDeclaredInPluginYml() throws IOException {
        Set<String> declared = pluginYmlCommands();
        Set<String> used = new HashSet<>();
        Pattern pattern = Pattern.compile("(?:registerCommand|getCommand)\\(\"([a-z]+)\"");
        for (Path file : TestFiles.mainSources()) {
            Matcher matcher = pattern.matcher(Files.readString(file));
            while (matcher.find()) {
                used.add(matcher.group(1));
            }
        }
        assertFalse(used.isEmpty());
        for (String command : used) {
            assertTrue(declared.contains(command), "Command '" + command + "' is used in the code but missing in plugin.yml");
        }
    }

    @Test
    void everyCommandOfPluginYmlHasAnExecutor() throws IOException {
        String all = "";
        for (Path file : TestFiles.mainSources()) {
            all += Files.readString(file);
        }
        for (String command : pluginYmlCommands()) {
            assertTrue(all.contains("\"" + command + "\""), "plugin.yml declares '" + command + "' but no code registers it");
        }
    }

    @Test
    void noExternalNpcLibrary() throws IOException {
        for (Path file : TestFiles.mainSources()) {
            String source = Files.readString(file);
            assertFalse(source.contains("com.github.juliarn"), file + " uses NPCLib, which is not a declared dependency");
        }
    }

    @Test
    void everyRoleClassHasDescriptionsAndAnObjective() throws IOException {
        for (Path file : TestFiles.mainSources()) {
            String name = file.getFileName().toString();
            boolean concreteRole = name.endsWith("Role.java") && !name.startsWith("Duo") && !name.equals("Role.java")
                    && !name.endsWith("Base.java") && file.toString().contains("/role/");
            if (!concreteRole) {
                continue;
            }
            String source = Files.readString(file);
            assertTrue(source.contains("getDescription()"), name + " must describe the role");
            assertTrue(source.contains("getArenaPhaseDescription()"), name + " must describe the finale");
            assertTrue(source.contains("getObjective()"), name + " must define its objective");
        }
    }

    /** Commands declared in plugin.yml. */
    private Set<String> pluginYmlCommands() throws IOException {
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(TestFiles.reader("src/main/resources/plugin.yml"));
        assertNotNull(yaml.getConfigurationSection("commands"));
        return yaml.getConfigurationSection("commands").getKeys(false);
    }
}
