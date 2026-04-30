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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class KuramiRole extends DuoRole {

    private static final int ORACLE_COOLDOWN = 20 * 60;
    private boolean jointMiniGameUsed = false;
    private boolean jointMiniGameArmed = false;
    private boolean extraLifeUsed = false;
    private long lastOracleUse = 0L;

    public KuramiRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player != null && partner != null) {
            MessageUtil.sendMessage(player, "&eFeel is: &a" + partner.getName());
        }
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player != null && !jointMiniGameUsed) {
            MessageUtil.sendMessage(player, "&eUse &a/duo together &eto link this mini-game to Feel once per game.");
        }
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        if (isWinner) {
            jointMiniGameArmed = false;
        }
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&cFeel has been eliminated.");
        }
    }

    public void armJointMiniGame() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        if (jointMiniGameUsed) {
            MessageUtil.sendMessage(player, "&cYou already used this ability.");
            return;
        }
        jointMiniGameUsed = true;
        jointMiniGameArmed = true;
        MessageUtil.sendMessage(player, "&aIf you lose your next mini-game, you and Feel will both fall to 5 hearts.");
        Player partner = getPartnerPlayer();
        if (partner != null) {
            MessageUtil.sendMessage(partner, "&eKurami linked the next mini-game to both of you.");
        }
    }

    public boolean consumeExtraLife(MiniGameType miniGameType) {
        if (extraLifeUsed) {
            return false;
        }
        if (miniGameType != MiniGameType.DES_A_COUDRE && miniGameType != MiniGameType.BLOC_PARTY) {
            return false;
        }
        extraLifeUsed = true;
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&aYour bonus negated the consequences of this defeat.");
        }
        return true;
    }

    public void handleJointLossIfNeeded() {
        if (!jointMiniGameArmed) {
            return;
        }
        jointMiniGameArmed = false;
        Player self = getPlayer();
        Player partner = getPartnerPlayer();
        if (self != null) {
            self.setHealth(Math.min(self.getMaxHealth(), 10.0));
            MessageUtil.sendMessage(self, "&cYou fall to 5 hearts because of the shared mini-game risk.");
        }
        if (partner != null) {
            partner.setHealth(Math.min(partner.getMaxHealth(), 10.0));
            MessageUtil.sendMessage(partner, "&cYou fall to 5 hearts because of Kurami's shared mini-game risk.");
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastOracleUse = 0L;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack card = new ItemBuilder(Material.PAPER)
                .name("&5&lOracle Card")
                .lore("&7Teleports two non-combat players together.", "&c20% chance to replace one target.")
                .glow(true)
                .setTag("role_item", "KURAMI")
                .build();
        player.getInventory().addItem(card);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.PAPER) {
            return useOracleCard();
        }
        return false;
    }

    private boolean useOracleCard() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }

        long now = System.currentTimeMillis() / 1000;
        if (now - lastOracleUse < ORACLE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cOracle Card cooldown: " + formatTime(ORACLE_COOLDOWN - (now - lastOracleUse)));
            return false;
        }

        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.getGameManager().isPlayerAlive(p.getUniqueId()))
                .filter(p -> !plugin.getCombatTracker().isInCombat(p.getUniqueId()))
                .filter(p -> !p.getUniqueId().equals(playerId))
                .toList());

        if (candidates.size() < 2) {
            MessageUtil.sendMessage(player, "&cNot enough valid players to teleport.");
            return false;
        }

        Player first = candidates.get(0);
        Player second = candidates.get(1);
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            first = player;
        }

        var firstLocation = first.getLocation().clone();
        var secondLocation = second.getLocation().clone();
        first.teleport(secondLocation);
        second.teleport(firstLocation);
        lastOracleUse = now;
        MessageUtil.broadcast("&5Kurami used an Oracle Card.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Kurami Zell.",
                "Your goal is to win with Feel.",
                "Use /duo together once to link your next mini-game to Feel.",
                "You negate one defeat in Bloc Party or Des a Coudre."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive an Oracle Card in finale.",
                "It teleports two non-combat players together every 20 minutes.",
                "There is a 20% risk that you replace one target."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Feel.";
    }

    private String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
