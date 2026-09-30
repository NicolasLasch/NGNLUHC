package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared behaviour of Kurami and Feel:
 * - once per game they can link a mini-game: losing it drops BOTH of them to 5 hearts;
 * - each of them owns a second life in two specific mini-games;
 * - each of them owns an Oracle Card in the finale (different effect).
 */
public abstract class KuramiFeelBase extends DuoRole {

    /** Maximum hearts of both partners after a lost linked mini-game. */
    private static final double LINKED_LOSS_HEARTS = 5.0;

    private final Set<MiniGameType> usedSecondLives = new HashSet<>();
    private boolean jointMiniGameUsed = false;
    private boolean jointMiniGameArmed = false;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Kurami or Feel
     */
    protected KuramiFeelBase(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    /**
     * Mini-games in which this role owns a second life.
     *
     * @return Set of mini-game types
     */
    protected abstract Set<MiniGameType> secondLifeGames();

    /**
     * Display name of the partner for messages.
     *
     * @return Partner name
     */
    protected abstract String partnerName();

    /**
     * Check if the role owns a second life in a mini-game.
     *
     * @param miniGameType Type of mini-game
     * @return True if the role gets two lives in it
     */
    public boolean hasSecondLife(MiniGameType miniGameType) {
        return secondLifeGames().contains(miniGameType);
    }

    /**
     * Spend the second life after a defeat: the mini-game is replayed instead of being lost.
     * The second life is available once per mini-game type the role owns it in.
     *
     * @param miniGameType Type of the lost mini-game
     * @return True if the defeat is absorbed
     */
    public boolean consumeSecondLife(MiniGameType miniGameType) {
        if (!hasSecondLife(miniGameType) || usedSecondLives.contains(miniGameType)) {
            return false;
        }
        usedSecondLives.add(miniGameType);
        return true;
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        if (hasSecondLife(miniGameType)) {
            MessageUtil.sendMessage(player, "&aTu disposes de &e2 vies &adans ce mini-jeu !");
        }
        if (!jointMiniGameUsed) {
            MessageUtil.sendMessage(player, "&eUtilise &a/duo together &epour lier ce mini-jeu à " + partnerName() + " (une fois par partie).");
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
            MessageUtil.sendMessage(player, "&c" + partnerName() + " a été éliminé(e).");
        }
    }

    /**
     * Arm the linked mini-game: if the next mini-game is lost, both partners fall to 5 hearts.
     */
    public void armJointMiniGame() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        if (jointMiniGameUsed) {
            MessageUtil.sendMessage(player, "&cTu as déjà utilisé cette capacité.");
            return;
        }
        jointMiniGameUsed = true;
        jointMiniGameArmed = true;
        MessageUtil.sendMessage(player, "&aSi tu perds ton prochain mini-jeu, toi et " + partnerName() + " tomberez à 5 cœurs.");
        Player partner = getPartnerPlayer();
        if (partner != null) {
            MessageUtil.sendMessage(partner, "&e" + getDisplayName() + " a lié le prochain mini-jeu à vous deux.");
        }
    }

    /**
     * Apply the linked loss: both partners' maximum health drops to 5 hearts (if above).
     */
    public void handleJointLossIfNeeded() {
        if (!jointMiniGameArmed) {
            return;
        }
        jointMiniGameArmed = false;
        capMaxHearts(getPlayerId());
        UUID partnerId = getPartnerUUID();
        if (partnerId != null) {
            capMaxHearts(partnerId);
        }
    }

    /**
     * Reduce a player's maximum health to 5 hearts when it is higher.
     *
     * @param targetId UUID of the player
     */
    private void capMaxHearts(UUID targetId) {
        NGNLPlayer target = plugin.getPlayerManager().getNGNLPlayer(targetId);
        if (target == null || !plugin.getGameManager().isPlayerAlive(targetId)) {
            return;
        }
        double capped = Math.min(target.getMaxHealth(), LINKED_LOSS_HEARTS * 2);
        target.setMaxHealth(capped);
        Player player = target.getPlayer();
        if (player != null) {
            player.setHealth(Math.min(player.getHealth(), capped));
            MessageUtil.sendMessage(player, "&cTu tombes à 5 cœurs à cause du mini-jeu lié.");
        }
    }
}
