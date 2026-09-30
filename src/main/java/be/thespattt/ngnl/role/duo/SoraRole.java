package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Sora: can ask Shiro to play a mini-game in his place, knows Shiro from the start.
 */
public class SoraRole extends SoraShiroBase {

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     */
    public SoraRole(NoGameNoLife plugin, UUID playerId) {
        super(plugin, playerId, RoleType.SORA);
    }

    @Override
    protected String partnerName() {
        return "Shiro";
    }

    @Override
    protected String crownName() {
        return "&6&lCouronne de Sora";
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        MessageUtil.sendMessage(player, "&6Mini-jeu : &e" + miniGameType.getDisplayName());
        MessageUtil.sendMessage(player, "&6Tu as 30 secondes pour demander à Shiro d'y aller à ta place !");
        MessageUtil.sendMessage(player, "&eUtilise &a/substitute &epour lui demander de jouer pour toi.");
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Sora.",
                "Your goal is to win with Shiro.",
                "When a mini-game starts you have 30 seconds to ask Shiro",
                "to play in your place (/substitute).",
                "You know Shiro's identity and position from the start."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You get a crown that summons 5 clones around you for 10 seconds",
                "(every 20 minutes).",
                "Close to Shiro (30 blocks): Resistance and Speed.",
                "Farther than 30 blocks: Weakness."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Shiro.";
    }
}
