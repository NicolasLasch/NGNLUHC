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

public class EinzigRole extends Role {

    private boolean secondLifeUsed = false;

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
            MessageUtil.sendMessage(player, "&eSora is: &a" + sora.getName());
        }
        player.setMaxHealth(20.0);
        player.setHealth(20.0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 1, false, false));
    }

    @Override
    public void onDeath(UUID killerId) {
        if (!isArenaPhaseActive() || secondLifeUsed) {
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        secondLifeUsed = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getGameManager().revivePlayer(playerId, player.getLocation(), 16.0)) {
                Player revived = getPlayer();
                if (revived != null) {
                    revived.setMaxHealth(16.0);
                    revived.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false));
                    revived.removePotionEffect(PotionEffectType.WEAKNESS);
                }
                MessageUtil.broadcast("&fEinzig returned with a second life.");
            }
        }, 2L);
    }

    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        if (killerId == null || !killerId.equals(playerId)) {
            return;
        }
        var victim = plugin.getPlayerManager().getNGNLPlayer(victimId);
        if (victim == null || victim.getRole() == null || victim.getRole().getRoleType() != RoleType.RIKU) {
            return;
        }
        UUID soraId = plugin.getRoleManager().getPlayerByRole(RoleType.SORA);
        NGNLPlayer self = getNGNLPlayer();
        NGNLPlayer sora = soraId != null ? plugin.getPlayerManager().getNGNLPlayer(soraId) : null;
        if (self != null && sora != null) {
            self.setAlliancePartner(soraId);
            sora.setAlliancePartner(playerId);
            MessageUtil.sendMessage(getPlayer(), "&aYou killed Riku and joined Sora's camp.");
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Einzig.",
                "Your goal is to win alone or with an alliance.",
                "You know Sora from the start.",
                "Your first life is weak and limited to 10 hearts."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "If you die once in finale, you return with Strength and 8 hearts.",
                "If you kill Riku, you join Sora's camp."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
