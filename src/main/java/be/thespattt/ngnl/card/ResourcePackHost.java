package be.thespattt.ngnl.card;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Optional tiny HTTP server that serves the bundled resource pack to the players, so the server
 * owner does not need any external hosting (the port must be reachable by the players).
 */
public class ResourcePackHost {

    private static final String RESOURCE_PATH = "resourcepack/NGNL-ResourcePack.zip";
    private static final String FILE_NAME = "NGNL-ResourcePack.zip";

    private final NoGameNoLife plugin;
    private HttpServer server;
    private String sha1;
    private String url;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public ResourcePackHost(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Extract the bundled pack and start serving it.
     *
     * @param port       TCP port to listen on
     * @param publicHost Host name or IP players use to reach the server
     * @return True if the server started
     */
    public boolean start(int port, String publicHost) {
        try {
            File packFile = extractPack();
            sha1 = computeSha1(packFile);
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/" + FILE_NAME, exchange -> serve(exchange, packFile));
            server.start();
            url = "http://" + publicHost + ":" + port + "/" + FILE_NAME;
            MessageUtil.logInfo("Resource pack served at " + url + " (sha1 " + sha1 + ")");
            return true;
        } catch (IOException | NoSuchAlgorithmException exception) {
            MessageUtil.logError("Could not start the resource pack host on port " + port, exception);
            return false;
        }
    }

    /**
     * Copy the pack from the plugin jar to the data folder.
     *
     * @return The extracted file
     * @throws IOException If the pack is missing or cannot be written
     */
    private File extractPack() throws IOException {
        File target = new File(plugin.getDataFolder(), FILE_NAME);
        plugin.getDataFolder().mkdirs();
        try (InputStream in = plugin.getResource(RESOURCE_PATH)) {
            if (in == null) {
                throw new IOException("Bundled resource pack not found: " + RESOURCE_PATH);
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /**
     * Compute the SHA-1 of a file, as required by the Minecraft client.
     *
     * @param file File to hash
     * @return Lower-case hexadecimal hash
     * @throws IOException              If the file cannot be read
     * @throws NoSuchAlgorithmException If SHA-1 is unavailable
     */
    private String computeSha1(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file.toPath())));
    }

    /**
     * Answer an HTTP request with the pack file.
     *
     * @param exchange HTTP exchange
     * @param file     Pack file
     * @throws IOException If the answer cannot be written
     */
    private void serve(com.sun.net.httpserver.HttpExchange exchange, File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public String getUrl() {
        return url;
    }

    public String getSha1() {
        return sha1;
    }
}
