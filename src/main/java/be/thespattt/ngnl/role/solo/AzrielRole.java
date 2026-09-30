package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Azriel: detects Flugel movements within 50 blocks, copies a fragment of another Flugel's power
 * once per episode and, in the finale, creates a zone where flying is disabled.
 */
public class AzrielRole extends Role {

    /** Cooldown of the anti-flight zone in seconds. */
    private static final int ZONE_COOLDOWN = 20 * 60;
    /** Duration of the anti-flight zone in seconds. */
    private static final int ZONE_SECONDS = 30;
    /** Radius (blocks) of the anti-flight zone. */
    private static final double ZONE_RADIUS = 20.0;
    /** Radius (blocks) in which Flugel movements are detected. */
    private static final double DETECTION_RADIUS = 50.0;
    /** Flugel roles that can be detected or copied. */
    private static final Set<RoleType> FLUGEL_ROLES = Set.of(RoleType.JIBRIL, RoleType.AZRIEL);

    private int copiedEpisode = -1;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (AZRIEL)
     */
    public AzrielRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        runRepeating(this::detectFlugel, 20L, 20L * 15);
        giveItem(player, buildRoleItem(Material.PRISMARINE_CRYSTALS, "&b&lMimétisme Flügel",
                "&7Une fois par épisode, copie temporairement le pouvoir", "&7d'un autre Flügel proche."));
    }

    /**
     * Warn Azriel when another Flugel is within 50 blocks.
     */
    private void detectFlugel() {
        Player player = getPlayer();
        if (player == null || !isAlive()) {
            return;
        }
        for (Player other : nearbyAlivePlayers(player.getLocation(), DETECTION_RADIUS)) {
            if (isFlugel(other)) {
                MessageUtil.sendMessage(player, "&bMouvement de Flügel détecté près de &f" + other.getName());
            }
        }
    }

    /**
     * Check whether a player has a Flugel role.
     *
     * @param other Player to check
     * @return True if he is a Flugel
     */
    private boolean isFlugel(Player other) {
        var data = plugin.getPlayerManager().getNGNLPlayer(other.getUniqueId());
        return data != null && data.getRole() != null && FLUGEL_ROLES.contains(data.getRole().getRoleType());
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("anti_flight");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.BLAZE_ROD, "&c&lZone anti-vol",
                "&7Désactive le vol et la glisse autour de toi pendant 30 secondes.", "",
                "&eClic droit pour activer", "&cRecharge : 20 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.PRISMARINE_CRYSTALS) {
            return useMimicry();
        }
        return item.getType() == Material.BLAZE_ROD && useAntiFlightZone();
    }

    /**
     * Copy the power of a nearby Flugel (Speed II and Regeneration) once per episode.
     *
     * @return True if a power was copied
     */
    private boolean useMimicry() {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        int episode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        if (copiedEpisode == episode) {
            MessageUtil.sendMessage(player, "&cTu as déjà copié un Flügel pendant cet épisode.");
            return false;
        }
        Player model = nearbyAlivePlayers(player.getLocation(), DETECTION_RADIUS).stream()
                .filter(this::isFlugel).findFirst().orElse(null);
        if (model == null) {
            MessageUtil.sendMessage(player, "&cAucun autre Flügel à proximité (50 blocs).");
            return false;
        }
        copiedEpisode = episode;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60 * 20, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 15 * 20, 0, false, false));
        MessageUtil.sendMessage(player, "&aTu as copié un fragment du pouvoir de " + model.getName() + ".");
        return true;
    }

    /**
     * Create a fixed zone where flying and gliding are impossible for 30 seconds.
     *
     * @return True if the zone was created
     */
    private boolean useAntiFlightZone() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("anti_flight", ZONE_COOLDOWN)) {
            return false;
        }
        Location center = player.getLocation().clone();
        var task = runRepeating(() -> enforceNoFlight(center), 0L, 10L);
        runLater(task::cancel, ZONE_SECONDS * 20L);
        MessageUtil.broadcast("&cAzriel a créé une zone anti-vol !");
        return true;
    }

    /**
     * Ground every flying or gliding player inside the zone.
     *
     * @param center Center of the zone
     */
    private void enforceNoFlight(Location center) {
        center.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 1, 0), 20, ZONE_RADIUS / 2, 0.5, ZONE_RADIUS / 2, 0);
        for (Player other : center.getWorld().getPlayers()) {
            if (other.getLocation().distanceSquared(center) > ZONE_RADIUS * ZONE_RADIUS) {
                continue;
            }
            other.setAllowFlight(false);
            other.setFlying(false);
            if (other.isGliding()) {
                other.setGliding(false);
            }
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Azriel, sister of Jibril.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You detect Flugel movements within 50 blocks.",
                "Once per episode you can copy a fragment of another Flugel's power."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Anti-flight zone: disables flight and gliding in an area for 30 seconds",
                "(every 20 minutes)."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
