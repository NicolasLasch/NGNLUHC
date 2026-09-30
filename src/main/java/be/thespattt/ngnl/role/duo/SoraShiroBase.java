package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.DirectionArrow;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Shared behaviour of Sora and Shiro: they know each other, follow each other with an
 * action-bar arrow, own a crown summoning clones in the finale and are buffed when close
 * to each other (Resistance + Speed) or weakened when far (Weakness).
 */
public abstract class SoraShiroBase extends DuoRole {

    /** Distance (blocks) under which the two partners are considered close. */
    protected static final int PARTNER_PROXIMITY_RANGE = 30;
    /** Number of clones summoned by the crown. */
    private static final int CLONE_AMOUNT = 5;
    /** Lifetime of the clones in seconds. */
    private static final int CLONE_DURATION = 10;
    /** Cooldown of the crown in seconds. */
    private static final int CROWN_COOLDOWN = 20 * 60;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Sora or Shiro
     */
    protected SoraShiroBase(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    /**
     * Name of the partner, used in messages.
     *
     * @return "Shiro" for Sora, "Sora" for Shiro
     */
    protected abstract String partnerName();

    /**
     * Item name of the crown.
     *
     * @return Colored crown name
     */
    protected abstract String crownName();

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Player partner = getPartnerPlayer();
        if (partner != null) {
            MessageUtil.sendMessage(player, "&e" + partnerName() + " est : &a" + partner.getName());
        }
        MessageUtil.sendMessage(player, "&eTu connais son identité et sa position dès le début (flèche dans l'action bar).");
        runRepeating(this::updatePartnerActionBar, 20L, 20L);
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("crown");
        runRepeating(this::applyProximityEffects, 20L, 20L);
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&c&l" + partnerName() + " a été éliminé(e) !");
            MessageUtil.sendMessage(player, "&cTu te sens bien plus faible sans ton partenaire...");
        }
    }

    // ------------------------------------------------------------------ proximity effects

    /**
     * Apply the buffs when close to the partner, the weakness otherwise (finale only).
     */
    private void applyProximityEffects() {
        Player player = getPlayer();
        if (player == null || !isAlive() || plugin.getMiniGameEngine().isPlayerInMiniGame(playerId)) {
            return;
        }
        if (isCloseToPartner(player)) {
            applyBuffs(player);
        } else {
            applyWeakness(player);
        }
    }

    /**
     * Check if the partner is alive, in the same world and within range.
     *
     * @param player The role's player
     * @return True if the partner is close
     */
    private boolean isCloseToPartner(Player player) {
        Player partner = getPartnerPlayer();
        if (partner == null || !isPartnerAlive() || !partner.getWorld().equals(player.getWorld())) {
            return false;
        }
        return player.getLocation().distance(partner.getLocation()) <= PARTNER_PROXIMITY_RANGE;
    }

    /**
     * Give Resistance and Speed, remove Weakness.
     *
     * @param player The role's player
     */
    private void applyBuffs(Player player) {
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, false));
    }

    /**
     * Give Weakness, remove the buffs.
     *
     * @param player The role's player
     */
    private void applyWeakness(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false));
    }

    // ------------------------------------------------------------------ action bar arrow

    /**
     * Show the direction and distance of the partner in the action bar.
     */
    private void updatePartnerActionBar() {
        Player player = getPlayer();
        if (player == null || !isAlive()) {
            return;
        }
        Player partner = isPartnerAlive() ? getPartnerPlayer() : null;
        DirectionArrow.show(player, partner, partnerName(), PARTNER_PROXIMITY_RANGE);
    }

    // ------------------------------------------------------------------ crown

    /**
     * Summon the clones of the crown (finale only, every 20 minutes).
     *
     * @return True if the clones were summoned
     */
    public boolean useCloneAbility() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("crown", CROWN_COOLDOWN)) {
            return false;
        }
        if (!plugin.getCloneManager().spawnClones(player, CLONE_AMOUNT, CLONE_DURATION)) {
            resetCooldown("crown");
            MessageUtil.sendMessage(player, "&cImpossible de créer les clones !");
            return false;
        }
        MessageUtil.sendMessage(player, "&a&lTu as invoqué " + CLONE_AMOUNT + " clones pendant " + CLONE_DURATION + " secondes !");
        MessageUtil.broadcast("&c" + player.getName() + " a invoqué des clones !");
        return true;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack crown = buildRoleItem(Material.GOLDEN_HELMET, crownName(),
                "&75 clones autour de toi pendant 10 secondes.", "",
                "&eClic droit pour activer", "&cRecharge : 20 minutes");
        giveItem(player, crown);
        MessageUtil.sendMessage(player, "&aTu as reçu ta couronne ! Clic droit pour créer des clones.");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.GOLDEN_HELMET && useCloneAbility();
    }
}
