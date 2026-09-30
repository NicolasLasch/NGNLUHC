package be.thespattt.ngnl.item;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.item.structure.AkaSiAnseDungeon;
import be.thespattt.ngnl.item.structure.StructureUtil;
import be.thespattt.ngnl.item.structure.SuniasterCloud;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.player.faction.FactionType;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the items hidden in the mining world:
 * - the Aka Si Anse, kept by Nina Clive in a dungeon (coordinates announced between 50 and 80 minutes);
 * - the Suniaster, lying on a cloud near the spawn (the first Old Deus to take it gets 15 hearts).
 * It also applies the behaviour of every special item through {@link SpecialItemEffects}.
 */
public class SpecialItemManager {

    /** Margin (blocks) applied to the coordinates announced in the chat for the Aka Si Anse. */
    private static final int ANNOUNCE_MARGIN = 50;
    /** Distance range (blocks) from the center at which the dungeon is built. */
    private static final double DUNGEON_MIN_DISTANCE = 150;
    /** Distance range (blocks) from the spawn at which the cloud floats. */
    private static final double CLOUD_MAX_DISTANCE = 125;
    /** Hearts given by the Suniaster. */
    private static final double SUNIASTER_HEARTS = 15.0;
    /** Hearts left to a player killed by Nina Clive. */
    private static final double NINA_PENALTY_HEARTS = 5.0;

