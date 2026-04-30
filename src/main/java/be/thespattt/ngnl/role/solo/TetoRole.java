package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
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

public class TetoRole extends Role {

    private static final int KING_COOLDOWN = 10 * 60;
    private long lastKingUse = 0L;
    private long attackLockUntil = 0L;
    private boolean reviveUsed = false;

    public TetoRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        List<Player> others = new ArrayList<>(Bukkit.getOnlinePlayers());
        others.removeIf(other -> other.getUniqueId().equals(playerId));
        if (!others.isEmpty()) {
            Player target = others.get(0);
            var ngnlTarget = plugin.getPlayerManager().getNGNLPlayer(target.getUniqueId());
            if (ngnlTarget != null && ngnlTarget.getRole() != null) {
                MessageUtil.sendMessage(player, "&eYou learned the role of &a" + target.getName() + "&e: &f" + ngnlTarget.getRole().getDisplayName());
            }
        }
    }

    public boolean canChooseMiniGame() {
        return true;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack king = new ItemBuilder(Material.NETHER_STAR)
                .name("&6&lKing's Piece")
                .lore("&710 seconds of invincibility.", "&cCosts 2 hearts and reveals you.")
                .glow(true)
                .setTag("role_item", "TETO")
                .build();
        ItemStack revive = new ItemBuilder(Material.TOTEM_OF_UNDYING)
                .name("&e&lRoyal Recall")
                .lore("&7Revive one eliminated player as your ally.")
                .glow(true)
                .setTag("role_item", "TETO")
                .build();
        player.getInventory().addItem(king, revive);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == Material.NETHER_STAR) {
            return useKingPiece();
        }
        if (item.getType() == Material.TOTEM_OF_UNDYING) {
            return useRoyalRecall();
        }
        return false;
    }

    private boolean useKingPiece() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastKingUse < KING_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cKing's Piece cooldown active.");
            return false;
        }
        if (player.getHealth() <= 4.0) {
            player.setHealth(0.0);
            return true;
        }
        lastKingUse = now;
        player.setHealth(player.getHealth() - 4.0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 10 * 20, 10, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 10 * 20, 0, false, false));
        attackLockUntil = System.currentTimeMillis() + 10_000L;
        MessageUtil.broadcast("&6Teto revealed their position with the King's Piece: "
                + player.getLocation().getBlockX() + ", "
                + player.getLocation().getBlockY() + ", "
                + player.getLocation().getBlockZ());
        return true;
    }

    private boolean useRoyalRecall() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || reviveUsed) {
            return false;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(playerId) || plugin.getGameManager().isPlayerAlive(online.getUniqueId())) {
                continue;
            }
            if (plugin.getGameManager().revivePlayer(online.getUniqueId(), player.getLocation(), 8.0)) {
                reviveUsed = true;
                NGNLPlayer self = getNGNLPlayer();
                NGNLPlayer ally = plugin.getPlayerManager().getNGNLPlayer(online.getUniqueId());
                if (self != null && ally != null) {
                    self.setAlliancePartner(online.getUniqueId());
                    ally.setAlliancePartner(playerId);
                }
                MessageUtil.broadcast("&eTeto revived " + online.getName() + " as an ally.");
                return true;
            }
        }
        MessageUtil.sendMessage(player, "&cNo eliminated player could be revived.");
        return false;
    }

    public boolean isAttackLocked() {
        return System.currentTimeMillis() < attackLockUntil;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Teto.",
                "Your goal is to win alone or with an alliance.",
                "You learn one player's role at the start.",
                "You always control mini-game selection when involved."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Royal Recall revives one player as your ally.",
                "King's Piece grants 10 seconds of invincibility every 10 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
