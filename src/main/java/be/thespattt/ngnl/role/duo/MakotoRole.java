package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MakotoRole extends DuoRole {

    private boolean hasUsedDefeatCancellation = false;

    public MakotoRole(NoGameNoLife plugin, UUID playerId) {
        super(plugin, playerId, RoleType.MAKOTO);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) return;

        MessageUtil.sendMessage(player, "&eYou can cancel one mini-game defeat per game.");
        MessageUtil.sendMessage(player, "&eUse &a/duo cancel &ewhen you lose a mini-game to activate this.");
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) return;

        MessageUtil.sendMessage(player, "&6Mini-game starting: &e" + miniGameType.getDisplayName());

        if (!hasUsedDefeatCancellation) {
            MessageUtil.sendMessage(player, "&eRemember: You can cancel a defeat with &a/duo cancel&e!");
        }
    }

    @Override
    public void onMiniGameEnd(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        Player player = getPlayer();
        if (player == null) return;

        if (!isWinner && !hasUsedDefeatCancellation) {
            MessageUtil.sendMessage(player, "&cYou lost the mini-game!");
            MessageUtil.sendMessage(player, "&eYou have 60 seconds to use &a/duo cancel &eto reverse this defeat!");
        }
    }

    public boolean cancelDefeat() {
        if (hasUsedDefeatCancellation) {
            Player player = getPlayer();
            if (player != null) {
                MessageUtil.sendMessage(player, "&cYou have already used your defeat cancellation!");
            }
            return false;
        }

        hasUsedDefeatCancellation = true;

        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&aYou cancelled your mini-game defeat!");
            MessageUtil.broadcast("&6" + player.getName() + " cancelled their mini-game defeat!");
        }

        return true;
    }

    public boolean hasUsedDefeatCancellation() {
        return hasUsedDefeatCancellation;
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();

        // Check if Stephanie is alive
        UUID stephanieUUID = getPartnerUUID();
        if (stephanieUUID != null && !plugin.getGameManager().isPlayerAlive(stephanieUUID)) {
            autoAllianceWithTopPlayer();
        }
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null) return;

        MessageUtil.sendMessage(player, "&c&lStephanie has been eliminated!");

        if (isArenaPhaseActive()) {
            autoAllianceWithTopPlayer();
        } else {
            MessageUtil.sendMessage(player, "&eIf the arena phase starts, you will be allied with the top mini-game winner.");
        }
    }

    private void autoAllianceWithTopPlayer() {
        Player player = getPlayer();
        if (player == null) return;

        UUID topPlayerId = findTopMiniGameWinner();
        if (topPlayerId == null || topPlayerId.equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&eNo suitable alliance partner found.");
            return;
        }

        Player topPlayer = Bukkit.getPlayer(topPlayerId);
        if (topPlayer == null) {
            MessageUtil.sendMessage(player, "&eTop mini-game winner is not online.");
            return;
        }

        // Create alliance
        NGNLPlayer makotoNGNL = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        NGNLPlayer topNGNL = plugin.getPlayerManager().getNGNLPlayer(topPlayerId);

        if (makotoNGNL != null && topNGNL != null) {
            makotoNGNL.setAlliancePartner(topPlayerId);
            topNGNL.setAlliancePartner(player.getUniqueId());

            MessageUtil.sendMessage(player, "&aYou are now allied with &e" + topPlayer.getName() + "&a (top mini-game winner)!");
            MessageUtil.sendMessage(topPlayer, "&aMakoto has formed an alliance with you due to Stephanie's death!");
            MessageUtil.broadcast("&6" + player.getName() + " and " + topPlayer.getName() + " have formed an emergency alliance!");
        }
    }

    private UUID findTopMiniGameWinner() {
        UUID topPlayer = null;
        int maxWins = 0;

        for (NGNLPlayer ngnlPlayer : plugin.getPlayerManager().getAllNGNLPlayers()) {
            if (plugin.getGameManager().isPlayerAlive(ngnlPlayer.getPlayerId()) &&
                    !ngnlPlayer.getPlayerId().equals(this.playerId)) {

                int wins = ngnlPlayer.getMiniGamesWon();
                if (wins > maxWins) {
                    maxWins = wins;
                    topPlayer = ngnlPlayer.getPlayerId();
                }
            }
        }

        return topPlayer;
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        // Makoto doesn't get special items in arena phase
        MessageUtil.sendMessage(player, "&eYou don't receive special items, but your alliance strategy is your strength!");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return false; // Makoto has no special items
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Makoto Dola.",
                "Your goal is to win with Stephanie.",
                "Once per game, you can cancel a mini-game defeat.",
                "You have no special effects during mini-games."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "If Stephanie dies during the arena phase,",
                "you will automatically form an alliance with",
                "the player who has won the most mini-games."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Stephanie.";
    }
}