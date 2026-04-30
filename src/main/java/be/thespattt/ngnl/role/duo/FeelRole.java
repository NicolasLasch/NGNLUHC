package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class FeelRole extends DuoRole {

    private static final int ORACLE_COOLDOWN = 20 * 60;
    private boolean jointMiniGameUsed = false;
    private boolean jointMiniGameArmed = false;
    private boolean extraLifeUsed = false;
    private long lastOracleUse = 0L;

    public FeelRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player != null && partner != null) {
            MessageUtil.sendMessage(player, "&eKurami is: &a" + partner.getName());
        }
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player != null && !jointMiniGameUsed) {
            MessageUtil.sendMessage(player, "&eUse &a/duo together &eto link this mini-game to Kurami once per game.");
        }
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        if (isWinner) {
            jointMiniGameArmed = false;
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        lastOracleUse = 0L;
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&cKurami has been eliminated.");
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
        MessageUtil.sendMessage(player, "&aIf you lose your next mini-game, you and Kurami will both fall to 5 hearts.");
    }

    public boolean consumeExtraLife(MiniGameType miniGameType) {
        if (extraLifeUsed) {
            return false;
        }
        if (miniGameType != MiniGameType.ANVIL_RAIN && miniGameType != MiniGameType.FLOOR_IS_LAVA) {
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
        }
        if (partner != null) {
            partner.setHealth(Math.min(partner.getMaxHealth(), 10.0));
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack card = new ItemBuilder(Material.MAP)
                .name("&5&lFeel's Oracle Card")
                .lore("&7Teleports you to Kurami and grants invisibility for 30 seconds.")
                .glow(true)
                .setTag("role_item", "FEEL")
                .build();
        player.getInventory().addItem(card);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.MAP) {
            return useOracleCard();
        }
        return false;
    }

    private boolean useOracleCard() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player == null || partner == null || !isArenaPhaseActive()) {
            return false;
        }

        long now = System.currentTimeMillis() / 1000;
        if (now - lastOracleUse < ORACLE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cOracle Card cooldown: " + String.format("%d:%02d", (ORACLE_COOLDOWN - (now - lastOracleUse)) / 60, (ORACLE_COOLDOWN - (now - lastOracleUse)) % 60));
            return false;
        }
        lastOracleUse = now;
        player.teleport(partner.getLocation());
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30 * 20, 0, false, false));
        MessageUtil.sendMessage(player, "&aYou teleported to Kurami and became invisible for 30 seconds.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Feel Nilvalen.",
                "Your goal is to win with Kurami.",
                "Use /duo together once to link your next mini-game to Kurami.",
                "You negate one defeat in Anvil Rain or The Floor Is Lava."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive an Oracle Card in finale.",
                "It teleports you to Kurami and makes you invisible for 30 seconds.",
                "Cooldown: 20 minutes."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Kurami.";
    }
}
