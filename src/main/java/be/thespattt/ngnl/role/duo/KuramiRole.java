package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kurami: second life in Des a Coudre and Bloc Party; Oracle Card in the finale
 * (swap two players that are not fighting, 20% risk of being swapped instead of one of them).
 */
public class KuramiRole extends KuramiFeelBase {

    /** Cooldown of the Oracle Card in seconds. */
    private static final int ORACLE_COOLDOWN = 20 * 60;
    /** Percent chance that Kurami replaces the first chosen player. */
    private static final int SELF_TELEPORT_CHANCE = 20;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (KURAMI)
     */
    public KuramiRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected Set<MiniGameType> secondLifeGames() {
        return EnumSet.of(MiniGameType.DES_A_COUDRE, MiniGameType.BLOC_PARTY);
    }

    @Override
    protected String partnerName() {
        return "Feel";
    }

    @Override
    protected void onRoleSetup() {
        // Kurami does not learn Feel directly: the three-name list is sent with the role.
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("oracle_card");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.PAPER, "&5&lOracle Card",
                "&7Téléporte deux joueurs (hors combat) l'un à l'autre.",
                "&c20% de risque de te téléporter à la place de l'un d'eux.", "",
                "&eClic droit pour activer", "&cRecharge : 20 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !isArenaPhaseActive()) {
            return false;
        }
        openFirstTargetPicker();
        return true;
    }

    /**
     * Ask Kurami for the first player to teleport.
     */
    private void openFirstTargetPicker() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        List<UUID> candidates = eligibleTargets(player, null);
        if (candidates.size() < 2) {
            MessageUtil.sendMessage(player, "&cPas assez de joueurs valides à téléporter.");
            return;
        }
        plugin.getPlayerPicker().open(player, "Oracle Card - 1er joueur", candidates, this::openSecondTargetPicker);
    }

    /**
     * Ask Kurami for the second player to teleport.
     *
     * @param firstId First chosen player
     */
    private void openSecondTargetPicker(UUID firstId) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        plugin.getPlayerPicker().open(player, "Oracle Card - 2e joueur", eligibleTargets(player, firstId),
                secondId -> swapTargets(firstId, secondId));
    }

    /**
     * List the alive players that are not in combat (and not already chosen).
     *
     * @param viewer   Kurami
     * @param excluded Player to leave out (can be null)
     * @return Eligible target UUIDs
     */
    private List<UUID> eligibleTargets(Player viewer, UUID excluded) {
        List<UUID> result = new ArrayList<>();
        for (UUID id : plugin.getPlayerPicker().aliveCandidates(viewer)) {
            if (!id.equals(excluded) && !plugin.getCombatTracker().isInCombat(id)) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * Swap the positions of the two chosen players (20% chance Kurami replaces the first).
     *
     * @param firstId  First chosen player
     * @param secondId Second chosen player
     */
    private void swapTargets(UUID firstId, UUID secondId) {
        Player player = getPlayer();
        Player first = org.bukkit.Bukkit.getPlayer(firstId);
        Player second = org.bukkit.Bukkit.getPlayer(secondId);
        if (player == null || first == null || second == null || !tryUseCooldown("oracle_card", ORACLE_COOLDOWN)) {
            return;
        }

        boolean mishap = ThreadLocalRandom.current().nextInt(100) < SELF_TELEPORT_CHANCE;
        Player swapped = mishap ? player : first;
        Location swappedSpot = swapped.getLocation().clone();
        swapped.teleport(second.getLocation());
        second.teleport(swappedSpot);

        MessageUtil.broadcast("&5Kurami a utilisé une Oracle Card.");
        if (mishap) {
            MessageUtil.sendMessage(player, "&cL'Oracle Card s'est retournée contre toi !");
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Kurami Zell.",
                "Your goal is to win with Feel.",
                "Once per game you can link a mini-game with Feel (/duo together):",
                "if you lose it, both of you fall to 5 hearts.",
                "You have two lives in Des a Coudre and Bloc Party."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You get an Oracle Card: it swaps two players that are not in combat",
                "(every 20 minutes). 20% risk that you are swapped instead of one of them."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Feel.";
    }
}
