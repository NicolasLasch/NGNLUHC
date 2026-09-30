package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Chlammy: reads the inventory of a player once per episode and, in the finale, anticipates the
 * movements of a nearby opponent (Slowness for 20 seconds).
 */
public class ChlammyRole extends DuoRole {

    /** Cooldown of the Prediction Orb in seconds. */
    private static final int PREDICTION_COOLDOWN = 15 * 60;
    /** Duration of the Slowness effect in seconds. */
    private static final int SLOWNESS_SECONDS = 20;
    /** Range (blocks) in which an opponent can be predicted. */
    private static final double PREDICTION_RANGE = 30.0;

    private int lastReadEpisode = -1;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (CHLAMMY)
     */
    public ChlammyRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Player fiel = getPartnerPlayer();
        if (fiel != null) {
            MessageUtil.sendMessage(player, "&eFiel est : &a" + fiel.getName());
        }
        trackPartnerWithArrow("Fiel");
        giveItem(player, buildRoleItem(Material.BOOK, "&b&lLecture d'esprit",
                "&7Clic droit : choisis un joueur et découvre son inventaire", "&7(une fois par épisode)."));
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("prediction");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.ENDER_PEARL, "&b&lOrbe de prédiction",
                "&7Ralentit un adversaire proche pendant 20 secondes.", "",
                "&eClic droit pour activer", "&cRecharge : 15 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.BOOK) {
            return openMindReadingPicker();
        }
        if (item.getType() == Material.ENDER_PEARL) {
            return usePrediction();
        }
        return false;
    }

    // ------------------------------------------------------------------ mind reading

    /**
     * Ask which player's mind to read (once per episode).
     *
     * @return True if the picker was opened
     */
    private boolean openMindReadingPicker() {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        int episode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        if (lastReadEpisode == episode) {
            MessageUtil.sendMessage(player, "&cTu as déjà lu un esprit pendant cet épisode.");
            return false;
        }
        plugin.getPlayerPicker().open(player, "Lecture d'esprit - qui lire ?",
                plugin.getPlayerPicker().aliveCandidates(player), this::readMind);
        return true;
    }

    /**
     * Show the inventory of the chosen player.
     *
     * @param targetId UUID of the chosen player
     */
    private void readMind(UUID targetId) {
        Player player = getPlayer();
        Player target = Bukkit.getPlayer(targetId);
        if (player == null || target == null) {
            return;
        }
        lastReadEpisode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        MessageUtil.sendMessage(player, "&bInventaire de " + target.getName() + " :");
        for (ItemStack stack : target.getInventory().getContents()) {
            if (stack != null && stack.getType() != Material.AIR) {
                MessageUtil.sendMessage(player, "&7- &f" + describeStack(stack));
            }
        }
    }

    /**
     * Describe an item stack for the inventory report.
     *
     * @param stack Item stack
     * @return "Name xAmount"
     */
    private String describeStack(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : stack.getType().name();
        return name + " &7x" + stack.getAmount();
    }

    // ------------------------------------------------------------------ prediction

    /**
     * Slow the nearest opponent for 20 seconds.
     *
     * @return True if the orb was used
     */
    private boolean usePrediction() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        Player target = nearestEnemy(PREDICTION_RANGE);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cAucun adversaire à portée.");
            return false;
        }
        if (!tryUseCooldown("prediction", PREDICTION_COOLDOWN)) {
            return false;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOWNESS_SECONDS * 20, 1, false, false));
        MessageUtil.sendMessage(player, "&aTu as anticipé les mouvements de " + target.getName() + ".");
        MessageUtil.sendMessage(target, "&cChlammy a anticipé tes mouvements.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Chlammy.",
                "Your goal is to win with Fiel.",
                "You know Fiel's identity and position from the start.",
                "Once per episode you can read another player's mind to see his inventory."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Prediction Orb: anticipate an opponent's movements, Slowness for 20 seconds",
                "(every 15 minutes)."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Fiel.";
    }
}
