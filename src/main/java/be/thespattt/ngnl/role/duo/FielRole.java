package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Fiel: creates illusions (a decoy clone of herself, once per episode, that she can swap places
 * with once per game) and, in the finale, disguises as another player for 30 seconds.
 */
public class FielRole extends DuoRole {

    /** Lifetime of an illusion in seconds. */
    private static final int ILLUSION_SECONDS = 120;
    /** Maximum distance (blocks) at which the illusion can be placed. */
    private static final int ILLUSION_RANGE = 30;
    /** Cooldown of the disguise in seconds. */
    private static final int DISGUISE_COOLDOWN = 15 * 60;
    /** Duration of the disguise in seconds. */
    private static final int DISGUISE_SECONDS = 30;

    private int lastIllusionEpisode = -1;
    private boolean swapUsed = false;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (FIEL)
     */
    public FielRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Player chlammy = getPartnerPlayer();
        if (chlammy != null) {
            MessageUtil.sendMessage(player, "&eChlammy est : &a" + chlammy.getName());
        }
        trackPartnerWithArrow("Chlammy");
        giveItem(player, buildRoleItem(Material.AMETHYST_SHARD, "&d&lÉclat d'illusion",
                "&7Clic droit : place une illusion de toi là où tu regardes", "&7(une fois par épisode).",
                "&7Clic droit en étant accroupi : échange ta place avec elle", "&7(une fois par partie)."));
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("disguise");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.CARVED_PUMPKIN, "&d&lMasque d'illusion",
                "&7Clic droit : choisis un joueur dont tu prends l'apparence", "&7pendant 30 secondes.", "",
                "&cRecharge : 15 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.AMETHYST_SHARD) {
            return useIllusionShard();
        }
        if (item.getType() == Material.CARVED_PUMPKIN) {
            return useDisguise();
        }
        return false;
    }

    // ------------------------------------------------------------------ mining phase: illusion

    /**
     * Place an illusion (or swap with it when sneaking).
     *
     * @return True if something happened
     */
    private boolean useIllusionShard() {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        return player.isSneaking() ? swapWithIllusion(player) : placeIllusion(player);
    }

    /**
     * Place a decoy clone where the player looks (once per episode).
     *
     * @param player Fiel
     * @return True if the illusion was placed
     */
    private boolean placeIllusion(Player player) {
        int episode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        if (lastIllusionEpisode == episode) {
            MessageUtil.sendMessage(player, "&cTu as déjà créé ton illusion pendant cet épisode.");
            return false;
        }

        Block target = player.getTargetBlockExact(ILLUSION_RANGE);
        Location spot = target != null ? target.getLocation().add(0.5, 1, 0.5) : player.getLocation();
        spot.setYaw(player.getLocation().getYaw());
        if (plugin.getCloneManager().placeDecoy(player, spot, ILLUSION_SECONDS) == null) {
            MessageUtil.sendMessage(player, "&cImpossible de créer l'illusion.");
            return false;
        }

        lastIllusionEpisode = episode;
        MessageUtil.sendMessage(player, "&aIllusion créée ! Clic droit accroupi pour échanger ta place avec elle (une fois par partie).");
        return true;
    }

    /**
     * Swap places with the illusion (once per game).
     *
     * @param player Fiel
     * @return True if the swap happened
     */
    private boolean swapWithIllusion(Player player) {
        if (swapUsed) {
            MessageUtil.sendMessage(player, "&cTu as déjà échangé ta place avec une illusion.");
            return false;
        }
        if (!plugin.getCloneManager().swapWithDecoy(player)) {
            MessageUtil.sendMessage(player, "&cTu n'as pas d'illusion active.");
            return false;
        }
        swapUsed = true;
        MessageUtil.sendMessage(player, "&aTu as échangé ta place avec ton illusion !");
        return true;
    }

    // ------------------------------------------------------------------ finale: disguise

    /**
     * Ask which player to imitate, then disguise Fiel as him.
     *
     * @return True if the picker was opened
     */
    private boolean useDisguise() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        plugin.getPlayerPicker().open(player, "Masque d'illusion - qui imiter ?",
                plugin.getPlayerPicker().aliveCandidates(player), this::disguiseAs);
        return true;
    }

    /**
     * Disguise as the chosen player for 30 seconds.
     *
     * @param targetId UUID of the player to imitate
     */
    private void disguiseAs(UUID targetId) {
        Player player = getPlayer();
        Player target = Bukkit.getPlayer(targetId);
        if (player == null || target == null || !tryUseCooldown("disguise", DISGUISE_COOLDOWN)) {
            return;
        }
        if (!plugin.getDisguiseService().disguise(player, target)) {
            resetCooldown("disguise");
            MessageUtil.sendMessage(player, "&cLe déguisement a échoué.");
            return;
        }
        MessageUtil.sendMessage(player, "&aTu ressembles à " + target.getName() + " pendant " + DISGUISE_SECONDS + " secondes.");
        runLater(() -> endDisguise(player), DISGUISE_SECONDS * 20L);
    }

    /**
     * End the disguise.
     *
     * @param player Fiel
     */
    private void endDisguise(Player player) {
        plugin.getDisguiseService().restore(player);
        MessageUtil.sendMessage(player, "&eTon déguisement prend fin.");
    }

    @Override
    public void cleanup() {
        super.cleanup();
        Player player = getPlayer();
        if (player != null) {
            plugin.getDisguiseService().restore(player);
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Fiel.",
                "Your goal is to win with Chlammy.",
                "You know Chlammy's identity and position from the start.",
                "Once per episode you can create an illusion of yourself where you look,",
                "and once per game you can swap places with it."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You can disguise as another player for 30 seconds (every 15 minutes)."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Chlammy.";
    }
}
