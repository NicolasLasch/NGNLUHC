package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ivan: knows Riku and Nonna without knowing who is who (10% chance of a wrong name),
 * gets a knockback stick in Sumo and, in the finale, an iron helmet showing footprints.
 */
public class IvanRole extends DuoRole {

    /** Percent chance that one of the two announced players is wrong. */
    private static final int WRONG_INFO_CHANCE = 10;
    /** Time (ms) footprints stay visible. */
    private static final long FOOTPRINT_LIFETIME_MS = 10_000L;
    /** Radius (blocks) in which Ivan perceives footprints. */
    private static final double FOOTPRINT_RADIUS = 40.0;
    /** Minimum distance (blocks) between two footprints of the same player. */
    private static final double FOOTPRINT_SPACING = 0.9;

    private final Map<UUID, Deque<Footprint>> footprints = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (IVAN)
     */
    public IvanRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        List<Player> shown = buildAnnouncedPlayers();
        Collections.shuffle(shown);
        MessageUtil.sendMessage(player, "&eParmi ces joueurs se trouvent Riku et Nonna (tu ne sais pas qui est qui) :");
        shown.forEach(target -> MessageUtil.sendMessage(player, "&f- " + target.getName()));
    }

    /**
     * Build the two announced players (Riku and Nonna), with a 10% risk that one is wrong.
     *
     * @return Announced players
     */
    private List<Player> buildAnnouncedPlayers() {
        List<Player> shown = new ArrayList<>();
        addIfOnline(shown, getPartnerUUID());
        addIfOnline(shown, plugin.getRoleManager().getPlayerByRole(RoleType.RIKU));

        if (shown.size() == 2 && ThreadLocalRandom.current().nextInt(100) < WRONG_INFO_CHANCE) {
            Player impostor = pickPlayerNotIn(shown);
            if (impostor != null) {
                shown.set(ThreadLocalRandom.current().nextInt(2), impostor);
            }
        }
        return shown;
    }

    /**
     * Add an online player to a list.
     *
     * @param list     Target list
     * @param playerId UUID of the player (can be null)
     */
    private void addIfOnline(List<Player> list, UUID playerId) {
        Player online = playerId != null ? Bukkit.getPlayer(playerId) : null;
        if (online != null) {
            list.add(online);
        }
    }

    /**
     * Pick a random online player who is neither Ivan nor in the given list.
     *
     * @param excluded Players to avoid
     * @return A random player or null
     */
    private Player pickPlayerNotIn(List<Player> excluded) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean avoid = online.getUniqueId().equals(playerId)
                    || excluded.stream().anyMatch(p -> p.getUniqueId().equals(online.getUniqueId()));
            if (!avoid && plugin.getGameManager().isPlayerAlive(online.getUniqueId())) {
                candidates.add(online);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player != null && miniGameType == MiniGameType.SUMO) {
            MessageUtil.sendMessage(player, "&eTu recevras un bâton de recul (1 utilisation) à chaque manche de Sumo.");
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        runRepeating(this::recordFootprints, 10L, 10L);
        runRepeating(this::displayFootprints, 5L, 5L);
    }

    // ------------------------------------------------------------------ footprints

    /**
     * Check whether Ivan currently wears the ID Helmet.
     *
     * @param player Ivan
     * @return True if the ID Helmet is on his head
     */
    private boolean wearsIdHelmet(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null && helmet.getType() == Material.IRON_HELMET && helmet.hasItemMeta()
                && helmet.getItemMeta().getPersistentDataContainer()
                .has(plugin.getNamespacedKey("role_item"), org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * Remember where nearby players walked (only while the ID Helmet is worn).
     */
    private void recordFootprints() {
        Player ivan = getPlayer();
        if (ivan == null || !isAlive() || !wearsIdHelmet(ivan)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player target : nearbyAlivePlayers(ivan.getLocation(), FOOTPRINT_RADIUS)) {
            recordFootprint(target, now);
        }
    }

    /**
     * Add a footprint for a player if he moved enough since the previous one.
     *
     * @param target Player walking
     * @param now    Current time in ms
     */
    private void recordFootprint(Player target, long now) {
        if (!target.isOnGround()) {
            return;
        }
        Deque<Footprint> trail = footprints.computeIfAbsent(target.getUniqueId(), id -> new ArrayDeque<>());
        Location feet = target.getLocation().clone();
        feet.setY(Math.floor(feet.getY()) + 0.05);
        Footprint last = trail.peekLast();
        if (last == null || !last.location.getWorld().equals(feet.getWorld())
                || last.location.distance(feet) >= FOOTPRINT_SPACING) {
            trail.addLast(new Footprint(feet, now));
        }
    }

    /**
     * Show the recent footprints to Ivan and forget the old ones (10 seconds).
     */
    private void displayFootprints() {
        Player ivan = getPlayer();
        if (ivan == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean visible = isAlive() && wearsIdHelmet(ivan);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 170, 40), 1.2f);

        for (Deque<Footprint> trail : footprints.values()) {
            trail.removeIf(print -> now - print.timestamp > FOOTPRINT_LIFETIME_MS);
            if (!visible) {
                continue;
            }
            for (Footprint print : trail) {
                if (print.location.getWorld().equals(ivan.getWorld())) {
                    ivan.spawnParticle(Particle.DUST, print.location, 2, 0.12, 0, 0.12, 0, dust);
                }
            }
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack helmet = buildRoleItem(Material.IRON_HELMET, "&7&lID Helmet",
                "&7Équipé, il révèle les traces de pas des joueurs", "&7(elles s'effacent après 10 secondes).", "",
                "&eÉquipe-le pour l'activer");
        helmet.addUnsafeEnchantment(Enchantment.PROTECTION, 1);
        giveItem(player, helmet);
        MessageUtil.sendMessage(player, "&aTu as reçu l'ID Helmet (fer, Protection I). Équipe-le à la place de ton casque.");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return false;
    }

    @Override
    public void onDeath(UUID killerId) {
        super.onDeath(killerId);
        footprints.clear();
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Ivan Zell.",
                "Your goal is to win with Nonna.",
                "In Sumo you receive a Knockback stick (one use per round).",
                "You know Riku and Nonna but not who is who (10% risk of wrong info)."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You get the ID Helmet (iron, Protection I).",
                "When worn it reveals the footprints of nearby players for 10 seconds."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Nonna.";
    }

    /**
     * A footprint left by a player.
     */
    private static final class Footprint {
        private final Location location;
        private final long timestamp;

        private Footprint(Location location, long timestamp) {
            this.location = location;
            this.timestamp = timestamp;
        }
    }
}
