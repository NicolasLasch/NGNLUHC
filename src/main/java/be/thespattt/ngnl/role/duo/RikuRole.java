package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Riku: +1 heart per mini-game won (applied by the mini-game listener), revives Schwi after
 * surviving 5 minutes, Strength II in the finale when Schwi is low, /heal to give her HP.
 */
public class RikuRole extends DuoRole {

    /** Time Riku must survive after Schwi's death to revive her (ticks). */
    private static final long REVIVE_DELAY_TICKS = 5L * 60L * 20L;
    /** Hearts both lose when Schwi is revived. */
    private static final double REVIVE_HEART_COST = 3.0;
    /** Health (HP) with which Schwi comes back. */
    private static final double REVIVE_HEALTH = 8.0;
    /** Schwi's health (HP) under which Riku gets Strength II (3 hearts). */
    private static final double SCHWI_LOW_HEALTH = 6.0;
    /** Duration of the Strength II effect in seconds. */
    private static final int STRENGTH_SECONDS = 30;
    /** HP transferred by /heal. */
    private static final double HEAL_TRANSFER = 2.0;

    private BukkitTask reviveTask;
    private long strengthActiveUntil = 0L;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (RIKU)
     */
    public RikuRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&eTu gagnes 1 cœur de plus à chaque mini-jeu remporté.");
        }
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (isWinner && player != null) {
            MessageUtil.sendMessage(player, "&aTon rôle te donne 1 cœur supplémentaire.");
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        runRepeating(this::checkSchwiHealth, 20L, 20L);
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || !partnerId.equals(getPartnerUUID())) {
            return;
        }
        MessageUtil.sendMessage(player, "&cSchwi a été éliminée. Survis 5 minutes pour la ressusciter.");
        cancelReviveTask();
        reviveTask = runLater(() -> reviveSchwi(partnerId), REVIVE_DELAY_TICKS);
    }

    /**
     * Bring Schwi back next to Riku; both lose hearts. Does nothing if Riku died meanwhile.
     *
     * @param schwiId UUID of Schwi
     */
    private void reviveSchwi(UUID schwiId) {
        Player player = getPlayer();
        if (player == null || !isAlive() || plugin.getGameManager().isPlayerAlive(schwiId)) {
            return;
        }
        if (plugin.getGameManager().revivePlayer(schwiId, player.getLocation(), REVIVE_HEALTH)) {
            plugin.getGameManager().removePlayerHearts(playerId, REVIVE_HEART_COST);
            plugin.getGameManager().removePlayerHearts(schwiId, REVIVE_HEART_COST);
            MessageUtil.broadcast("&dRiku a survécu assez longtemps pour ressusciter Schwi.");
        }
    }

    /**
     * Give Strength II for 30 seconds when Schwi falls under 3 hearts (finale).
     */
    private void checkSchwiHealth() {
        Player player = getPlayer();
        Player schwi = getPartnerPlayer();
        if (player == null || schwi == null || !isAlive() || !isPartnerAlive()) {
            return;
        }
        if (schwi.getHealth() < SCHWI_LOW_HEALTH && System.currentTimeMillis() >= strengthActiveUntil) {
            strengthActiveUntil = System.currentTimeMillis() + STRENGTH_SECONDS * 1000L;
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, STRENGTH_SECONDS * 20, 1, false, false));
            MessageUtil.sendMessage(player, "&cSchwi est en danger ! Force II pendant " + STRENGTH_SECONDS + " secondes.");
        }
    }

    /**
     * Give 2 HP to Schwi (/heal, unlimited uses as long as Riku keeps more than 1 heart).
     *
     * @return True if the health was transferred
     */
    public boolean transferHealthToSchwi() {
        Player player = getPlayer();
        Player schwi = getPartnerPlayer();
        if (player == null || schwi == null || !isPartnerAlive()) {
            return false;
        }
        if (player.getHealth() <= HEAL_TRANSFER) {
            MessageUtil.sendMessage(player, "&cIl te faut plus d'un cœur pour transférer de la vie.");
            return false;
        }
        player.setHealth(player.getHealth() - HEAL_TRANSFER);
        schwi.setHealth(Math.min(schwi.getMaxHealth(), schwi.getHealth() + HEAL_TRANSFER));
        MessageUtil.sendMessage(player, "&aTu as transféré 2 PV à Schwi.");
        MessageUtil.sendMessage(schwi, "&aRiku t'a transféré 2 PV.");
        return true;
    }

    /**
     * Cancel the pending revive of Schwi.
     */
    private void cancelReviveTask() {
        if (reviveTask != null) {
            reviveTask.cancel();
            reviveTask = null;
        }
    }

    @Override
    public void onDeath(UUID killerId) {
        super.onDeath(killerId);
        cancelReviveTask();
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        MessageUtil.sendMessage(player, "&eUtilise &a/heal &epour donner 2 PV à Schwi quand tu veux.");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Riku Dola.",
                "Your goal is to win with Schwi.",
                "Whenever you win a mini-game, you gain 1 extra heart.",
                "If you survive 5 minutes after Schwi dies, she revives",
                "next to you and both of you lose 3 hearts."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "If Schwi has less than 3 hearts, you get Strength II for 30 seconds.",
                "You can use /heal to give 2 HP to Schwi (unlimited)."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Schwi.";
    }
}
