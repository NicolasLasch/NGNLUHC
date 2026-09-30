package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Shi: shares his health pool with Ku, analyses the past mini-games of his opponent and
 * creates temporary safe zones in the finale.
 */
public class ShiRole extends ShiKuBase {

    /** Cooldown of the safety core in seconds. */
    private static final int SAFE_ZONE_COOLDOWN = 20 * 60;
    /** Lifetime of a safe zone in seconds. */
    private static final int SAFE_ZONE_SECONDS = 15;
    /** Radius (blocks) of a safe zone. */
    private static final double SAFE_ZONE_RADIUS = 8.0;

    private Location safeZoneCenter;
    private long safeZoneUntil = 0L;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (SHI)
     */
    public ShiRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Player ku = getPartnerPlayer();
        if (ku != null) {
            MessageUtil.sendMessage(player, "&eKu est : &a" + ku.getName());
        }
        MessageUtil.sendMessage(player, "&bTa santé est partagée avec Ku : vous devez tous les deux être éliminés pour perdre.");
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        Player rival = Bukkit.getPlayer(opponent);
        if (player == null || rival == null) {
            return;
        }
        var stats = plugin.getMiniGameStatsTracker();
        MessageUtil.sendMessage(player, "&b[Analyse] &f" + rival.getName() + " : "
                + stats.getWins(opponent) + " victoire(s), " + stats.getLosses(opponent) + " défaite(s).");
        if (!stats.getLostGames(opponent).isEmpty()) {
            MessageUtil.sendMessage(player, "&b[Analyse] &7Défaites passées : &f" + joinGameNames(stats.getLostGames(opponent)));
        }
        if (!stats.getWonGames(opponent).isEmpty()) {
            MessageUtil.sendMessage(player, "&b[Analyse] &7Victoires passées : &f" + joinGameNames(stats.getWonGames(opponent)));
        }
    }

    /**
     * Join the display names of mini-games in a readable list.
     *
     * @param games Mini-game types
     * @return Comma separated names
     */
    private String joinGameNames(List<MiniGameType> games) {
        return String.join(", ", games.stream().map(MiniGameType::getDisplayName).distinct().toList());
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("safety_core");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.LIGHT_BLUE_DYE, "&b&lNoyau de sécurité",
                "&7Crée une zone de 8 blocs où aucun dégât ne peut être infligé", "&7pendant 15 secondes.", "",
                "&eClic droit pour activer", "&cRecharge : 20 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.LIGHT_BLUE_DYE && useSafeZone();
    }

    /**
     * Create a temporary zone in which nobody can be damaged.
     *
     * @return True if the zone was created
     */
    private boolean useSafeZone() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("safety_core", SAFE_ZONE_COOLDOWN)) {
            return false;
        }
        safeZoneCenter = player.getLocation().clone();
        safeZoneUntil = System.currentTimeMillis() + SAFE_ZONE_SECONDS * 1000L;
        runRepeating(this::drawSafeZone, 0L, 10L);
        MessageUtil.broadcast("&bShi a créé une zone de sécurité !");
        return true;
    }

    /**
     * Draw the border of the safe zone with particles.
     */
    private void drawSafeZone() {
        if (!isSafeZoneActive()) {
            return;
        }
        for (int i = 0; i < 24; i++) {
            double angle = 2 * Math.PI * i / 24;
            Location point = safeZoneCenter.clone().add(Math.cos(angle) * SAFE_ZONE_RADIUS, 0.3, Math.sin(angle) * SAFE_ZONE_RADIUS);
            safeZoneCenter.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Check whether a location lies inside the currently active safe zone.
     *
     * @param location Location to test
     * @return True if the location is protected
     */
    public boolean isInSafeZone(Location location) {
        return isSafeZoneActive() && safeZoneCenter.getWorld().equals(location.getWorld())
                && safeZoneCenter.distanceSquared(location) <= SAFE_ZONE_RADIUS * SAFE_ZONE_RADIUS;
    }

    /**
     * Check whether a safe zone currently exists.
     *
     * @return True while the zone is active
     */
    public boolean isSafeZoneActive() {
        return safeZoneCenter != null && System.currentTimeMillis() < safeZoneUntil;
    }

    /**
     * Check whether any Shi currently protects a location with a safe zone.
     *
     * @param plugin   Plugin instance
     * @param location Location to test
     * @return True if some safe zone covers the location
     */
    public static boolean isProtectedByAnySafeZone(NoGameNoLife plugin, Location location) {
        for (Role role : plugin.getRoleManager().getAllRoles()) {
            if (role instanceof ShiRole shi && shi.isInSafeZone(location)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Shi.",
                "Your goal is to win with Ku.",
                "Your health is shared with Ku: you must both be eliminated to lose.",
                "Before a mini-game you analyse your opponent's past mini-games."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Safety Core: creates a zone where no damage can be dealt (15 seconds,",
                "every 20 minutes)."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Ku.";
    }
}
