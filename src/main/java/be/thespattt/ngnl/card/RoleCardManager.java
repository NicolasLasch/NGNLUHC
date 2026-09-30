package be.thespattt.ngnl.card;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shows the role card of a player in a popup inventory.
 * The card picture comes from the resource pack: the title of the inventory is made of custom font
 * glyphs (one glyph per tile of the card) that the pack draws as the card image. Players who did not
 * load the pack get a plain text card instead. The popup can be closed (ESC) and reopened with /role.
 */
public class RoleCardManager implements Listener {

    private static final int PAGE_FRONT = 0;
    private static final int PAGE_BACK = 1;
    private static final int INVENTORY_SIZE = 54;
    private static final int FLIP_SLOT = 8;
    private static final int CLOSE_SLOT = 0;
    /** Ticks waited for the resource pack before falling back to the text card. */
    private static final long PACK_WAIT_TICKS = 100L;

    private final NoGameNoLife plugin;
    private final Key fontKey = Key.key("ngnl", "card");
    private final Map<RoleType, int[][]> glyphs = new EnumMap<>(RoleType.class);
    private final Set<UUID> packLoaded = new HashSet<>();
    private final Set<UUID> pendingReveal = new HashSet<>();
    private final ResourcePackHost packHost;

    private int gridSize = 2;
    private int shiftCharacter;
    private int joinCharacter;
    private int backCharacter;
    private String packUrl = "";
    private String packSha1 = "";

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public RoleCardManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.packHost = new ResourcePackHost(plugin);
        loadGlyphLayout();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------------ setup

    /**
     * Read the glyph layout generated together with the resource pack.
     */
    private void loadGlyphLayout() {
        var stream = plugin.getResource("cards/glyphs.yml");
        if (stream == null) {
            MessageUtil.logWarning("cards/glyphs.yml is missing: role cards will use the text fallback.");
            return;
        }
        YamlConfiguration layout = YamlConfiguration.loadConfiguration(new InputStreamReader(stream));
        gridSize = layout.getInt("grid", 2);
        shiftCharacter = layout.getInt("left-shift-char");
        joinCharacter = layout.getInt("join-char");
        backCharacter = layout.getInt("back-char");

        ConfigurationSection roles = layout.getConfigurationSection("roles");
        if (roles == null) {
            return;
        }
        for (String key : roles.getKeys(false)) {
            RoleType type = RoleType.getByName(key);
            if (type != null) {
                glyphs.put(type, new int[][]{
                        roles.getIntegerList(key + ".front").stream().mapToInt(Integer::intValue).toArray(),
                        roles.getIntegerList(key + ".back").stream().mapToInt(Integer::intValue).toArray()});
            }
        }
    }

    /**
     * Start the optional built-in pack host and read the pack settings (call once the config is loaded).
     */
    public void start() {
        var config = plugin.getConfig();
        if (!config.getBoolean("resourcepack.enabled", true)) {
            return;
        }
        packUrl = config.getString("resourcepack.url", "");
        packSha1 = config.getString("resourcepack.sha1", "");

        if (packUrl.isBlank() && config.getBoolean("resourcepack.self-host.enabled", false)) {
            String host = config.getString("resourcepack.self-host.public-host", "");
            if (host.isBlank()) {
                host = Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
                MessageUtil.logWarning("resourcepack.self-host.public-host is empty: using '" + host
                        + "'. Set it to the public address of your server so players can download the pack.");
            }
            if (packHost.start(config.getInt("resourcepack.self-host.port", 8123), host)) {
                packUrl = packHost.getUrl();
                packSha1 = packHost.getSha1();
            }
        }
        if (packUrl.isBlank()) {
            MessageUtil.logWarning("No resource pack URL configured: role cards will use the text fallback. See config.yml (resourcepack).");
        }
    }

    /**
     * Stop the built-in pack host.
     */
    public void stop() {
        packHost.stop();
    }

