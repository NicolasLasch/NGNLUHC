package be.thespattt.ngnl.game.world;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs a zipped world (the custom arena map) the first time it is needed.
 * Looked up in this order: plugins/NoGameNoLifeUHC/maps/&lt;world&gt;.zip, then a map bundled inside
 * the plugin jar at maps/&lt;world&gt;.zip. The zip may contain the world files at its root or inside a
 * single top-level folder.
 */
public class MapInstaller {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public MapInstaller(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Install a map into the world container if one is available.
     *
     * @param worldName   Name of the world (folder created in the world container)
     * @param worldFolder Destination folder
     * @return True if a map was extracted
     */
    public boolean installIfAvailable(String worldName, File worldFolder) {
        File external = new File(plugin.getDataFolder(), "maps/" + worldName + ".zip");
        try {
            if (external.isFile()) {
                try (InputStream in = Files.newInputStream(external.toPath())) {
                    extract(in, worldFolder);
                }
                MessageUtil.logInfo("Arena map installed from " + external.getPath());
                return true;
            }
            try (InputStream bundled = plugin.getResource("maps/" + worldName + ".zip")) {
                if (bundled != null) {
                    extract(bundled, worldFolder);
                    MessageUtil.logInfo("Arena map installed from the plugin jar (maps/" + worldName + ".zip)");
                    return true;
                }
            }
        } catch (IOException exception) {
            MessageUtil.logError("Could not install the arena map '" + worldName + "'", exception);
            deleteRecursively(worldFolder.toPath());
        }
        return false;
    }

    /**
     * Extract a zip stream into a folder (zip-slip safe). A single top-level folder is stripped and
     * the files that must not be copied between servers (uid.dat, session.lock) are skipped.
     *
     * @param zipStream   Zip content
     * @param destination Destination folder (created if needed)
     * @throws IOException If the zip is invalid or cannot be written
     */
    public static void extract(InputStream zipStream, File destination) throws IOException {
        List<String> names = new ArrayList<>();
        List<byte[]> contents = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    names.add(entry.getName().replace('\\', '/'));
                    contents.add(zip.readAllBytes());
                }
            }
        }
        String prefix = commonTopFolder(names);
        Path root = destination.toPath().toAbsolutePath().normalize();
        Files.createDirectories(root);

        for (int i = 0; i < names.size(); i++) {
            String relative = names.get(i).substring(prefix.length());
            if (relative.isEmpty() || isSkipped(relative)) {
                continue;
            }
            Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Unsafe path in zip: " + names.get(i));
            }
            Files.createDirectories(target.getParent());
            Files.write(target, contents.get(i));
        }
        if (!new File(destination, "level.dat").isFile()) {
            throw new IOException("The zip does not contain a level.dat: it is not a Minecraft world");
        }
    }

    /**
     * Find the folder that contains level.dat when the world is wrapped in a single top-level folder.
     *
     * @param names Names of the files in the zip
     * @return Prefix to strip ("" or "folder/")
     */
    private static String commonTopFolder(List<String> names) {
        for (String name : names) {
            if (name.equals("level.dat")) {
                return "";
            }
        }
        for (String name : names) {
            if (name.endsWith("/level.dat") && name.indexOf('/') == name.lastIndexOf('/')) {
                return name.substring(0, name.indexOf('/') + 1);
            }
        }
        return "";
    }

    /**
     * Files that must not be copied from another server.
     *
     * @param relative Relative path
     * @return True if skipped
     */
    private static boolean isSkipped(String relative) {
        return relative.equals("uid.dat") || relative.equals("session.lock");
    }

    /**
     * Delete a folder and everything inside.
     *
     * @param path Folder to delete
     */
    private void deleteRecursively(Path path) {
        try {
            if (Files.exists(path)) {
                try (var walk = Files.walk(path)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (IOException ignored) {
            // Best effort: a broken partial extraction is reported in the log already.
        }
    }
}
