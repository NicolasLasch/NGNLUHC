package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.CombatRestrictions;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Corone Dola: knows Riku, regenerates automatically and, in the finale, can see 179 Ghost even
 * when he is invisible. If Ivan dies, Nonna may join her; if Ghost kills her she joins Ghost.
 */
public class CoroneRole extends Role implements CombatRestrictions {

    private final GhostForm ghostForm = new GhostForm();
    private boolean ghostPower = false;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (CORONE)
     */
    public CoroneRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        UUID rikuId = plugin.getRoleManager().getPlayerByRole(RoleType.RIKU);
        Player riku = rikuId != null ? Bukkit.getPlayer(rikuId) : null;
        if (riku != null) {
            MessageUtil.sendMessage(player, "&eRiku est : &a" + riku.getName());
        }
        startNaturalRegeneration();
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        runRepeating(this::revealGhost, 20L, 5L);
    }

    /**
     * Show 179 Ghost to Corone even when he is invisible (particles + compass).
     */
    private void revealGhost() {
        Player player = getPlayer();
        UUID ghostId = plugin.getRoleManager().getPlayerByRole(RoleType.GHOST);
        Player ghost = ghostId != null ? Bukkit.getPlayer(ghostId) : null;
        if (player == null || ghost == null || !isAlive() || !plugin.getGameManager().isPlayerAlive(ghostId)) {
            return;
        }
        player.setCompassTarget(ghost.getLocation());
        if (ghost.getWorld().equals(player.getWorld())) {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(190, 190, 255), 1.6f);
            player.spawnParticle(Particle.DUST, ghost.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0, dust);
        }
    }

    /**
     * Give Corone the ghost form (after being killed by 179 Ghost).
     */
    public void grantGhostPower() {
        Player player = getPlayer();
        ghostPower = true;
        if (player == null) {
            return;
        }
        giveItem(player, buildRoleItem(Material.PHANTOM_MEMBRANE, "&7&lObjet Fantôme",
                "&7Clic droit : forme fantôme (invisible, Vitesse II, pas de chute)."));
        ghostForm.setHidden(player, true);
    }

    /**
     * Give Corone the Speed I bonus of Nonna's alliance.
     */
    public void grantGhostVisionCompanion() {
        Player player = getPlayer();
        if (player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
            MessageUtil.sendMessage(player, "&aNonna a rejoint ton camp : Vitesse I pour le reste de la partie.");
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        // Corone has no finale item (unless 179 Ghost converts her).
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        Player player = getPlayer();
        return ghostPower && player != null && item != null
                && item.getType() == Material.PHANTOM_MEMBRANE && ghostForm.toggle(player);
    }

    @Override
    public boolean isAttackBlocked() {
        return ghostForm.isHidden();
    }

    @Override
    public String attackBlockedMessage() {
        return "&cTu ne peux pas attaquer sous ta forme fantôme.";
    }

    @Override
    public boolean isFallImmune() {
        return ghostForm.isHidden();
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Corone Dola.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You know Riku from the start and regenerate automatically."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You can see 179 Ghost even when he is invisible.",
                "If Ivan dies, Nonna can join your camp (Speed I for both of you)."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
