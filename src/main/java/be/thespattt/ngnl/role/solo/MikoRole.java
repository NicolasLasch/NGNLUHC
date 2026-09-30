package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Miko: knows Riku, permanently has 12 hearts and, in the finale, owns a Mechanical Eye that
 * rewinds nearby players by 10 seconds. If Ino dies, Izuna may join her camp.
 */
public class MikoRole extends Role {

    /** Cooldown of the eye in seconds. */
    private static final int EYE_COOLDOWN = 20 * 60;
    /** Number of seconds of positions kept for every player. */
    private static final int REWIND_SECONDS = 10;
    /** Radius (blocks) of the rewind. */
    private static final double REWIND_RADIUS = 25.0;
    /** Hearts Miko has during the whole game. */
    private static final double MIKO_HEARTS = 12.0;

    private final Map<UUID, Deque<Location>> snapshots = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (MIKO)
     */
    public MikoRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        setMaxHearts(MIKO_HEARTS);
        UUID rikuId = plugin.getRoleManager().getPlayerByRole(RoleType.RIKU);
        Player riku = rikuId != null ? Bukkit.getPlayer(rikuId) : null;
        if (riku != null) {
            MessageUtil.sendMessage(player, "&eRiku est : &a" + riku.getName());
        }
        runRepeating(this::recordSnapshots, 20L, 20L);
    }

    /**
     * Remember the position of every alive player (last 10 seconds).
     */
    private void recordSnapshots() {
        for (UUID aliveId : plugin.getGameManager().getGame().getAlivePlayers()) {
            Player online = Bukkit.getPlayer(aliveId);
            if (online == null) {
                continue;
            }
            Deque<Location> deque = snapshots.computeIfAbsent(aliveId, id -> new ArrayDeque<>());
            deque.addLast(online.getLocation().clone());
            while (deque.size() > REWIND_SECONDS) {
                deque.removeFirst();
            }
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("mechanical_eye");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.CLOCK, "&b&lŒil mécanique",
                "&7Fait revenir les joueurs proches 10 secondes en arrière.", "",
                "&eClic droit pour activer", "&cRecharge : 20 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.CLOCK && useMechanicalEye();
    }

    /**
     * Teleport every nearby player (Miko excepted) to where he was 10 seconds ago.
     *
     * @return True if the eye was used
     */
    private boolean useMechanicalEye() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("mechanical_eye", EYE_COOLDOWN)) {
            return false;
        }
        for (Player nearby : nearbyAlivePlayers(player.getLocation(), REWIND_RADIUS)) {
            rewind(nearby);
        }
        MessageUtil.broadcast("&bMiko a activé l'Œil mécanique !");
        return true;
    }

    /**
     * Send a player back to his oldest remembered position.
     *
     * @param target Player to rewind
     */
    private void rewind(Player target) {
        Deque<Location> deque = snapshots.get(target.getUniqueId());
        if (deque != null && !deque.isEmpty()) {
            target.teleport(deque.getFirst());
            MessageUtil.sendMessage(target, "&bLe temps revient en arrière...");
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Miko.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You know Riku from the start and permanently have 12 hearts."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Mechanical Eye: rewinds nearby players by 10 seconds (every 20 minutes).",
                "If Ino dies, Izuna can join your camp: Haste II for both of you."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
