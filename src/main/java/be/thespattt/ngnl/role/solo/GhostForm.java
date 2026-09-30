package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * State of the "ghost form" shared by 179 Ghost and the Corone he converts:
 * invisible, Speed II, no fall damage, cannot attack. After leaving the form the player must
 * wait 5 minutes before hiding again.
 */
public class GhostForm {

    /** Seconds to wait after becoming visible before hiding again. */
    private static final int REHIDE_COOLDOWN_SECONDS = 5 * 60;

    private boolean hidden = false;
    private long visibleSince = 0L;

    /**
     * Enter or leave the ghost form.
     *
     * @param player Player toggling the form
     * @return True if the form changed
     */
    public boolean toggle(Player player) {
        if (hidden) {
            setHidden(player, false);
            visibleSince = System.currentTimeMillis() / 1000;
            MessageUtil.sendMessage(player, "&eTu es redevenu visible.");
            return true;
        }
        long waited = System.currentTimeMillis() / 1000 - visibleSince;
        if (waited < REHIDE_COOLDOWN_SECONDS) {
            MessageUtil.sendMessage(player, "&cTu dois attendre " + (REHIDE_COOLDOWN_SECONDS - waited) + "s avant de disparaître à nouveau.");
            return false;
        }
        setHidden(player, true);
        return true;
    }

    /**
     * Apply or remove the ghost effects.
     *
     * @param player Player concerned
     * @param value  True to become a ghost
     */
    public void setHidden(Player player, boolean value) {
        hidden = value;
        if (value) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
            MessageUtil.sendMessage(player, "&7Forme fantôme active : invisible, Vitesse II, pas de dégâts de chute, tu ne peux pas attaquer.");
        } else {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    /**
     * Whether the ghost form is active.
     *
     * @return True while hidden
     */
    public boolean isHidden() {
        return hidden;
    }
}