    private final NoGameNoLife plugin;
    private final SpecialItemEffects effects;
    private final AkaSiAnseDungeon dungeon;
    private final SuniasterCloud cloud;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean suniasterClaimed = false;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public SpecialItemManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.effects = new SpecialItemEffects(plugin);
        this.dungeon = new AkaSiAnseDungeon(plugin);
        this.cloud = new SuniasterCloud(plugin);
    }

    /**
     * Get the behaviour handler of the consumable special items.
     *
     * @return The effects handler
     */
    public SpecialItemEffects getEffects() {
        return effects;
    }

    // ------------------------------------------------------------------ game lifecycle

    /**
     * Build the hidden structures of the mining world and schedule the Aka Si Anse announcement.
     * Called when the game starts.
     */
    public void prepareGame() {
        cleanup();
        World world = plugin.getWorldManager().getMiningWorld();
        if (world == null) {
            return;
        }
        suniasterClaimed = false;
        Location center = world.getWorldBorder().getCenter();
        double radius = world.getWorldBorder().getSize() / 2.0;

        if (plugin.getConfigManager().getGameConfig().isItemEnabled("aka_si_anse")) {
            dungeon.build(StructureUtil.randomSurfaceLocation(world, center.getX(), center.getZ(),
                    Math.min(DUNGEON_MIN_DISTANCE, radius * 0.3), radius * 0.6));
            scheduleAkaSiAnseAnnouncement();
        }
        if (plugin.getConfigManager().getGameConfig().isItemEnabled("suniaster")) {
            Location surface = StructureUtil.randomSurfaceLocation(world, center.getX(), center.getZ(), 20, CLOUD_MAX_DISTANCE);
            cloud.build(surface, plugin.getItemManager().getSpecialItem("suniaster").clone());
        }
    }

    /**
     * Schedule the chat announcement of the approximate Aka Si Anse position
     * (between 50 and 80 minutes with the default 60 minutes setting).
     */
    private void scheduleAkaSiAnseAnnouncement() {
        int base = plugin.getConfigManager().getGameConfig().getAkaSiAnseAppearTime();
        int minMinutes = Math.max(1, base - 10);
        int maxMinutes = Math.max(minMinutes, base + 20);
        int minutes = ThreadLocalRandom.current().nextInt(minMinutes, maxMinutes + 1);
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, this::announceAkaSiAnse, minutes * 60L * 20L));
    }

    /**
     * Announce the position of the Aka Si Anse (accurate to 50 blocks).
     */
    private void announceAkaSiAnse() {
        Location exact = dungeon.getCenter();
        if (exact == null || plugin.getGameManager().getGameState() != GameState.MINING_PHASE) {
            return;
        }
        int x = exact.getBlockX() + ThreadLocalRandom.current().nextInt(-ANNOUNCE_MARGIN, ANNOUNCE_MARGIN + 1);
        int z = exact.getBlockZ() + ThreadLocalRandom.current().nextInt(-ANNOUNCE_MARGIN, ANNOUNCE_MARGIN + 1);
        MessageUtil.broadcast("&c&l=== AKA SI ANSE ===");
        MessageUtil.broadcast("&cNina Clive, l'elfe gardienne de l'Aka Si Anse, a été repérée vers X: " + x + ", Z: " + z + " (à 50 blocs près).");
        MessageUtil.broadcast("&cMéfiez-vous : fragile, mais d'une puissance sans nom !");
    }

    /**
     * Remove the structures' entities and stop every task (game end).
     */
    public void cleanup() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        dungeon.remove();
        cloud.remove();
        effects.cleanup();
    }

    // ------------------------------------------------------------------ knowledge given to roles

    /**
     * Exact location of the dungeon (known by Think Nirvalen).
     *
     * @return Dungeon center, or null if there is none
     */
    public Location getAkaSiAnseLocation() {
        return dungeon.getCenter();
    }

    /**
     * Exact location of the Suniaster cloud (known by the Old Deus).
     *
     * @return Cloud center, or null if there is none
     */
    public Location getSuniasterLocation() {
        return cloud.getCenter();
    }

    /**
     * Tell the Old Deus where the Suniaster is when they receive their role.
     *
     * @param role Role that has just been revealed
     */
    public void informGodsAboutSuniaster(Role role) {
        Player player = role.getPlayer();
        Location location = getSuniasterLocation();
        if (player == null || location == null || role.getRoleType().getFaction() != FactionType.OLD_DEUS) {
            return;
        }
        MessageUtil.sendMessage(player, "&eEn tant que dieu, tu sens le Suniaster sur un nuage en &fX " + location.getBlockX()
                + ", Y " + location.getBlockY() + ", Z " + location.getBlockZ() + "&e.");
    }

    // ------------------------------------------------------------------ Suniaster

    /**
     * Try to claim the Suniaster: the first Old Deus who does gets 15 hearts for the rest of the game.
     *
     * @param player Player holding the Suniaster
     * @param item   The Suniaster item stack
     */
    public void claimSuniaster(Player player, ItemStack item) {
        NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        boolean isGod = data != null && data.getRole() != null && data.getRole().getRoleType().getFaction() == FactionType.OLD_DEUS;
        if (!isGod) {
            MessageUtil.sendMessage(player, "&cSeuls les dieux peuvent s'approprier le Suniaster. Brûle-le pour qu'aucun dieu ne l'obtienne !");
            return;
        }
        if (suniasterClaimed) {
            MessageUtil.sendMessage(player, "&cUn autre dieu s'est déjà approprié le Suniaster.");
            return;
        }
        if (!effects.consumeOne(player, "suniaster")) {
            return;
        }
        suniasterClaimed = true;
        data.setMaxHealth(SUNIASTER_HEARTS * 2);
        player.setHealth(player.getMaxHealth());
        MessageUtil.sendMessage(player, "&e&lLe Suniaster est à toi : 15 cœurs pour le reste de la partie !");
        MessageUtil.broadcast("&eLe Suniaster a trouvé son maître parmi les dieux...");
    }

    /**
     * Whether the Suniaster was already claimed by a god.
     *
     * @return True if claimed
     */
    public boolean isSuniasterClaimed() {
        return suniasterClaimed;
    }

    // ------------------------------------------------------------------ Nina Clive

    /**
     * Check whether an entity is Nina Clive.
     *
     * @param entity Entity to check
     * @return True for Nina Clive
     */
    public boolean isNina(org.bukkit.entity.Entity entity) {
        return dungeon.isNina(entity);
    }

    /**
     * Nina was killed: stop her attacks and announce it.
     */
    public void onNinaDefeated() {
        dungeon.stop();
        MessageUtil.broadcast("&5Nina Clive a été vaincue ! L'Aka Si Anse est tombée.");
    }

    /**
     * A player was "killed" by Nina Clive: instead of dying he is teleported away and keeps only
     * 5 hearts (equivalent to losing a mini-game).
     *
     * @param victim Player hit by the lethal blow
     */
    public void punishPlayerKilledByNina(Player victim) {
        NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(victim.getUniqueId());
        if (data != null) {
            data.setMaxHealth(Math.min(data.getMaxHealth(), NINA_PENALTY_HEARTS * 2));
        }
        victim.setHealth(victim.getMaxHealth());
        Location spawn = plugin.getWorldManager().getRandomSpawnLocation(be.thespattt.ngnl.game.world.WorldType.MINING);
        if (spawn != null) {
            victim.teleport(spawn);
        }
        MessageUtil.sendMessage(victim, "&cNina Clive t'a terrassé ! Tu es téléporté et il ne te reste que 5 cœurs.");
    }
}
