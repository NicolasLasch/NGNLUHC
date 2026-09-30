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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holou: at the start he may hide the names and roles of the dead (only he sees them) in exchange
 * for 2 hearts, once per game he swaps two players with /teleport, and in the finale his TP Stick
 * teleports him 30 blocks from a chosen player (5 seconds of blindness). Immune to Jibril's catastrophes.
 */
public class HolouRole extends Role {

    /** Cooldown of the TP Stick in seconds. */
    private static final int TP_COOLDOWN = 10 * 60;
    /** Distance (blocks) at which Holou appears from his target. */
    private static final double TP_DISTANCE = 30.0;
    /** Duration of the blindness after a teleport in seconds. */
    private static final int BLINDNESS_SECONDS = 5;
    /** Hearts paid to hide the dead. */
    private static final double HIDE_DEAD_COST = 2.0;

    private boolean swapUsed = false;
    private boolean hidesDeaths = false;
    private boolean hideChoiceMade = false;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (HOLOU)
     */
    public HolouRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        giveItem(player, buildRoleItem(Material.BLACK_DYE, "&8&lVoile des morts",
                "&7Cache les pseudos et rôles des morts à tous les joueurs", "&7(toi seul les verras) contre 2 cœurs permanents.", "",
                "&eAccroupi + clic droit pour accepter", "&cChoix unique et définitif"));
        MessageUtil.sendMessage(player, "&eUtilise &a/teleport <joueur1> <joueur2> &epour échanger la place de deux joueurs (une fois).");
    }

    // ------------------------------------------------------------------ hide the dead

    /**
     * Accept or ignore the Veil of the Dead (sneak + right-click).
     *
     * @return True if the choice was made
     */
    private boolean chooseToHideDeaths() {
        Player player = getPlayer();
        if (player == null || hideChoiceMade) {
            return false;
        }
        if (!player.isSneaking()) {
            MessageUtil.sendMessage(player, "&eAccroupis-toi et fais un clic droit pour confirmer : tu perdras 2 cœurs permanents.");
            return false;
        }
        hideChoiceMade = true;
        hidesDeaths = true;
        addMaxHearts(-HIDE_DEAD_COST);
        player.getInventory().remove(player.getInventory().getItemInMainHand());
        MessageUtil.sendMessage(player, "&8Les pseudos et rôles des morts ne seront plus révélés qu'à toi (−2 cœurs).");
        return true;
    }

    /**
     * Whether Holou chose to hide the dead.
     *
     * @return True if deaths are hidden from the other players
     */
    public boolean hidesDeaths() {
        return hidesDeaths;
    }

    // ------------------------------------------------------------------ swap players

    /**
     * Swap the positions of two players and give each a random enchanted book (once per game).
     *
     * @param first  First player
     * @param second Second player
     * @return True if the swap happened
     */
    public boolean swapPlayers(Player first, Player second) {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        if (swapUsed) {
            MessageUtil.sendMessage(player, "&cTu as déjà utilisé ton échange pendant cette partie.");
            return false;
        }
        if (first.equals(second) || !isAliveInGame(first) || !isAliveInGame(second)) {
            MessageUtil.sendMessage(player, "&cLes deux joueurs doivent être différents et encore en vie.");
            return false;
        }

        Location firstSpot = first.getLocation().clone();
        first.teleport(second.getLocation());
        second.teleport(firstSpot);
        swapUsed = true;

        plugin.getMiniGameEngine().giveRandomRewardBook(first.getUniqueId());
        plugin.getMiniGameEngine().giveRandomRewardBook(second.getUniqueId());
        MessageUtil.broadcast("&5Holou a échangé la place de " + first.getName() + " et " + second.getName() + " !");
        return true;
    }

    /**
     * Check that a player is alive in the current game.
     *
     * @param target Player to check
     * @return True if alive
     */
    private boolean isAliveInGame(Player target) {
        return plugin.getGameManager().isPlayerAlive(target.getUniqueId());
    }

    // ------------------------------------------------------------------ TP stick

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("tp_stick");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.BLAZE_ROD, "&5&lBâton de téléportation",
                "&7Te téléporte à 30 blocs du joueur de ton choix.", "&7Tu es aveuglé 5 secondes.", "",
                "&eClic droit pour activer", "&cRecharge : 10 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.BLACK_DYE) {
            return chooseToHideDeaths();
        }
        return item.getType() == Material.BLAZE_ROD && openTeleportPicker();
    }

    /**
     * Ask Holou which player to teleport next to.
     *
     * @return True if the picker was opened
     */
    private boolean openTeleportPicker() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        plugin.getPlayerPicker().open(player, "Bâton de téléportation - vers qui ?",
                plugin.getPlayerPicker().aliveCandidates(player), this::teleportNear);
        return true;
    }

    /**
     * Teleport Holou at about 30 blocks from the chosen player and blind him.
     *
     * @param targetId UUID of the chosen player
     */
    private void teleportNear(UUID targetId) {
        Player player = getPlayer();
        Player target = Bukkit.getPlayer(targetId);
        if (player == null || target == null || !tryUseCooldown("tp_stick", TP_COOLDOWN)) {
            return;
        }
        player.teleport(findSpotAround(target.getLocation()));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLINDNESS_SECONDS * 20, 0, false, false));
        MessageUtil.sendMessage(player, "&5Tu apparais à environ 30 blocs de " + target.getName() + ".");
    }

    /**
     * Find a safe spot about 30 blocks away from a location, inside the world border.
     *
     * @param center Location of the target
     * @return A location on the surface around the target
     */
    private Location findSpotAround(Location center) {
        Location best = center;
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            int x = (int) (center.getX() + Math.cos(angle) * TP_DISTANCE);
            int z = (int) (center.getZ() + Math.sin(angle) * TP_DISTANCE);
            Location candidate = new Location(center.getWorld(), x + 0.5, 0, z + 0.5);
            if (!center.getWorld().getWorldBorder().isInside(candidate)) {
                continue;
            }
            candidate.setY(center.getWorld().getHighestBlockYAt(x, z) + 1);
            return candidate;
        }
        return best;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Holou.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "At the start you may hide the names and roles of the dead (only you see them)",
                "in exchange for 2 hearts. /teleport <p1> <p2> swaps two players once."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "TP Stick: teleports you 30 blocks from a chosen player (5 seconds of blindness,",
                "every 10 minutes). You take no damage from Jibril's catastrophes."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
