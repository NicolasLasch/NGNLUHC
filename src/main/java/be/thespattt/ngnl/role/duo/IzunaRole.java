package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.CombatRestrictions;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.DirectionArrow;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Izuna: blinds her opponent in Bloc Party (applied by the mini-game listener), Speed II and an
 * arrow to Ino in the finale. When Ino dies she gets the clone crown and may join Miko's camp.
 */
public class IzunaRole extends DuoRole implements CombatRestrictions {

    /** Cooldown of the clone crown in seconds. */
    private static final int CROWN_COOLDOWN = 20 * 60;
    /** Number of clones summoned by the crown. */
    private static final int CLONE_AMOUNT = 5;
    /** Lifetime of the clones in seconds. */
    private static final int CLONE_DURATION = 10;
    /** Duration of the Hatsuse Shield protection in seconds. */
    private static final int SHIELD_SECONDS = 30;
    /** Weakness duration paid to join Miko's camp, in seconds. */
    private static final int MIKO_WEAKNESS_SECONDS = 5 * 60;

    private boolean crownUnlocked = false;
    private boolean crownGiven = false;
    private boolean shieldProtected = false;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (IZUNA)
     */
    public IzunaRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        // Blindness in Bloc Party is applied by the mini-game listener; nothing to do at reveal.
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        // Blindness is applied in the mini-game listener.
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        Player player = getPlayer();
        if (player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
        }
        runRepeating(this::showInoArrow, 20L, 20L);
        giveCrownIfUnlocked();
    }

    /**
     * Show an arrow pointing to Ino in the action bar.
     */
    private void showInoArrow() {
        Player player = getPlayer();
        if (player == null || !isAlive()) {
            return;
        }
        Player ino = isPartnerAlive() ? getPartnerPlayer() : null;
        DirectionArrow.show(player, ino, "Ino", 30);
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || !partnerId.equals(getPartnerUUID())) {
            return;
        }
        crownUnlocked = true;
        giveCrownIfUnlocked();
        MessageUtil.sendMessage(player, "&eTa couronne de clones est débloquée.");
        MessageUtil.sendMessage(player, "&eTu peux rejoindre le camp de Miko avec &a/duo joinmiko &e(Faiblesse II pendant 5 minutes).");
    }

    /**
     * Give the crown item once it is unlocked and while the finale is running.
     */
    private void giveCrownIfUnlocked() {
        Player player = getPlayer();
        if (player == null || !crownUnlocked || crownGiven) {
            return;
        }
        crownGiven = true;
        giveItem(player, buildRoleItem(Material.GOLDEN_HELMET, "&6&lCouronne d'Izuna",
                "&75 clones pendant 10 secondes.", "", "&eClic droit pour activer", "&cRecharge : 20 minutes"));
    }

    /**
     * Protect Izuna with the Hatsuse Shield: invincible, invisible and unable to hit anybody.
     */
    public void activateShieldProtection() {
        Player player = getPlayer();
        if (player == null || shieldProtected) {
            return;
        }
        shieldProtected = true;
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, SHIELD_SECONDS * 20, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, SHIELD_SECONDS * 20, 10, false, false));
        MessageUtil.sendMessage(player, "&aIno te protège avec le Bouclier Hatsuse pendant " + SHIELD_SECONDS + " secondes.");
        runLater(() -> shieldProtected = false, SHIELD_SECONDS * 20L);
    }

    @Override
    public boolean isAttackBlocked() {
        return shieldProtected;
    }

    @Override
    public String attackBlockedMessage() {
        return "&cTu ne peux pas attaquer tant que le Bouclier Hatsuse te protège.";
    }

    /**
     * Check whether the Hatsuse Shield is currently protecting Izuna.
     *
     * @return True while the shield is active
     */
    public boolean isProtectedByShield() {
        return shieldProtected;
    }

    /**
     * Join Miko's camp after Ino's death: alliance + Haste II for both, Weakness II for 5 minutes for Izuna.
     */
    public void joinMikoCamp() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        if (isPartnerAlive()) {
            MessageUtil.sendMessage(player, "&cTu ne peux faire cela qu'après la mort d'Ino.");
            return;
        }
        UUID mikoId = plugin.getRoleManager().getPlayerByRole(RoleType.MIKO);
        if (mikoId == null || !plugin.getGameManager().isPlayerAlive(mikoId)) {
            MessageUtil.sendMessage(player, "&cMiko n'est pas disponible.");
            return;
        }

        linkAlliance(mikoId);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, MIKO_WEAKNESS_SECONDS * 20, 1, false, false));
        giveHaste(player);
        Player miko = org.bukkit.Bukkit.getPlayer(mikoId);
        if (miko != null) {
            giveHaste(miko);
            MessageUtil.sendMessage(miko, "&aIzuna a rejoint ton camp : Hâte II pour vous deux.");
        }
        MessageUtil.sendMessage(player, "&eTu as rejoint le camp de Miko : Faiblesse II pendant 5 minutes.");
    }

    /**
     * Create the alliance between Izuna and another player.
     *
     * @param otherId UUID of the new ally
     */
    private void linkAlliance(UUID otherId) {
        NGNLPlayer self = getNGNLPlayer();
        NGNLPlayer other = plugin.getPlayerManager().getNGNLPlayer(otherId);
        if (self != null && other != null) {
            self.setAlliancePartner(otherId);
            other.setAlliancePartner(playerId);
        }
    }

    /**
     * Give Haste II for the rest of the game.
     *
     * @param target Player receiving Haste II
     */
    private void giveHaste(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, 1, false, false));
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        // The crown is only given after Ino's death (see giveCrownIfUnlocked).
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.GOLDEN_HELMET && useCloneAbility();
    }

    /**
     * Summon the clones of the crown.
     *
     * @return True if the clones were summoned
     */
    private boolean useCloneAbility() {
        Player player = getPlayer();
        if (player == null || !crownUnlocked || !isArenaPhaseActive() || !tryUseCooldown("crown", CROWN_COOLDOWN)) {
            return false;
        }
        if (!plugin.getCloneManager().spawnClones(player, CLONE_AMOUNT, CLONE_DURATION)) {
            resetCooldown("crown");
            return false;
        }
        MessageUtil.broadcast("&c" + player.getName() + " a invoqué des clones !");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Izuna Hatsuse.",
                "Your goal is to win with Ino.",
                "You inflict Blindness to your opponent in Bloc Party."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You get Speed II and an arrow pointing to Ino.",
                "If Ino dies: a crown summoning 5 clones for 10 seconds (every 20 minutes)",
                "and you may join Miko's camp (/duo joinmiko) for 5 minutes of Weakness II."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Ino.";
    }
}
