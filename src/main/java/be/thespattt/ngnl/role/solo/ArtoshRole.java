package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ArtoshRole extends Role {

    private static final int BLADE_COOLDOWN = 10 * 60;
    private long lastBladeUse = 0L;

    public ArtoshRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        UUID schwiId = plugin.getRoleManager().getPlayerByRole(RoleType.SCHWI);
        if (player == null || schwiId == null) {
            return;
        }
        List<Player> others = new ArrayList<>(Bukkit.getOnlinePlayers());
        others.removeIf(other -> other.getUniqueId().equals(playerId));
        Player revealed = Bukkit.getPlayer(schwiId);
        if (!others.isEmpty() && ThreadLocalRandom.current().nextInt(100) < 40) {
            revealed = others.get(ThreadLocalRandom.current().nextInt(others.size()));
        }
        if (revealed != null) {
            MessageUtil.sendMessage(player, "&eYou were told Schwi is: &a" + revealed.getName());
        }
    }

    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        if (killerId == null || !killerId.equals(playerId)) {
            return;
        }
        Player player = getPlayer();
        if (player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 5 * 60 * 20, 0, false, false));
            MessageUtil.sendMessage(player, "&cYour kill awakened Artosh's strength for 5 minutes.");
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        player.getInventory().addItem(new ItemStack(Material.ELYTRA));
        ItemStack blade = new ItemBuilder(Material.NETHERITE_SWORD)
                .name("&4&lBlade of Infinity")
                .lore("&7Execute nearby enemies under 2 hearts.", "&cCooldown: 10 minutes unless it kills.")
                .glow(true)
                .setTag("role_item", "ARTOSH")
                .build();
        player.getInventory().addItem(blade);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.NETHERITE_SWORD && useBlade();
    }

    private boolean useBlade() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastBladeUse < BLADE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cBlade cooldown active.");
            return false;
        }
        lastBladeUse = now;
        boolean killed = false;
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby.getUniqueId().equals(playerId) || !plugin.getGameManager().isPlayerAlive(nearby.getUniqueId())) {
                continue;
            }
            if (!nearby.getWorld().equals(player.getWorld()) || nearby.getLocation().distance(player.getLocation()) > 5.0) {
                continue;
            }
            if (nearby.getHealth() <= 4.0) {
                nearby.getWorld().createExplosion(nearby.getLocation(), 0F, false);
                nearby.setHealth(0.0);
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2.0));
                killed = true;
            }
        }
        if (killed) {
            lastBladeUse = 0L;
            MessageUtil.sendMessage(player, "&aBlade kill confirmed. Cooldown instantly reset.");
            return true;
        }
        MessageUtil.sendMessage(player, "&eNo enemy was weak enough.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Artosh.",
                "Your goal is to win alone or with an alliance.",
                "You receive information about Schwi with a 40% chance it is wrong.",
                "Each kill grants Strength for 5 minutes."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive Elytra for mobility.",
                "Blade of Infinity executes nearby enemies under 2 hearts."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
