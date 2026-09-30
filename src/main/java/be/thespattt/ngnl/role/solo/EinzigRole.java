package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Einzig: knows Sora from the start. In the finale he has two lives: a weak first one (Weakness II,
 * 10 hearts) and, after dying once, a strong second one (Strength, 8 hearts).
 * Killing Riku makes him join Sora's camp.
 */
public class EinzigRole extends Role {

    /** Hearts of the first (weak) life. */
    private static final double FIRST_LIFE_HEARTS = 10.0;
    /** Hearts of the second (strong) life. */
    private static final double SECOND_LIFE_HEARTS = 8.0;

    private boolean secondLifeUsed = false;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (EINZIG)
     */
    public EinzigRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        UUID soraId = plugin.getRoleManager().getPlayerByRole(RoleType.SORA);
        Player sora = soraId != null ? Bukkit.getPlayer(soraId) : null;
        if (sora != null) {
            MessageUtil.sendMessage(player, "&eSora est : &a" + sora.getName());
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        setMaxHearts(FIRST_LIFE_HEARTS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 1, false, false));
        MessageUtil.sendMessage(player, "&7Première vie : un homme faible (Faiblesse II, 10 cœurs). Si tu meurs, tu reviendras plus fort.");
    }

    @Override
    public boolean tryCheatDeath() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || secondLifeUsed) {
            return false;
        }
        secondLifeUsed = true;
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false));
        setMaxHearts(SECOND_LIFE_HEARTS);
        MessageUtil.broadcast("&fEinzig est revenu pour une seconde vie !");
        return true;
    }

    @Override
    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        if (killerId == null || !killerId.equals(playerId)) {
            return;
        }
        NGNLPlayer victim = plugin.getPlayerManager().getNGNLPlayer(victimId);
        if (victim == null || victim.getRole() == null || victim.getRole().getRoleType() != RoleType.RIKU) {
            return;
        }
        UUID soraId = plugin.getRoleManager().getPlayerByRole(RoleType.SORA);
        NGNLPlayer sora = soraId != null ? plugin.getPlayerManager().getNGNLPlayer(soraId) : null;
        NGNLPlayer self = getNGNLPlayer();
        if (self != null && sora != null) {
            self.setAlliancePartner(soraId);
            sora.setAlliancePartner(playerId);
            MessageUtil.sendMessage(getPlayer(), "&aTu as tué Riku : tu rejoins le camp de Sora.");
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        // Einzig has no finale item.
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Einzig.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You know Sora's identity from the start."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Two lives: first a weak man (Weakness II, 10 hearts); if you die you return",
                "with Strength and 8 hearts. If you kill Riku you join Sora's camp."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
