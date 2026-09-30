package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class StephanieRole extends DuoRole {

    private static final int LOVE_GUN_COOLDOWN = 10 * 60;
    private static final int LOVE_GUN_DURATION = 30;
    private static final double LOVE_GUN_HEALTH = 12.0; // 6 hearts

    private boolean hasUsedMiniGameChoice = false;
    private long lastLoveGunUsage = 0;

    public StephanieRole(NoGameNoLife plugin, UUID playerId) {
        super(plugin, playerId, RoleType.STEPHANIE);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) return;

        MessageUtil.sendMessage(player, "&eYou can choose mini-games once per game (when you win PvP).");
        MessageUtil.sendMessage(player, "&eYou get Speed I in Parkour and Floor is Lava mini-games.");
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) return;

        if (miniGameType == MiniGameType.PARKOUR || miniGameType == MiniGameType.FLOOR_IS_LAVA) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
            MessageUtil.sendMessage(player, "&aYou have Speed I for this mini-game!");
        }

        if (isWinner && !hasUsedMiniGameChoice) {
            MessageUtil.sendMessage(player, "&6You won the PvP! You can choose the mini-game!");
            MessageUtil.sendMessage(player, "&eUse &a/duo choosegame &eto select a specific mini-game.");
        } else if (isWinner && hasUsedMiniGameChoice) {
            MessageUtil.sendMessage(player, "&eYou've already used your mini-game choice ability.");
        }
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) return;

        player.removePotionEffect(PotionEffectType.SPEED);
    }

    public boolean chooseMiniGame(MiniGameType miniGameType) {
        if (hasUsedMiniGameChoice) {
            Player player = getPlayer();
            if (player != null) {
                MessageUtil.sendMessage(player, "&cYou have already used your mini-game choice!");
            }
            return false;
        }

        hasUsedMiniGameChoice = true;

        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&aYou chose the mini-game: &e" + miniGameType.getDisplayName());
        }

        return true;
    }

    public boolean hasUsedMiniGameChoice() {
        return hasUsedMiniGameChoice;
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastLoveGunUsage = 0;
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null) return;

        MessageUtil.sendMessage(player, "&c&lMakoto has been eliminated!");
        MessageUtil.sendMessage(player, "&cYou must continue alone...");
    }

    public boolean useLoveGun(Player target) {
        if (!isArenaPhaseActive()) {
            return false;
        }

        Player player = getPlayer();
        if (player == null) return false;

        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime - lastLoveGunUsage < LOVE_GUN_COOLDOWN) {
            long remainingCooldown = LOVE_GUN_COOLDOWN - (currentTime - lastLoveGunUsage);
            MessageUtil.sendMessage(player, "&cYou must wait " + formatTime(remainingCooldown) + " to use Love Gun again!");
            return false;
        }

        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, "&cInvalid target!");
            return false;
        }

        if (target.equals(player)) {
            MessageUtil.sendMessage(player, "&cYou cannot use Love Gun on yourself!");
            return false;
        }

        UUID partnerUUID = getPartnerUUID();
        if (partnerUUID != null && partnerUUID.equals(target.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou cannot use Love Gun on your duo teammate!");
            return false;
        }

        lastLoveGunUsage = currentTime;

        target.setMaxHealth(LOVE_GUN_HEALTH);
        if (target.getHealth() > LOVE_GUN_HEALTH) {
            target.setHealth(LOVE_GUN_HEALTH);
        }

        MessageUtil.sendMessage(player, "&dYou hit " + target.getName() + " with the Love Gun!");
        MessageUtil.sendMessage(target, "&dStephanie hit you with the Love Gun 2! Your max health is 12 HP for 30 seconds!");

        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreMaxHealth(target), LOVE_GUN_DURATION * 20L);

        return true;
    }

    /**
     * Give back the real maximum health of a player hit by the Love Gun.
     *
     * @param target Player hit by the Love Gun
     */
    private void restoreMaxHealth(Player target) {
        if (!target.isOnline()) {
            return;
        }
        var ngnlTarget = plugin.getPlayerManager().getNGNLPlayer(target.getUniqueId());
        double realMaxHealth = ngnlTarget != null ? ngnlTarget.getMaxHealth() : 20.0;
        target.setMaxHealth(realMaxHealth);
        MessageUtil.sendMessage(target, "&eThe Love Gun effect has worn off.");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack loveGun = createLoveGun();
        player.getInventory().addItem(loveGun);
        MessageUtil.sendMessage(player, "&aYou received the &6LOVE GUN 2&a!");
        MessageUtil.sendMessage(player, "&eHit players to set their max health to 12 HP for 30 seconds! (Cooldown: 10 minutes)");
    }

    private ItemStack createLoveGun() {
        ItemStack loveGun = new ItemBuilder(Material.GOLDEN_HOE)
                .name("&6&lLOVE GUN 2")
                .lore(
                        "&7Sets the hit player's max health to 12 HP for 30 seconds.",
                        "",
                        "&eRight-click to shoot",
                        "&cCooldown: 10 minutes"
                )
                .glow(true)
                .build();

        // Add role tag manually
        try {
            ItemMeta meta = loveGun.getItemMeta();
            if (meta != null) {
                NamespacedKey key = new NamespacedKey(plugin, "role_item");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "STEPHANIE");
                loveGun.setItemMeta(meta);
            }
        } catch (Exception e) {
            MessageUtil.logError("Failed to add tag to Love Gun", e);
        }

        return loveGun;
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }

        Projectile projectile = player.launchProjectile(Snowball.class);
        projectile.setVelocity(player.getLocation().getDirection().normalize().multiply(2.5));
        projectile.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "stephanie_love_gun"),
                PersistentDataType.STRING,
                player.getUniqueId().toString()
        );
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.4f);
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Stephanie Dola.",
                "Your goal is to win with Makoto.",
                "Once per game, you can choose the mini-game type",
                "(only if you win the PvP).",
                "You get Speed I in Parkour and Floor is Lava mini-games."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You now have the LOVE GUN 2.",
                "Hitting a player with it sets their max health to 12 HP",
                "for 30 seconds. Can be used every 10 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Makoto.";
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
}
