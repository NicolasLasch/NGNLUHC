package be.thespattt.ngnl;

import be.thespattt.ngnl.role.RoleType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The role cards need three things to agree: the roles of the plugin, the card texts of the
 * generator, and the glyph layout + resource pack bundled in the jar.
 */
class RoleCardPackTest {

    private static final String ZIP = "src/main/resources/resourcepack/NGNL-ResourcePack.zip";

    @Test
    void everyRoleHasACardText() throws IOException {
        Set<String> keys = new HashSet<>();
        Matcher matcher = Pattern.compile("key=\"([A-Z_0-9]+)\"").matcher(TestFiles.read("tools/cards_data.py"));
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        for (RoleType role : RoleType.values()) {
            assertTrue(keys.contains(role.name()), "tools/cards_data.py has no card for " + role);
        }
        assertEquals(RoleType.values().length, keys.size(), "cards_data.py has roles that do not exist");
    }

    @Test
    void glyphLayoutCoversEveryRoleWithUniqueCodePoints() throws IOException {
        YamlConfiguration layout = YamlConfiguration.loadConfiguration(TestFiles.reader("src/main/resources/cards/glyphs.yml"));
        int grid = layout.getInt("grid");
        assertTrue(grid >= 1);
        ConfigurationSection roles = layout.getConfigurationSection("roles");
        assertNotNull(roles);

        Set<Integer> codePoints = new HashSet<>(List.of(layout.getInt("left-shift-char"), layout.getInt("join-char"), layout.getInt("back-char")));
        assertEquals(3, codePoints.size(), "space characters must differ");
        for (RoleType role : RoleType.values()) {
            for (String page : new String[]{"front", "back"}) {
                List<Integer> glyphs = roles.getIntegerList(role.name() + "." + page);
                assertEquals(grid * grid, glyphs.size(), role + " " + page + " needs grid*grid glyphs");
                for (int glyph : glyphs) {
                    assertTrue(glyph >= 0xE000 && glyph <= 0xF8FF, "glyphs must be private-use characters");
                    assertTrue(codePoints.add(glyph), "duplicate glyph " + Integer.toHexString(glyph));
                }
            }
        }
    }

    @Test
    void bundledPackIsAValidResourcePack() throws IOException {
        assertTrue(Files.exists(TestFiles.path(ZIP)), "the resource pack zip is missing: run tools/generate_resourcepack.py");
        try (ZipFile zip = new ZipFile(TestFiles.path(ZIP).toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            ZipEntry font = zip.getEntry("assets/ngnl/font/card.json");
            assertNotNull(font, "font definition missing");

            String json = new String(zip.getInputStream(font).readAllBytes(), "UTF-8");
            Matcher files = Pattern.compile("\"file\":\\s*\"ngnl:font/cards/([a-z0-9_]+)\\.png\"").matcher(json);
            int tiles = 0;
            while (files.find()) {
                tiles++;
                assertNotNull(zip.getEntry("assets/ngnl/textures/font/cards/" + files.group(1) + ".png"),
                        "tile referenced by the font is missing: " + files.group(1));
            }
            assertEquals(RoleType.values().length * 2 * 4, tiles, "2 pages x 4 tiles per role");
        }
    }

    @Test
    void packMetaTargetsMinecraft1214() throws IOException {
        try (ZipFile zip = new ZipFile(TestFiles.path(ZIP).toFile());
             InputStream in = zip.getInputStream(zip.getEntry("pack.mcmeta"))) {
            assertTrue(new String(in.readAllBytes(), "UTF-8").contains("\"pack_format\": 46"));
        }
    }
}