    // ------------------------------------------------------------------ resource pack

    /**
     * Send the resource pack to a player (if configured and not already loaded).
     *
     * @param player Player to send the pack to
     */
    public void sendPack(Player player) {
        if (packUrl.isBlank() || packLoaded.contains(player.getUniqueId())) {
            return;
        }
        boolean required = plugin.getConfig().getBoolean("resourcepack.required", false);
        player.setResourcePack(packUrl, packSha1.isBlank() ? null : packSha1, required,
                Component.text("Les cartes de rôles de No Game No Life UHC"));
    }

    /**
     * Whether a player has the resource pack loaded.
     *
     * @param player Player to check
     * @return True if the pack is loaded
     */
    public boolean hasPack(Player player) {
        return packLoaded.contains(player.getUniqueId());
    }

    /**
     * Track the pack status of the players and open pending cards as soon as the pack is ready.
     *
     * @param event Pack status event
     */
    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED:
                packLoaded.add(player.getUniqueId());
                if (pendingReveal.remove(player.getUniqueId())) {
                    openCurrentRole(player);
                }
                break;
            case DECLINED:
            case FAILED_DOWNLOAD:
            case FAILED_RELOAD:
            case INVALID_URL:
            case DISCARDED:
                packLoaded.remove(player.getUniqueId());
                break;
            default:
                break;
        }
    }

    /**
     * Forget a player who leaves.
     *
     * @param event Quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packLoaded.remove(event.getPlayer().getUniqueId());
        pendingReveal.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ reveal / open

    /**
     * Reveal a role to its player: short chat message and card popup.
     *
     * @param player Player receiving the role
     * @param role   His role
     */
    public void reveal(Player player, Role role) {
        MessageUtil.sendMessage(player, "&5&lTon rôle : &f" + role.getDisplayName() + " &7— &e/role &7pour rouvrir ta carte.");
        if (hasPack(player)) {
            open(player, role, PAGE_FRONT);
            return;
        }
        pendingReveal.add(player.getUniqueId());
        sendPack(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingReveal.remove(player.getUniqueId()) && player.isOnline()) {
                open(player, role, PAGE_FRONT);
            }
        }, PACK_WAIT_TICKS);
    }

    /**
     * Open the card of the role a player currently has.
     *
     * @param player Player who opens his card
     */
    public void openCurrentRole(Player player) {
        var data = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (data != null && data.getRole() != null) {
            open(player, data.getRole(), PAGE_FRONT);
        }
    }

    /**
     * Open a card page (picture if the pack is loaded, text otherwise).
     *
     * @param player Viewer
     * @param role   Role shown
     * @param page   0 = front (portrait), 1 = back (powers)
     */
    public void open(Player player, Role role, int page) {
        int[][] roleGlyphs = glyphs.get(role.getRoleType());
        if (hasPack(player) && roleGlyphs != null && roleGlyphs[page].length == gridSize * gridSize) {
            openPictureCard(player, role, page, roleGlyphs[page]);
        } else {
            openTextCard(player, role);
        }
    }

    // ------------------------------------------------------------------ picture card

    /**
     * Open the inventory whose title draws the card picture.
     *
     * @param player Viewer
     * @param role   Role shown
     * @param page   Page shown
     * @param tiles  Glyph code points of the page (row-major)
     */
    private void openPictureCard(Player player, Role role, int page, int[] tiles) {
        CardHolder holder = new CardHolder(role.getRoleType(), page);
        Component title = Component.text(buildTitle(tiles)).font(fontKey).color(NamedTextColor.WHITE);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.inventory = inventory;
        inventory.setItem(CLOSE_SLOT, buildButton(Material.BARRIER, "&cFermer", "&7Rouvre ta carte avec &e/role"));
        inventory.setItem(FLIP_SLOT, buildButton(Material.BOOK,
                page == PAGE_FRONT ? "&eVoir mes pouvoirs" : "&eVoir ma carte", "&7Clique pour tourner la carte"));
        player.openInventory(inventory);
    }

    /**
     * Build the title string: one glyph per tile, rows separated by a backwards shift.
     *
     * @param tiles Glyph code points (row-major)
     * @return Title text using the card font
     */
    private String buildTitle(int[] tiles) {
        StringBuilder title = new StringBuilder();
        title.appendCodePoint(shiftCharacter);
        for (int row = 0; row < gridSize; row++) {
            for (int column = 0; column < gridSize; column++) {
                if (column > 0) {
                    title.appendCodePoint(joinCharacter);
                }
                title.appendCodePoint(tiles[row * gridSize + column]);
            }
            if (row < gridSize - 1) {
                title.appendCodePoint(backCharacter);
            }
        }
        return title.toString();
    }

    /**
     * Build a button item.
     *
     * @param material Icon
     * @param name     Colored name
     * @param lore     Lore line
     * @return The button
     */
    private ItemStack buildButton(Material material, String name, String lore) {
        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    // ------------------------------------------------------------------ text fallback

    /**
     * Open the plain text card used when the resource pack is not loaded.
     *
     * @param player Viewer
     * @param role   Role shown
     */
    private void openTextCard(Player player, Role role) {
        CardHolder holder = new CardHolder(role.getRoleType(), PAGE_FRONT);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§5" + role.getDisplayName());
        holder.inventory = inventory;

        inventory.setItem(11, describeRoleItem(role));
        inventory.setItem(15, describeArenaItem(role));
        inventory.setItem(22, buildButton(Material.BARRIER, "&cFermer", "&7Rouvre ta carte avec &e/role"));
        player.openInventory(inventory);

        if (!packUrl.isBlank() && !hasPack(player)) {
            MessageUtil.sendMessage(player, "&7(Accepte le resource pack du serveur pour voir l'illustration de ta carte.)");
        }
    }

    /**
     * Item describing the role and its mining-phase powers.
     *
     * @param role Role shown
     * @return Description item
     */
    private ItemStack describeRoleItem(Role role) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Faction : &f" + role.getRoleType().getFaction().getDisplayName());
        lore.add("&7Objectif : &f" + role.getObjective());
        lore.add("");
        role.getDescription().forEach(line -> lore.add("&f" + line));
        return new ItemBuilder(Material.PLAYER_HEAD).name("&5&l" + role.getDisplayName()).lore(lore).build();
    }

    /**
     * Item describing the finale powers.
     *
     * @param role Role shown
     * @return Description item
     */
    private ItemStack describeArenaItem(Role role) {
        List<String> lore = new ArrayList<>();
        role.getArenaPhaseDescription().forEach(line -> lore.add("&f" + line));
        return new ItemBuilder(Material.NETHER_STAR).name("&6&lFinale").lore(lore).build();
    }

    // ------------------------------------------------------------------ clicks

    /**
     * Handle the buttons of the card popup; the card itself can not be taken.
     *
     * @param event Click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CardHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == CLOSE_SLOT || (event.getRawSlot() == 22 && event.getInventory().getSize() == 27)) {
            player.closeInventory();
        } else if (event.getRawSlot() == FLIP_SLOT && event.getInventory().getSize() == INVENTORY_SIZE) {
            flipPage(player, holder);
        }
    }

    /**
     * Turn the card over.
     *
     * @param player Viewer
     * @param holder Current card
     */
    private void flipPage(Player player, CardHolder holder) {
        var data = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        Role role = data != null ? data.getRole() : null;
        if (role != null) {
            open(player, role, holder.page == PAGE_FRONT ? PAGE_BACK : PAGE_FRONT);
        }
    }

    /**
     * Holder identifying a role card popup.
     */
    private static final class CardHolder implements InventoryHolder {
        private final RoleType roleType;
        private final int page;
        private Inventory inventory;

        private CardHolder(RoleType roleType, int page) {
            this.roleType = roleType;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
