package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ShiroRole extends DuoRole {

    private static final int CLONE_COOLDOWN = 20 * 60;
    private static final int CLONE_DURATION = 60;
    private static final int PARTNER_PROXIMITY_RANGE = 30;
    private static final int MAX_BONUSES = 2;

    private long lastCloneUsage = 0;
    private int bonusesUsed = 0;
    private int proximityCheckTaskId = -1;
    private boolean distancePenaltyActive = false;

    public ShiroRole(NoGameNoLife plugin, UUID playerId) {
        super(plugin, playerId, RoleType.SHIRO);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) return;

        revealPartnerIdentity(player);

        MessageUtil.sendMessage(player, "&eYou can give Sora bonuses during mini-games (&a" + MAX_BONUSES + " times&e).");
        startProximityCheck();
    }

    private void startProximityCheck() {
        if (proximityCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(proximityCheckTaskId);
        }

        proximityCheckTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                this::checkPartnerProximity, 20L, 20L);
    }

    private void updateSoraDirectionActionBar() {
        Player player = getPlayer();
        Player sora = getPartnerPlayer();
        
        if (player == null) return;

        if (sora == null || !plugin.getGameManager().isPlayerAlive(getPartnerUUID())) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§cSora is not available"));
            return;
        }
        
        if (!player.getWorld().equals(sora.getWorld())) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§e" + sora.getName() + " is in another world"));
            return;
        }
        
        double dx = sora.getLocation().getX() - player.getLocation().getX();
        double dz = sora.getLocation().getZ() - player.getLocation().getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        float playerYaw = player.getLocation().getYaw();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relativeAngle = targetYaw - playerYaw;
        
        // Normalize angle to -180 to 180
        while (relativeAngle > 180) relativeAngle -= 360;
        while (relativeAngle < -180) relativeAngle += 360;
        
        // Convert to arrow direction
        String arrow = getDirectionArrow(relativeAngle);
        String distanceText = String.format("%.1f", distance);

        String color = distance <= PARTNER_PROXIMITY_RANGE ? "§b" : "§c";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(color + arrow + " §f" + sora.getName() + " §7(" + distanceText + "m)"));
    }

    private String getDirectionArrow(double angle) {
        if (angle >= -22.5 && angle < 22.5) return "↑";
        else if (angle >= 22.5 && angle < 67.5) return "↗";
        else if (angle >= 67.5 && angle < 112.5) return "→";
        else if (angle >= 112.5 && angle < 157.5) return "↘";
        else if (angle >= 157.5 || angle < -157.5) return "↓";
        else if (angle >= -157.5 && angle < -112.5) return "↙";
        else if (angle >= -112.5 && angle < -67.5) return "←";
        else return "↖";
    }

    // Modify the checkPartnerProximity method to include the direction update
    private void checkPartnerProximity() {
        Player player = getPlayer();
        if (player == null) return;

        updateSoraDirectionActionBar();

        Player sora = getPartnerPlayer();
        if (plugin.getMiniGameEngine().isPlayerInMiniGame(playerId)) {
            removeBaseProximityEffects(player);
            distancePenaltyActive = false;
            return;
        }

        if (sora == null || !plugin.getGameManager().isPlayerAlive(getPartnerUUID())) {
            applyNegativeEffects(player);
            return;
        }

        if (!player.getWorld().equals(sora.getWorld())) {
            applyNegativeEffects(player);
            return;
        }

        double distance = player.getLocation().distance(sora.getLocation());

        if (distance <= PARTNER_PROXIMITY_RANGE) {
            applyPositiveEffects(player);
        } else {
            applyNegativeEffects(player);
        }
    }

    private void applyPositiveEffects(Player player) {
        distancePenaltyActive = false;

        if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }

        if (!player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
    }

    private void applyNegativeEffects(Player player) {
        removeBaseProximityEffects(player);
        distancePenaltyActive = true;
    }

    private void removeBaseProximityEffects(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) return;

        Player sora = getPartnerPlayer();
        if (sora != null && bonusesUsed < MAX_BONUSES) {
            MessageUtil.sendMessage(player, "&6Mini-game starting: &e" + miniGameType.getDisplayName());
            MessageUtil.sendMessage(player, "&aYou can give Sora a bonus! (&e" + (MAX_BONUSES - bonusesUsed) + " remaining&a)");
            MessageUtil.sendMessage(player, "&eUse &a/bonus &eto give Sora an advantage.");
        }
    }

    public boolean giveBonusToSora() {
        if (bonusesUsed >= MAX_BONUSES) {
            Player player = getPlayer();
            if (player != null) {
                MessageUtil.sendMessage(player, "&cYou have already used all your bonuses!");
            }
            return false;
        }

        Player sora = getPartnerPlayer();
        if (sora == null) {
            Player player = getPlayer();
            if (player != null) {
                MessageUtil.sendMessage(player, "&cSora is not online!");
            }
            return false;
        }

        bonusesUsed++;

        sora.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20 * 60 * 5, 1, false, false));
        sora.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 5, 0, false, false));

        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&aYou gave Sora a bonus! (&e" + (MAX_BONUSES - bonusesUsed) + " remaining&a)");
        }
        MessageUtil.sendMessage(sora, "&aShiro gave you a bonus! (Luck II + Speed I for 5 minutes)");

        return true;
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

        MessageUtil.sendMessage(player, "&c&lSora has been eliminated!");
        MessageUtil.sendMessage(player, "&cYou feel significantly weaker without your partner...");
        distancePenaltyActive = true;
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
                .name("&f&lShiro's Crown")
                .lore(
                        "&7Allows you to spawn 5 clones",
                        "&7around you for 1 minute.",
                        "",
                        "&eRight-click to activate",
                        "&cCooldown: 20 minutes"
                )
                .glow(true)
                .setTag("role_item", "SHIRO")
                .build();

        player.getInventory().addItem(crown);
        MessageUtil.sendMessage(player, "&aYou received &fShiro's Crown&a!");
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
                "You are Shiro.",
                "Your goal is to win with Sora.",
                "During mini-games, you can give Sora bonuses",
                "2 times during the entire game.",
                "You know Sora's identity and position from the start."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You now have a crown that allows you to spawn 5 clones",
                "around you for 10 seconds. This ability can be used",
                "every 20 minutes.",
                "",
                "If you are close to Sora (within 30 blocks), you will have",
                "the Resistance and Speed effects. However, if you are",
                "more than 30 blocks away, you will have the Weakness effect."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Sora.";
    }

    public int getBonusesRemaining() {
        return MAX_BONUSES - bonusesUsed;
    }

    public boolean isDistancePenaltyActive() {
        return distancePenaltyActive;
    }

    private void revealPartnerIdentity(Player player) {
        UUID soraUUID = getPartnerUUID();
        if (soraUUID == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player current = getPlayer();
                if (current != null) {
                    revealPartnerIdentity(current);
                }
            }, 20L);
            return;
        }

        Player soraPlayer = Bukkit.getPlayer(soraUUID);
        if (soraPlayer != null) {
            MessageUtil.sendMessage(player, "&eSora is: &a" + soraPlayer.getName());
            MessageUtil.sendMessage(player, "&eYou know their identity and position from the start.");
        }
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    // Modify the onDeath method to clean up the boss bar
    @Override
    public void onDeath(UUID killerId) {
        super.onDeath(killerId);

        if (proximityCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(proximityCheckTaskId);
            proximityCheckTaskId = -1;
        }
        distancePenaltyActive = false;
    }

    // Also clean up when role is removed/changed
    public void cleanup() {
        if (proximityCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(proximityCheckTaskId);
            proximityCheckTaskId = -1;
        }
        distancePenaltyActive = false;
    }
}
