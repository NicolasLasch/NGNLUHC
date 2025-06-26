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

public class SoraRole extends DuoRole {

    private static final int CLONE_COOLDOWN = 20 * 60;
    private static final int CLONE_DURATION = 10;
    private static final int PARTNER_PROXIMITY_RANGE = 30;

    private long lastCloneUsage = 0;
    private int proximityCheckTaskId = -1;

    public SoraRole(NoGameNoLife plugin, UUID playerId) {
        super(plugin, playerId, RoleType.SORA);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) return;

        UUID shiroUUID = getPartnerUUID();
        if (shiroUUID != null) {
            Player shiroPlayer = Bukkit.getPlayer(shiroUUID);
            if (shiroPlayer != null) {
                MessageUtil.sendMessage(player, "&eShiro is: &a" + shiroPlayer.getName());
                MessageUtil.sendMessage(player, "&eYou know their identity and position from the start.");
            }
        }

        startProximityCheck();
    }

    private void startProximityCheck() {
        if (proximityCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(proximityCheckTaskId);
        }

        proximityCheckTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                this::checkPartnerProximity, 20L, 20L);
    }

    private void checkPartnerProximity() {
        Player player = getPlayer();
        if (player == null) return;

        Player shiro = getPartnerPlayer();
        if (shiro == null || !plugin.getGameManager().isPlayerAlive(getPartnerUUID())) {
            applyNegativeEffects(player);
            return;
        }

        if (!player.getWorld().equals(shiro.getWorld())) {
            applyNegativeEffects(player);
            return;
        }

        double distance = player.getLocation().distance(shiro.getLocation());

        if (distance <= PARTNER_PROXIMITY_RANGE) {
            applyPositiveEffects(player);
        } else {
            applyNegativeEffects(player);
        }
    }

    private void applyPositiveEffects(Player player) {
        player.removePotionEffect(PotionEffectType.WEAKNESS);

        if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }

        if (!player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
    }

    private void applyNegativeEffects(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);

        if (!player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
        }
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) return;

        MessageUtil.sendMessage(player, "&6Mini-game starting: &e" + miniGameType.getDisplayName());
        MessageUtil.sendMessage(player, "&6You have 30 seconds to ask Shiro to substitute!");
        MessageUtil.sendMessage(player, "&eUse &a/substitute &eto request Shiro to play for you.");
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastCloneUsage = 0;
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null) return;

        MessageUtil.sendMessage(player, "&c&lShiro has been eliminated!");
        MessageUtil.sendMessage(player, "&cYou feel significantly weaker without your partner...");

        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
    }

    public boolean useCloneAbility() {
        if (!isArenaPhaseActive()) {
            return false;
        }

        Player player = getPlayer();
        if (player == null) return false;

        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime - lastCloneUsage < CLONE_COOLDOWN) {
            long remainingCooldown = CLONE_COOLDOWN - (currentTime - lastCloneUsage);
            MessageUtil.sendMessage(player, "&cYou must wait " + formatTime(remainingCooldown) + " to use this ability again!");
            return false;
        }

        lastCloneUsage = currentTime;

        boolean success = plugin.getCloneManager().spawnClones(player, 5, CLONE_DURATION);

        if (success) {
            MessageUtil.sendMessage(player, "&a&lYou summoned 5 clones around yourself!");
            MessageUtil.broadcast("&c" + player.getName() + " has summoned clones!");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    MessageUtil.sendMessage(player, "&eYour clones have disappeared.");
                }
            }, CLONE_DURATION * 20L);
        } else {
            MessageUtil.sendMessage(player, "&cFailed to create clones!");
            return false;
        }

        return true;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack crown = new ItemBuilder(Material.GOLDEN_HELMET)
                .name("&6&lSora's Crown")
                .lore(
                        "&7Allows you to spawn 5 clones",
                        "&7around you for 10 seconds.",
                        "",
                        "&eRight-click to activate",
                        "&cCooldown: 20 minutes"
                )
                .glow(true)
                .setTag("role_item", "SORA")
                .build();

        player.getInventory().addItem(crown);
        MessageUtil.sendMessage(player, "&aYou received &6Sora's Crown&a!");
        MessageUtil.sendMessage(player, "&eRight-click to create clones. (Cooldown: 20 minutes)");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.GOLDEN_HELMET) {
            return useCloneAbility();
        }
        return false;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Sora.",
                "Your goal is to win with Shiro.",
                "During mini-game launches, you have 30 seconds",
                "to ask Shiro to go in your place.",
                "You know Shiro's identity and position from the start."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You now have a crown that allows you to spawn 5 clones",
                "around you for 10 seconds. This ability can be used",
                "every 20 minutes.",
                "",
                "If you are close to Shiro (within 30 blocks), you will have",
                "the Resistance and Speed effects. However, if you are",
                "more than 30 blocks away, you will have the Weakness effect."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Shiro.";
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    @Override
    public void onDeath(UUID killerId) {
        super.onDeath(killerId);

        if (proximityCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(proximityCheckTaskId);
            proximityCheckTaskId = -1;
        }
    }
}