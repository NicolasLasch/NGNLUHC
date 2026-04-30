package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class IvanRole extends DuoRole {

    private int footprintTaskId = -1;

    public IvanRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        List<Player> shown = new ArrayList<>();
        Player nonna = getPartnerPlayer();
        if (nonna != null) {
            shown.add(nonna);
        }

        UUID rikuId = plugin.getRoleManager().getPlayerByRole(RoleType.RIKU);
        Player riku = rikuId != null ? Bukkit.getPlayer(rikuId) : null;
        if (riku != null) {
            shown.add(riku);
        }

        if (shown.size() == 2 && ThreadLocalRandom.current().nextInt(100) < 10) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(playerId) && shown.stream().noneMatch(p -> p.getUniqueId().equals(online.getUniqueId()))) {
                    shown.set(ThreadLocalRandom.current().nextInt(2), online);
                    break;
                }
            }
        }

        MessageUtil.sendMessage(player, "&eAmong these players you have Riku and Nonna:");
        shown.forEach(target -> MessageUtil.sendMessage(player, "&f- " + target.getName()));
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        if (miniGameType == MiniGameType.SUMO) {
            Player player = getPlayer();
            if (player != null) {
                MessageUtil.sendMessage(player, "&eYou receive your Knockback stick for this Sumo round.");
            }
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        startFootprintTask();
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack helmet = new ItemBuilder(Material.IRON_HELMET)
                .name("&7&lID Helmet")
                .lore("&7Wear it to reveal nearby footprints.", "&7Protection I")
                .glow(true)
                .setTag("role_item", "IVAN")
                .build();
        player.getInventory().addItem(helmet);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return false;
    }

    private void startFootprintTask() {
        if (footprintTaskId != -1) {
            Bukkit.getScheduler().cancelTask(footprintTaskId);
        }

        footprintTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            if (player == null || !plugin.getGameManager().isPlayerAlive(playerId)) {
                return;
            }
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet == null || helmet.getType() != Material.IRON_HELMET) {
                return;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.getUniqueId().equals(playerId) || !plugin.getGameManager().isPlayerAlive(target.getUniqueId())) {
                    continue;
                }
                if (target.getWorld().equals(player.getWorld()) && target.getLocation().distance(player.getLocation()) <= 35.0) {
                    target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 5, 0.25, 0.1, 0.25, 0.01);
                }
            }
        }, 20L, 20L);
    }

    @Override
    public void onDeath(UUID killerId) {
        super.onDeath(killerId);
        if (footprintTaskId != -1) {
            Bukkit.getScheduler().cancelTask(footprintTaskId);
            footprintTaskId = -1;
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Ivan Zell.",
                "Your goal is to win with Nonna.",
                "In Sumo, you receive a Knockback stick every round.",
                "You know Riku and Nonna, with a 10% risk of bad information."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive the ID Helmet in finale.",
                "When worn, it reveals nearby footprints."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Nonna.";
    }
}
