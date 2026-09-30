package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plum: sees the outline of nearby players when it is dark, can place up to 3 listening devices that
 * record the conversations nearby as text (each has a 20% chance to be noticed by a player coming
 * within 5 blocks) and, in the finale, casts a temporary blindness on nearby enemies.
 */
public class PlumRole extends Role {

    /** Cooldown of the blind spell in seconds. */
    private static final int BLIND_COOLDOWN = 15 * 60;
    /** Maximum number of listening devices. */
    private static final int MAX_DEVICES = 3;
    /** Radius (blocks) in which a device hears chat messages. */
    private static final double HEARING_RADIUS = 15.0;
    /** Radius (blocks) in which a device notices players passing by. */
    private static final double PRESENCE_RADIUS = 10.0;
    /** Radius (blocks) within which a player may detect a device. */
    private static final double DETECTION_RADIUS = 5.0;
    /** Percent chance that a player detects a device. */
    private static final int DETECTION_CHANCE = 20;
    /** Light level under which the outline effect is active. */
    private static final int DARKNESS_LIGHT_LEVEL = 7;
    /** Radius (blocks) of the darkness outline and of the blind spell. */
    private static final double SIGHT_RADIUS = 12.0;

    private final List<ListeningDevice> devices = new ArrayList<>();

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (PLUM)
     */
    public PlumRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        giveItem(player, buildRoleItem(Material.SCULK_SENSOR, "&5&lDispositif d'écoute",
                "&7Clic droit : place un dispositif à ta position (3 maximum).",
                "&7Accroupi + clic droit : lit les enregistrements.",
                "&7Un joueur qui s'approche à moins de 5 blocs a 20% de chance de le repérer."));
        runRepeating(this::showOutlines, 20L, 20L * 5);
        runRepeating(this::tickDevices, 20L, 20L);
    }

    // ------------------------------------------------------------------ darkness outline

    /**
     * Make nearby players glow while Plum stands in the dark.
     */
    private void showOutlines() {
        Player player = getPlayer();
        if (player == null || !isAlive() || player.getLocation().getBlock().getLightLevel() > DARKNESS_LIGHT_LEVEL) {
            return;
        }
        for (Player other : nearbyAlivePlayers(player.getLocation(), SIGHT_RADIUS)) {
            other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
        }
    }

    // ------------------------------------------------------------------ listening devices

    /**
     * Place or read the devices depending on whether Plum sneaks.
     *
     * @return True if something happened
     */
    private boolean useListeningDevice() {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        return player.isSneaking() ? readRecordings(player) : placeDevice(player);
    }

    /**
     * Place a device at Plum's position.
     *
     * @param player Plum
     * @return True if the device was placed
     */
    private boolean placeDevice(Player player) {
        if (devices.size() >= MAX_DEVICES) {
            MessageUtil.sendMessage(player, "&cTu as déjà placé " + MAX_DEVICES + " dispositifs.");
            return false;
        }
        devices.add(new ListeningDevice(player.getLocation().clone()));
        MessageUtil.sendMessage(player, "&dDispositif d'écoute placé (" + devices.size() + "/" + MAX_DEVICES + ").");
        return true;
    }

    /**
     * Print everything the devices recorded.
     *
     * @param player Plum
     * @return True if there was something to read
     */
    private boolean readRecordings(Player player) {
        if (devices.isEmpty()) {
            MessageUtil.sendMessage(player, "&cTu n'as placé aucun dispositif.");
            return false;
        }
        int index = 1;
        for (ListeningDevice device : devices) {
            MessageUtil.sendMessage(player, "&5Dispositif " + index + " &7(" + device.location.getBlockX() + ", "
                    + device.location.getBlockY() + ", " + device.location.getBlockZ() + ") :");
            if (device.log.isEmpty()) {
                MessageUtil.sendMessage(player, "&7  (rien d'enregistré)");
            }
            device.log.forEach(line -> MessageUtil.sendMessage(player, "&7  " + line));
            index++;
        }
        return true;
    }

    /**
     * Every second: record the players passing near a device and let them detect it.
     */
    private void tickDevices() {
        for (ListeningDevice device : devices) {
            if (device.location.getWorld() == null) {
                continue;
            }
            for (Player other : device.location.getWorld().getPlayers()) {
                if (other.getUniqueId().equals(playerId) || !plugin.getGameManager().isPlayerAlive(other.getUniqueId())) {
                    continue;
                }
                double distance = other.getLocation().distance(device.location);
                if (distance <= PRESENCE_RADIUS) {
                    device.recordPresence(other.getName());
                }
                if (distance <= DETECTION_RADIUS) {
                    tryDetect(device, other);
                }
            }
        }
    }

    /**
     * Give a player a single 20% chance to notice a device he walks next to.
     *
     * @param device Device in range
     * @param other  Player in range
     */
    private void tryDetect(ListeningDevice device, Player other) {
        if (!device.rolledDetection.add(other.getUniqueId())) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) < DETECTION_CHANCE) {
            MessageUtil.sendMessage(other, "&cTu as repéré un dispositif d'écoute près de toi (" + device.location.getBlockX()
                    + ", " + device.location.getBlockY() + ", " + device.location.getBlockZ() + ") !");
            Player plum = getPlayer();
            if (plum != null) {
                MessageUtil.sendMessage(plum, "&c" + other.getName() + " a repéré un de tes dispositifs !");
            }
        }
    }

    /**
     * Record a chat message said near the devices (called by the chat listener).
     *
     * @param speaker Player who talked
     * @param message Message he said
     */
    public void recordConversation(Player speaker, String message) {
        if (speaker.getUniqueId().equals(playerId)) {
            return;
        }
        for (ListeningDevice device : devices) {
            boolean sameWorld = device.location.getWorld() != null && device.location.getWorld().equals(speaker.getWorld());
            if (sameWorld && device.location.distance(speaker.getLocation()) <= HEARING_RADIUS) {
                device.log(speaker.getName() + " : " + message);
            }
        }
    }

    // ------------------------------------------------------------------ finale blind spell

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("blind_spell");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.INK_SAC, "&5&lSort de cécité",
                "&7Aveugle temporairement les ennemis proches.", "",
                "&eClic droit pour activer", "&cRecharge : 15 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.SCULK_SENSOR) {
            return useListeningDevice();
        }
        return item.getType() == Material.INK_SAC && useBlindSpell();
    }

    /**
     * Blind every nearby enemy for 8 seconds.
     *
     * @return True if the spell was cast
     */
    private boolean useBlindSpell() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("blind_spell", BLIND_COOLDOWN)) {
            return false;
        }
        for (Player other : nearbyAlivePlayers(player.getLocation(), SIGHT_RADIUS)) {
            if (!isFriendly(other.getUniqueId())) {
                PotionEffect blindness = new PotionEffect(PotionEffectType.BLINDNESS, 8 * 20, 0, false, false);
                other.addPotionEffect(blindness);
                plugin.getSpecialItemManager().getEffects().recordAbilityUsedAgainst(other, "Sort de cécité", blindness);
            }
        }
        MessageUtil.sendMessage(player, "&aTon sort de cécité frappe les ennemis proches.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Plum, a Dhampir.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You thrive in darkness and see the outline of nearby players in the dark.",
                "You can place up to 3 listening devices that record nearby conversations."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Blind spell: temporarily blinds nearby enemies (every 15 minutes)."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }

    /**
     * A listening device and what it recorded.
     */
    private static final class ListeningDevice {
        private static final int MAX_LOG_LINES = 40;
        private static final long PRESENCE_LOG_INTERVAL_MS = 60_000L;

        private final Location location;
        private final List<String> log = new ArrayList<>();
        private final Set<UUID> rolledDetection = new HashSet<>();
        private final java.util.Map<String, Long> lastPresence = new java.util.HashMap<>();

        private ListeningDevice(Location location) {
            this.location = location;
        }

        /**
         * Append a line to the log with the current time.
         *
         * @param line Text to record
         */
        private void log(String line) {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            log.add("[" + time + "] " + line);
            while (log.size() > MAX_LOG_LINES) {
                log.remove(0);
            }
        }

        /**
         * Note that a player is hanging around (at most once per minute per player).
         *
         * @param name Player name
         */
        private void recordPresence(String name) {
            long now = System.currentTimeMillis();
            Long last = lastPresence.get(name);
            if (last == null || now - last > PRESENCE_LOG_INTERVAL_MS) {
                lastPresence.put(name, now);
                log(name + " passe à proximité.");
            }
        }
    }
}
