package be.thespattt.ngnl.player.faction;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.item.structure.FlugelLibrary;
import be.thespattt.ngnl.item.structure.StructureUtil;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.util.DirectionArrow;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the faction advantages that need periodic checks or state:
 * proximity bonuses between members of a faction, the Werebeasts' enhanced detection, the Flügel
 * library and the betrayal mark (killing a member of one's own faction before the arena).
 * (Enchant and damage bonuses are plain rules in FactionBonusListener.)
 */
public class FactionManager {

    /** Distance (blocks) under which two members of a faction are "close". */
    private static final double PROXIMITY_RANGE = 15.0;
    /** Radius (blocks) of the Werebeasts' detection. */
    private static final double DETECTION_RANGE = 40.0;
    /** Ticks between two periodic checks. */
    private static final long CHECK_PERIOD = 40L;
    /** Distance range (blocks) of the Flügel library from the world center. */
    private static final double LIBRARY_MIN_DISTANCE = 100;

    private final NoGameNoLife plugin;
    private final Map<FactionType, Integer> factionMemberCounts = new EnumMap<>(FactionType.class);
    private final Set<UUID> traitors = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final FlugelLibrary library = new FlugelLibrary();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public FactionManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Load the factions (initialize member counts).
     */
    public void loadFactions() {
        for (FactionType type : FactionType.values()) {
            factionMemberCounts.put(type, 0);
        }
        MessageUtil.logInfo("Loaded " + FactionType.values().length + " factions");
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Build the Flügel library in the mining world (called when the game starts).
     *
     * @param world Mining world
     */
    public void prepareGame(World world) {
        stop();
        traitors.clear();
        Location center = world.getWorldBorder().getCenter();
        double radius = world.getWorldBorder().getSize() / 2.0;
        library.build(StructureUtil.randomSurfaceLocation(world, center.getX(), center.getZ(),
                Math.min(LIBRARY_MIN_DISTANCE, radius * 0.2), radius * 0.5));
    }

    /**
     * Start the periodic faction effects (called once the roles are revealed).
     */
    public void start() {
        stop();
        if (!plugin.getConfigManager().getGameConfig().areFactionBonusesEnabled()) {
            return;
        }
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::applyProximityBonuses, CHECK_PERIOD, CHECK_PERIOD));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::runWerebeastDetection, CHECK_PERIOD, CHECK_PERIOD / 2));
    }

    /**
     * Stop the periodic effects (game end).
     */
    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    // ------------------------------------------------------------------ proximity bonuses

    /**
     * Give a short buff to every player who has at least one other alive member of his faction
     * within 15 blocks.
     */
    private void applyProximityBonuses() {
        if (!plugin.getConfigManager().getGameConfig().isProximityBonusEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            FactionType faction = aliveFactionOf(player);
            PotionEffectType bonus = faction != null ? proximityEffectOf(faction) : null;
            if (bonus != null && !plugin.getMiniGameEngine().isPlayerInMiniGame(player.getUniqueId())
                    && hasCloseFactionMate(player, faction)) {
                player.addPotionEffect(new PotionEffect(bonus, 100, 0, false, false));
            }
        }
    }

    /**
     * Effect given by the proximity of two members of a faction.
     *
     * @param faction Faction
     * @return Potion effect type, or null if the faction has none
     */
    private PotionEffectType proximityEffectOf(FactionType faction) {
        switch (faction) {
            case IMANITY:
                return PotionEffectType.SPEED;
            case FLUGEL:
                return PotionEffectType.JUMP_BOOST;
            case WEREBEASTS:
                return PotionEffectType.NIGHT_VISION;
            case EX_MACHINA:
                return PotionEffectType.HASTE;
            case ELVES:
                return PotionEffectType.LUCK;
            case OLD_DEUS:
                return PotionEffectType.RESISTANCE;
            default:
                return null;
        }
    }

    /**
     * Check whether another alive member of the faction stands within 15 blocks.
     *
     * @param player  Player to check
     * @param faction His faction
     * @return True if a faction mate is close
     */
    private boolean hasCloseFactionMate(Player player, FactionType faction) {
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player) && aliveFactionOf(other) == faction
                    && other.getLocation().distanceSquared(player.getLocation()) <= PROXIMITY_RANGE * PROXIMITY_RANGE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Faction of an alive player in the game.
     *
     * @param player Player to check
     * @return His faction, or null
     */
    private FactionType aliveFactionOf(Player player) {
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            return null;
        }
        NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        return data != null ? data.getFaction() : null;
    }

    // ------------------------------------------------------------------ werebeasts detection

    /**
     * Werebeasts feel the closest player within 40 blocks (anonymous arrow in the action bar).
     */
    private void runWerebeastDetection() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (aliveFactionOf(player) != FactionType.WEREBEASTS) {
                continue;
            }
            Player nearest = findNearestOther(player);
            if (nearest != null) {
                DirectionArrow.showAnonymous(player, nearest);
            }
        }
    }

    /**
     * Find the nearest alive player who is not the viewer's partner or ally.
     *
     * @param viewer Detecting player
     * @return The nearest other player within range, or null
     */
    private Player findNearestOther(Player viewer) {
        NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(viewer.getUniqueId());
        Role role = data != null ? data.getRole() : null;
        Player best = null;
        double bestDistance = DETECTION_RANGE * DETECTION_RANGE;
        for (Player other : viewer.getWorld().getPlayers()) {
            boolean friendly = role != null && (other.getUniqueId().equals(role.getPartnerUUID())
                    || (data.hasAlliancePartner() && other.getUniqueId().equals(data.getAlliancePartner())));
            double distance = other.getLocation().distanceSquared(viewer.getLocation());
            if (!other.equals(viewer) && !friendly && plugin.getGameManager().isPlayerAlive(other.getUniqueId()) && distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ betrayal

    /**
     * Called for every elimination: killing a member of your own faction before the arena is a betrayal.
     *
     * @param victimId UUID of the eliminated player
     * @param killerId UUID of the killer (may be null)
     */
    public void onElimination(UUID victimId, UUID killerId) {
        if (killerId == null || killerId.equals(victimId) || !plugin.getConfigManager().getGameConfig().isBetrayalPenaltyEnabled()
                || plugin.getGameManager().getGameState() != be.thespattt.ngnl.game.GameState.MINING_PHASE) {
            return;
        }
        NGNLPlayer victim = plugin.getPlayerManager().getNGNLPlayer(victimId);
        NGNLPlayer killer = plugin.getPlayerManager().getNGNLPlayer(killerId);
        if (victim == null || killer == null || victim.getFaction() == null || victim.getFaction() != killer.getFaction()) {
            return;
        }
        traitors.add(killerId);
        Player traitor = Bukkit.getPlayer(killerId);
        if (traitor != null) {
            MessageUtil.sendMessage(traitor, "&cTu as trahi ta faction ! Tu seras visiblement marqué pendant la finale.");
        }
    }

    /**
     * Mark every traitor with permanent Glowing when the arena phase starts.
     */
    public void applyBetrayalMarks() {
        for (UUID traitorId : traitors) {
            Player traitor = Bukkit.getPlayer(traitorId);
            NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(traitorId);
            if (traitor == null || data == null || !plugin.getGameManager().isPlayerAlive(traitorId)) {
                continue;
            }
            traitor.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            MessageUtil.broadcast("&4Un traître de la faction &f" + data.getFaction().getDisplayName() + " &4est marqué : il brille dans l'arène !");
        }
    }

    // ------------------------------------------------------------------ Flügel library

    /**
     * The Flügel library.
     *
     * @return The library structure and rules
     */
    public FlugelLibrary getLibrary() {
        return library;
    }

    /**
     * Tell Flügel members where their library is when they receive their role.
     *
     * @param role Role that has just been revealed
     */
    public void informFlugelAboutLibrary(Role role) {
        Player player = role.getPlayer();
        Location location = library.getLocation();
        if (player == null || location == null || role.getRoleType().getFaction() != FactionType.FLUGEL) {
            return;
        }
        MessageUtil.sendMessage(player, "&dLa bibliothèque des Flügel (une seule utilisation par partie) se trouve en &fX "
                + location.getBlockX() + ", Y " + location.getBlockY() + ", Z " + location.getBlockZ() + "&d.");
    }

    // ------------------------------------------------------------------ queries

    /**
     * Get the number of players in a faction
     *
     * @param factionType Faction type
     * @return Number of players
     */
    public int getFactionMemberCount(FactionType factionType) {
        return factionMemberCounts.getOrDefault(factionType, 0);
    }

    /**
     * Update faction member counts
     */
    public void updateFactionMemberCounts() {
        for (FactionType type : FactionType.values()) {
            factionMemberCounts.put(type, 0);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
            if (ngnlPlayer != null && ngnlPlayer.getFaction() != null) {
                factionMemberCounts.merge(ngnlPlayer.getFaction(), 1, Integer::sum);
            }
        }
    }

    /**
     * Check if two players are in the same faction
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     * @return True if in the same faction
     */
    public boolean areSameFaction(UUID player1Id, UUID player2Id) {
        NGNLPlayer first = plugin.getPlayerManager().getNGNLPlayer(player1Id);
        NGNLPlayer second = plugin.getPlayerManager().getNGNLPlayer(player2Id);
        return first != null && second != null && first.getFaction() != null && first.getFaction() == second.getFaction();
    }
}
