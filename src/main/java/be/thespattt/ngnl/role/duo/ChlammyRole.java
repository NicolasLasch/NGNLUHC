package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ChlammyRole extends DuoRole {

    private static final int SLOW_COOLDOWN = 15 * 60;
    private long lastSlowUse = 0L;
    private int lastReadEpisode = -1;

    public ChlammyRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player != null && partner != null) {
            MessageUtil.sendMessage(player, "&eFiel is: &a" + partner.getName());
            giveMindEye(player);
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastSlowUse = 0L;
        Player player = getPlayer();
        if (player != null) {
            giveMindEye(player);
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack orb = new ItemBuilder(Material.ENDER_PEARL)
                .name("&b&lPrediction Orb")
                .lore("&7Slows a nearby enemy for 20 seconds.", "&cCooldown: 15 minutes")
                .glow(true)
                .setTag("role_item", "CHLAMMY")
                .build();
        player.getInventory().addItem(orb);
    }

    private void giveMindEye(Player player) {
        ItemStack eye = new ItemBuilder(Material.BOOK)
                .name("&b&lMind Reading")
                .lore("&7Once per episode, reveal the inventory", "&7of the nearest player.")
                .glow(true)
                .setTag("role_item", "CHLAMMY")
                .build();
        player.getInventory().addItem(eye);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.BOOK) {
            return useMindRead();
        }
        if (item.getType() == Material.ENDER_PEARL) {
            return usePrediction();
        }
        return false;
    }

    private boolean useMindRead() {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }

        int episode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
        if (lastReadEpisode == episode) {
            MessageUtil.sendMessage(player, "&cYou already used mind reading this episode.");
            return false;
        }

        Player target = getNearestValidPlayer(player, 12.0);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cNo valid target nearby.");
            return false;
        }

        lastReadEpisode = episode;
        MessageUtil.sendMessage(player, "&bInventory of " + target.getName() + ":");
        for (ItemStack stack : target.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : stack.getType().name();
            MessageUtil.sendMessage(player, "&7- &f" + name + " &7x" + stack.getAmount());
        }
        return true;
    }

    private boolean usePrediction() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }

        long now = System.currentTimeMillis() / 1000;
        if (now - lastSlowUse < SLOW_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cCooldown: " + formatTime(SLOW_COOLDOWN - (now - lastSlowUse)));
            return false;
        }

        Player target = getNearestValidPlayer(player, 18.0);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cNo target nearby.");
            return false;
        }

        lastSlowUse = now;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 20, 1, false, false));
        MessageUtil.sendMessage(player, "&aYou predicted " + target.getName() + "'s movement.");
        MessageUtil.sendMessage(target, "&cChlammy anticipated your movements.");
        return true;
    }

    private Player getNearestValidPlayer(Player player, double range) {
        Player result = null;
        double best = Double.MAX_VALUE;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(playerId) || other.getUniqueId().equals(getPartnerUUID())) {
                continue;
            }
            if (!plugin.getGameManager().isPlayerAlive(other.getUniqueId()) || !other.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = other.getLocation().distance(player.getLocation());
            if (distance <= range && distance < best) {
                best = distance;
                result = other;
            }
        }
        return result;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Chlammy.",
                "Your goal is to win with Fiel.",
                "You know Fiel's identity from the start.",
                "Once per episode, you can inspect the inventory of a nearby player."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive the Prediction Orb in finale.",
                "It inflicts Slowness on a nearby enemy for 20 seconds every 15 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Fiel.";
    }

    private String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
