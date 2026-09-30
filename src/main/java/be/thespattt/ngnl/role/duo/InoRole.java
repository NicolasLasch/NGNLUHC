package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class InoRole extends DuoRole {

    private static final int SHIELD_COOLDOWN = 20 * 60;
    private UUID revengeTarget;
    private int revengeTaskId = -1;

    public InoRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        // Ino learns Izuna through the three-name list sent with the role.
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        // Slowness is applied in the mini-game listener.
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("hatsuse_shield");
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || !partnerId.equals(getPartnerUUID())) {
            return;
        }

        revengeTarget = killerId;
        if (killerId == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 10 * 20, 1, false, false));
        MessageUtil.sendMessage(player, "&cIzuna died. Kill her murderer in 10 seconds or you die too.");

        if (revengeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(revengeTaskId);
        }

        revengeTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (revengeTarget != null && plugin.getGameManager().isPlayerAlive(playerId)) {
                MessageUtil.sendMessage(player, "&cYou failed to avenge Izuna.");
                plugin.getGameManager().handlePlayerElimination(playerId, revengeTarget);
            }
            revengeTarget = null;
            revengeTaskId = -1;
        }, 10 * 20L);
    }

    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        if (revengeTarget == null || !revengeTarget.equals(victimId) || killerId == null || !killerId.equals(playerId)) {
            return;
        }

        Player player = getPlayer();
        if (player == null) {
            return;
        }

        revengeTarget = null;
        if (revengeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(revengeTaskId);
            revengeTaskId = -1;
        }

        UUID izunaId = getPartnerUUID();
        if (izunaId != null && !plugin.getGameManager().isPlayerAlive(izunaId)) {
            plugin.getGameManager().revivePlayer(izunaId, player.getLocation(), 8.0);
            MessageUtil.broadcast("&dIno avenged Izuna and brought her back.");
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack shield = new ItemBuilder(Material.SHIELD)
                .name("&d&lHatsuse Shield")
                .lore("&7Protège Izuna pendant 30 secondes", "&7(invincible, invisible, ne peut pas frapper).", "&cRecharge : 20 minutes")
                .glow(true)
                .setTag("role_item", "INO")
                .build();
        player.getInventory().addItem(shield);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.SHIELD) {
            return useShield();
        }
        return false;
    }

    private boolean useShield() {
        Player player = getPlayer();
        Player izunaPlayer = getPartnerPlayer();
        if (player == null || izunaPlayer == null || !isArenaPhaseActive()) {
            return false;
        }

        if (!(plugin.getPlayerManager().getNGNLPlayer(izunaPlayer.getUniqueId()).getRole() instanceof IzunaRole izunaRole)) {
            return false;
        }
        if (!tryUseCooldown("hatsuse_shield", SHIELD_COOLDOWN)) {
            return false;
        }
        izunaRole.activateShieldProtection();
        MessageUtil.sendMessage(player, "&aIzuna est protégée pendant 30 secondes.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Ino Hatsuse.",
                "Your goal is to win with Izuna.",
                "You inflict Slowness in TNT Run and Splegg."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive the Hatsuse Shield in finale.",
                "If Izuna dies, you gain Strength II for 10 seconds to avenge her."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Izuna.";
    }
}
