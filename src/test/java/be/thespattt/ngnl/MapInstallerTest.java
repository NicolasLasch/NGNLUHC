package be.thespattt.ngnl;

import be.thespattt.ngnl.game.world.MapInstaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The arena map is installed from a zip: flat or wrapped in a folder, never writing outside.
 */
class MapInstallerTest {

    @TempDir
    File temp;

    private byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue().getBytes());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void extractsAFlatWorld() throws IOException {
        File target = new File(temp, "flat");
        MapInstaller.extract(new ByteArrayInputStream(zip(Map.of("level.dat", "x", "region/r.0.0.mca", "y"))), target);
        assertTrue(new File(target, "level.dat").isFile());
        assertTrue(new File(target, "region/r.0.0.mca").isFile());
    }

    @Test
    void stripsASingleTopLevelFolderAndSkipsMachineFiles() throws IOException {
        File target = new File(temp, "wrapped");
        MapInstaller.extract(new ByteArrayInputStream(zip(Map.of(
                "ngnl_arena_city/level.dat", "x",
                "ngnl_arena_city/uid.dat", "id",
                "ngnl_arena_city/session.lock", "lock",
                "ngnl_arena_city/region/r.0.0.mca", "y"))), target);
        assertTrue(new File(target, "level.dat").isFile());
        assertTrue(new File(target, "region/r.0.0.mca").isFile());
        assertFalse(new File(target, "uid.dat").exists());
        assertFalse(new File(target, "session.lock").exists());
    }

    @Test
    void refusesPathsEscapingTheDestination() throws IOException {
        File target = new File(temp, "evil");
        byte[] evil = zip(Map.of("level.dat", "x", "../../escaped.txt", "boom"));
        assertThrows(IOException.class, () -> MapInstaller.extract(new ByteArrayInputStream(evil), target));
        assertFalse(new File(temp.getParentFile(), "escaped.txt").exists());
    }

    @Test
    void refusesAZipThatIsNotAWorld() throws IOException {
        File target = new File(temp, "notworld");
        byte[] notWorld = zip(Map.of("readme.txt", "hello"));
        assertThrows(IOException.class, () -> MapInstaller.extract(new ByteArrayInputStream(notWorld), target));
    }
}
