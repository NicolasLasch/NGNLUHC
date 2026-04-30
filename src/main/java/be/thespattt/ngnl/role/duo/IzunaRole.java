package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
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

public class IzunaRole extends DuoRole {

    private static final int CLONE_COOLDOWN = 20 * 60;
    private long lastCloneUse = 0L;
    private int trackingTaskId = -1;
    private boolean cloneUnlocked = false;
    private boolean shieldProtected = false;

    public IzunaRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player != null && partner != null) {
            MessageUtil.sendMessage(player, "&eIno is: &a" + partner.getName());
        }
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
        startTrackingInoTask();
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || !partnerId.equals(getPartnerUUID())) {
            return;
        }
        cloneUnlocked = true;
        giveArenaPhaseItems(player);
        MessageUtil.sendMessage(player, "&eYour clone crown has been unlocked.");
    }

    private void startTrackingInoTask() {
        if (trackingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(trackingTaskId);
        }

        trackingTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            Player ino = getPartnerPlayer();
            if (player == null || ino == null) {
                return;
            }
            player.setCompassTarget(ino.getLocation());
        }, 20L, 20L);
    }

    public void activateShieldProtection() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        shieldProtected = true;
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30 * 20, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30 * 20, 10, false, false));
        MessageUtil.sendMessage(player, "&aIno protected you with the Hatsuse Shield.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> shieldProtected = false, 30 * 20L);
    }

    public boolean isProtectedByShield() {
        return shieldProtected;
    }

    public void joinMikoCamp() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        if (plugin.getGameManager().isPlayerAlive(getPartnerUUID())) {
            MessageUtil.sendMessage(player, "&cYou can only do this after Ino dies.");
            return;
        }

        UUID mikoId = plugin.getRoleManager().getPlayerByRole(RoleType.MIKO);
        NGNLPlayer self = plugin.getPlayerManager().getNGNLPlayer(playerId);
        NGNLPlayer miko = mikoId != null ? plugin.getPlayerManager().getNGNLPlayer(mikoId) : null;
        if (self != null && miko != null) {
            self.setAlliancePartner(mikoId);
            miko.setAlliancePartner(playerId);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 5 * 60 * 20, 1, false, false));
        Player mikoPlayer = mikoId != null ? Bukkit.getPlayer(mikoId) : null;
        if (mikoPlayer != null) {
            PotionEffectType haste = PotionEffectType.getByName("HASTE");
            if (haste != null) {
                player.addPotionEffect(new PotionEffect(haste, Integer.MAX_VALUE, 1, false, false));
                mikoPlayer.addPotionEffect(new PotionEffect(haste, Integer.MAX_VALUE, 1, false, false));
            }
        }
        MessageUtil.sendMessage(player, "&eYou joined Miko's camp and gained Weakness II for 5 minutes.");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        if (!cloneUnlocked) {
            return;
        }
        ItemStack crown = new ItemBuilder(Material.GOLDEN_HELMET)
                .name("&6&lIzuna's Crown")
                .lore("&75 clones for 10 seconds.", "&cCooldown: 20 minutes")
                .glow(true)
                .setTag("role_item", "IZUNA")
                .build();
        player.getInventory().addItem(crown);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.GOLDEN_HELMET) {
            return useCloneAbility();
        }
        return false;
    }

    private boolean useCloneAbility() {
        Player player = getPlayer();
        if (player == null || !cloneUnlocked || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastCloneUse < CLONE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cClone crown cooldown active.");
            return false;
        }
        lastCloneUse = now;
        return plugin.getCloneManager().spawnClones(player, 5, 10);
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Izuna Hatsuse.",
                "Your goal is to win with Ino.",
                "You inflict Blindness in Bloc Party."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You gain Speed II and your compass points to Ino.",
                "After Ino's death, your clone crown becomes available."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Ino.";
    }
}
