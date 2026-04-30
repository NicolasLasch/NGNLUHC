package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class OkeinRole extends Role {

    private static final int HAMMER_COOLDOWN = 15 * 60;
    private boolean firstDamageKnown = false;
    private long lastHammerUse = 0L;
    private int waterTaskId = -1;

    public OkeinRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.getInventory().addItem(new ItemStack(Material.ANVIL));
        player.getInventory().addItem(new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("&6&lFlame Book")
                .lore("&7A flame book granted by Okein.")
                .glow(true)
                .build());
        player.setLevel(player.getLevel() + 200);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        startWaterTask();
    }

    public void registerFirstDamagedPlayer(UUID targetId) {
        if (firstDamageKnown) {
            return;
        }
        firstDamageKnown = true;
        Player player = getPlayer();
        var target = plugin.getPlayerManager().getNGNLPlayer(targetId);
        if (player != null && target != null && target.getRole() != null) {
            MessageUtil.sendMessage(player, "&eFirst damaged player role: &f" + target.getRole().getDisplayName());
        }
    }

    private void startWaterTask() {
        if (waterTaskId != -1) {
            Bukkit.getScheduler().cancelTask(waterTaskId);
        }
        waterTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            if (player == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
                return;
            }
            Block feet = player.getLocation().getBlock();
            if (feet.isLiquid() || player.isInWater()) {
                player.damage(2.0);
                MessageUtil.sendMessage(player, "&cWater burns you for 1 heart.");
            }
        }, 20L, 20L);
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack hammer = new ItemBuilder(Material.IRON_AXE)
                .name("&8&lHammer of Destruction")
                .lore("&7Drops anvils on a 5x5 area.", "&cCooldown: 15 minutes")
                .glow(true)
                .setTag("role_item", "OKEIN")
                .build();
        player.getInventory().addItem(hammer);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.IRON_AXE && useHammer();
    }

    private boolean useHammer() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastHammerUse < HAMMER_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cHammer cooldown active.");
            return false;
        }
        lastHammerUse = now;
        var center = player.getTargetBlockExact(30);
        if (center == null) {
            center = player.getLocation().getBlock();
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                FallingBlock anvil = player.getWorld().spawnFallingBlock(center.getLocation().clone().add(x, 12, z), Material.ANVIL.createBlockData());
                anvil.setDropItem(false);
                anvil.setHurtEntities(true);
            }
        }
        MessageUtil.broadcast("&8Okein called an anvil storm.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Okein.",
                "Your goal is to win alone or with an alliance.",
                "You learn the role of the first player to take damage.",
                "You receive an anvil, 200 levels and permanent fire resistance."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive the Hammer of Destruction in finale.",
                "It summons an anvil rain on a 5x5 area every 15 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
