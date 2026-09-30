package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Shiro: can give Sora a bonus twice per game at the launch of a mini-game.
 */
public class ShiroRole extends SoraShiroBase {

    /** Maximum number of bonuses Shiro can give during the game. */
    private static final int MAX_BONUSES = 2;
    /** Duration of the bonus effects in seconds. */
    private static final int BONUS_DURATION = 5 * 60;

    private int bonusesUsed = 0;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     */
    public ShiroRole(NoGameNoLife plugin, UUID playerId) {
        super(plugin, playerId, RoleType.SHIRO);
    }

    @Override
    protected String partnerName() {
        return "Sora";
    }

    @Override
    protected String crownName() {
        return "&f&lCouronne de Shiro";
    }

    @Override
    protected void onRoleSetup() {
        super.onRoleSetup();
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&eTu peux donner un bonus à Sora pendant un mini-jeu (&a" + MAX_BONUSES + " fois&e).");
        }
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null || getPartnerPlayer() == null || bonusesUsed >= MAX_BONUSES) {
            return;
        }
        MessageUtil.sendMessage(player, "&6Mini-jeu : &e" + miniGameType.getDisplayName());
        MessageUtil.sendMessage(player, "&aTu peux donner un bonus à Sora ! (&e" + (MAX_BONUSES - bonusesUsed) + " restant(s)&a)");
        MessageUtil.sendMessage(player, "&eUtilise &a/bonus &epour lui donner un avantage.");
    }

    /**
     * Give Sora Luck II and Speed I for a few minutes.
     *
     * @return True if the bonus was given
     */
    public boolean giveBonusToSora() {
        Player player = getPlayer();
        Player sora = getPartnerPlayer();
        if (player == null) {
            return false;
        }
        if (bonusesUsed >= MAX_BONUSES) {
            MessageUtil.sendMessage(player, "&cTu as déjà utilisé tous tes bonus !");
            return false;
        }
        if (sora == null) {
            MessageUtil.sendMessage(player, "&cSora n'est pas en ligne !");
            return false;
        }

        bonusesUsed++;
        sora.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, BONUS_DURATION * 20, 1, false, false));
        sora.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BONUS_DURATION * 20, 0, false, false));
        MessageUtil.sendMessage(player, "&aBonus donné à Sora ! (&e" + (MAX_BONUSES - bonusesUsed) + " restant(s)&a)");
        MessageUtil.sendMessage(sora, "&aShiro t'a donné un bonus ! (Chance II + Vitesse I pendant 5 minutes)");
        return true;
    }

    /**
     * Number of bonuses Shiro can still give.
     *
     * @return Remaining bonuses
     */
    public int getBonusesRemaining() {
        return MAX_BONUSES - bonusesUsed;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Shiro.",
                "Your goal is to win with Sora.",
                "At the launch of a mini-game you can give Sora a bonus",
                "(2 times per game, /bonus).",
                "You know Sora's identity and position from the start."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You get a crown that summons 5 clones around you for 10 seconds",
                "(every 20 minutes).",
                "Close to Sora (30 blocks): Resistance and Speed.",
                "Farther than 30 blocks: Weakness."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Sora.";
    }
}
