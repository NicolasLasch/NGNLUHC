package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.CombatRestrictions;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Think Nirvalen: wins alone, knows where the Aka Si Anse is, gains a heart per kill and, in the
 * finale, can use two one-shot rites: /rite 1 (invisibility + flight for 2 minutes, ended by any
 * damage given or taken) and /rite 2 (Levitation 20 on a player for 5 seconds).
 */
public class ThinkRole extends Role implements CombatRestrictions {

    /** Duration of the rupture rite in seconds. */
    private static final int RITE_ONE_SECONDS = 2 * 60;
    /** Duration of the levitation in seconds. */
    private static final int RITE_TWO_SECONDS = 5;
    /** Range (blocks) of the levitation rite. */
    private static final double RITE_TWO_RANGE = 20.0;

    private boolean riteOneUsed = false;
    private boolean riteTwoUsed = false;
    private boolean riteOneActive = false;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (THINK)
     */
    public ThinkRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    public boolean canFormAlliance() {
        return false;
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Location akaSiAnse = plugin.getSpecialItemManager().getAkaSiAnseLocation();
        if (akaSiAnse != null) {
            MessageUtil.sendMessage(player, "&eTu connais la position de l'Aka Si Anse : &fX " + akaSiAnse.getBlockX()
                    + ", Y " + akaSiAnse.getBlockY() + ", Z " + akaSiAnse.getBlockZ());
        } else {
            MessageUtil.sendMessage(player, "&eL'Aka Si Anse n'est pas (encore) apparu dans le monde.");
        }
        MessageUtil.sendMessage(player, "&eChaque kill te donne 1 cœur supplémentaire. Tu gagnes seul !");
    }

    @Override
    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || killerId == null || !killerId.equals(playerId)) {
            return;
        }
        addMaxHearts(1);
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2.0));
        MessageUtil.sendMessage(player, "&aTon kill te donne 1 cœur supplémentaire.");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        MessageUtil.sendMessage(player, "&eTes rites se lancent par commande : &a/rite 1 &e(invisible + vol, 2 min) et &a/rite 2 &e(lévitation). Une utilisation chacun.");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return false;
    }

    // ------------------------------------------------------------------ rites

    /**
     * Rite of rupture: invisible and flying for 2 minutes, ended by the first damage given or taken.
     * Think has no fall protection.
     *
     * @return True if the rite was used
     */
    public boolean useStealthRite() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        if (riteOneUsed) {
            MessageUtil.sendMessage(player, "&cTu as déjà utilisé le rite 1.");
            return false;
        }
        riteOneUsed = true;
        riteOneActive = true;
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, RITE_ONE_SECONDS * 20, 0, false, false));
        player.setAllowFlight(true);
        player.setFlying(true);
        MessageUtil.sendMessage(player, "&aRite de rupture activé pour 2 minutes (attention aux dégâts de chute !).");
        runLater(this::endStealthRite, RITE_ONE_SECONDS * 20L);
        return true;
    }

    /**
     * End the rupture rite (time elapsed or damage involved).
     */
    private void endStealthRite() {
        if (!riteOneActive) {
            return;
        }
        riteOneActive = false;
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setFlying(false);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setAllowFlight(false);
        }
        MessageUtil.sendMessage(player, "&eLe rite de rupture prend fin.");
    }

    @Override
    public void onCombatInvolvement() {
        endStealthRite();
    }

    /**
     * Second rite: Levitation 20 on the nearest player for 5 seconds.
     *
     * @return True if the rite was used
     */
    public boolean useLevitationRite() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        if (riteTwoUsed) {
            MessageUtil.sendMessage(player, "&cTu as déjà utilisé le rite 2.");
            return false;
        }
        Player target = nearestEnemy(RITE_TWO_RANGE);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cAucune cible à moins de 20 blocs.");
            return false;
        }
        riteTwoUsed = true;
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, RITE_TWO_SECONDS * 20, 19, false, false));
        MessageUtil.sendMessage(player, "&aTu as frappé " + target.getName() + " de lévitation.");
        MessageUtil.sendMessage(target, "&cThink Nirvalen a utilisé un rite sur toi !");
        return true;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        riteOneActive = false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Think Nirvalen.",
                "Your goal is to win alone (no alliance).",
                "You know the position of the Aka Si Anse from the start.",
                "Each kill gives you 1 extra heart."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "/rite 1: invisible and flying for 2 minutes (ends on any damage given or taken).",
                "/rite 2: Levitation 20 on the nearest player for 5 seconds.",
                "Each rite can only be used once."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game alone.";
    }
}
