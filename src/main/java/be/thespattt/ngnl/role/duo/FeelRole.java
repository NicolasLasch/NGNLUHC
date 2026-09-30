package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Feel: second life in Anvil Rain and The Floor Is Lava; Oracle Card in the finale
 * (teleport to Kurami, invisible for 30 seconds).
 */
public class FeelRole extends KuramiFeelBase {

    /** Cooldown of the Oracle Card in seconds. */
    private static final int ORACLE_COOLDOWN = 20 * 60;
    /** Invisibility duration in seconds. */
    private static final int INVISIBILITY_SECONDS = 30;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (FEEL)
     */
    public FeelRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected Set<MiniGameType> secondLifeGames() {
        return EnumSet.of(MiniGameType.ANVIL_RAIN, MiniGameType.FLOOR_IS_LAVA);
    }

    @Override
    protected String partnerName() {
        return "Kurami";
    }

    @Override
    protected void onRoleSetup() {
        // Feel does not learn Kurami directly: the three-name list is sent with the role.
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("oracle_card");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.MAP, "&5&lOracle Card de Feel",
                "&7Te téléporte auprès de Kurami, invisible pendant 30 secondes.", "",
                "&eClic droit pour activer", "&cRecharge : 20 minutes"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null || item.getType() != Material.MAP) {
            return false;
        }
        return useOracleCard();
    }

    /**
     * Teleport to Kurami and become invisible.
     *
     * @return True if the card was used
     */
    private boolean useOracleCard() {
        Player player = getPlayer();
        Player partner = getPartnerPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        if (partner == null || !isPartnerAlive()) {
            MessageUtil.sendMessage(player, "&cKurami n'est pas disponible.");
            return false;
        }
        if (!tryUseCooldown("oracle_card", ORACLE_COOLDOWN)) {
            return false;
        }
        player.teleport(partner.getLocation());
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, INVISIBILITY_SECONDS * 20, 0, false, false));
        MessageUtil.sendMessage(player, "&aTu t'es téléporté auprès de Kurami et tu es invisible " + INVISIBILITY_SECONDS + " secondes.");
        return true;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Feel Nilvalen.",
                "Your goal is to win with Kurami.",
                "Once per game you can link a mini-game with Kurami (/duo together):",
                "if you lose it, both of you fall to 5 hearts.",
                "You have two lives in Anvil Rain and The Floor Is Lava."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You get an Oracle Card: teleport to Kurami, invisible for 30 seconds",
                "(every 20 minutes)."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Kurami.";
    }
}
