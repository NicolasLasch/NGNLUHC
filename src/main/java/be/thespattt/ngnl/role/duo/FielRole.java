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

public class FielRole extends DuoRole {

    private static final int DISGUISE_COOLDOWN = 15 * 60;
    private int lastIllusionEpisode = -1;
    private int lastShardEpisode = -1;
    private long lastDisguiseUse = 0L;

    public FielRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player != null && partner != null) {
            MessageUtil.sendMessage(player, "&eChlammy is: &a" + partner.getName());
            giveMindItem(player);
        }
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        int episode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        if (lastIllusionEpisode < episode) {
            lastIllusionEpisode = episode;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0, false, false));
            MessageUtil.sendMessage(player, "&dYour once-per-episode illusion gives you Speed I for this start.");
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastDisguiseUse = 0L;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveMindItem(player);
        ItemStack mask = new ItemBuilder(Material.CARVED_PUMPKIN)
                .name("&d&lIllusion Mask")
                .lore("&730 seconds of disguise-like stealth.", "&cCooldown: 15 minutes")
                .glow(true)
                .setTag("role_item", "FIEL")
                .build();
        player.getInventory().addItem(mask);
    }

    private void giveMindItem(Player player) {
        ItemStack item = new ItemBuilder(Material.AMETHYST_SHARD)
                .name("&d&lIllusion Shard")
                .lore("&7Once per episode, disturb nearby players.", "&7Applies Blindness and confusion.")
                .glow(true)
                .setTag("role_item", "FIEL")
                .build();
        player.getInventory().addItem(item);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }

        if (item.getType() == Material.CARVED_PUMPKIN) {
            return useDisguise();
        }
        if (item.getType() == Material.AMETHYST_SHARD) {
            return useIllusionShard();
        }
        return false;
    }

    private boolean useIllusionShard() {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }

        int episode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        if (lastShardEpisode == episode) {
            MessageUtil.sendMessage(player, "&cYou already used your illusion this episode.");
            return false;
        }

        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby.getUniqueId().equals(playerId) || !plugin.getGameManager().isPlayerAlive(nearby.getUniqueId())) {
                continue;
            }
            if (!nearby.getWorld().equals(player.getWorld()) || nearby.getLocation().distance(player.getLocation()) > 12.0) {
                continue;
            }
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 4 * 20, 0, false, false));
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 4 * 20, 0, false, false));
            MessageUtil.sendMessage(nearby, "&5An illusion disturbs your senses.");
        }

        lastShardEpisode = episode;
        MessageUtil.sendMessage(player, "&aYour illusion shard affected nearby players.");
        return true;
    }

    private boolean useDisguise() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }

        long now = System.currentTimeMillis() / 1000;
        if (now - lastDisguiseUse < DISGUISE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cCooldown: " + formatTime(DISGUISE_COOLDOWN - (now - lastDisguiseUse)));
            return false;
        }

        lastDisguiseUse = now;
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30 * 20, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30 * 20, 0, false, false));
        MessageUtil.sendMessage(player, "&aYou veil yourself for 30 seconds.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Fiel.",
                "Your goal is to win with Chlammy.",
                "You know Chlammy's identity from the start.",
                "Once per episode, your illusion shard disrupts nearby players."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive an Illusion Mask in finale.",
                "It grants 30 seconds of stealth every 15 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Chlammy.";
    }

    private String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
