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

public class SchwiRole extends DuoRole {

    private static final int ALLIANCE_COOLDOWN = 10 * 60;

    public SchwiRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&eTu as Vitesse et Saut amélioré dans TOUS les mini-jeux.");
        }
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("alliance_scanner");
        Player player = getPlayer();
        if (player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false));
        }
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || !partnerId.equals(getPartnerUUID())) {
            return;
        }
        MessageUtil.sendMessage(player, "&cRiku est mort. Tu meurs de tristesse...");
        if (plugin.getGameManager().isPlayerAlive(playerId)) {
            plugin.getGameManager().handlePlayerElimination(playerId, killerId);
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack alliance = new ItemBuilder(Material.ENDER_EYE)
                .name("&b&lAlliance Scanner")
                .lore("&7Glows every enemy within 30 blocks.", "&cCooldown: 10 minutes")
                .glow(true)
                .setTag("role_item", "SCHWI")
                .build();
        player.getInventory().addItem(alliance);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.ENDER_EYE) {
            return useAllianceScanner();
        }
        return false;
    }

    private boolean useAllianceScanner() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        if (!tryUseCooldown("alliance_scanner", ALLIANCE_COOLDOWN)) {
            return false;
        }

        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby.getUniqueId().equals(playerId) || nearby.getUniqueId().equals(getPartnerUUID())) {
                continue;
            }
            if (!plugin.getGameManager().isPlayerAlive(nearby.getUniqueId())) {
                continue;
            }
            if (nearby.getWorld().equals(player.getWorld()) && nearby.getLocation().distance(player.getLocation()) <= 30.0) {
                nearby.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 10 * 20, 0, false, false));
            }
        }
        MessageUtil.sendMessage(player, "&aNearby enemies are now glowing.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Schwi Dola.",
                "Your goal is to win with Riku.",
                "You gain Speed and Jump Boost in every mini-game.",
                "If Riku dies, you die as well."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You gain a nerfed Strength effect in finale.",
                "Your Alliance Scanner reveals enemies within 30 blocks every 10 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Riku.";
    }
}
