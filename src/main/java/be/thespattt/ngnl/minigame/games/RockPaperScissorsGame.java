package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.command.commands.RockPaperScissorsCommand.PlayerGameChecker;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Class representing a rock-paper-scissors game between two players
 */
public class RockPaperScissorsGame implements PlayerGameChecker {
    private final NoGameNoLife plugin;
    private final Player player1;
    private final Player player2;
    private final String wager;

    private final Map<UUID, String> choices = new HashMap<>();
    private int taskId = -1;
    private int timeLeft = 30; // 30 seconds to make a choice

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player1 First player
     * @param player2 Second player
     * @param wager Wager for the game
     */
    public RockPaperScissorsGame(NoGameNoLife plugin, Player player1, Player player2, String wager) {
        this.plugin = plugin;
        this.player1 = player1;
        this.player2 = player2;
        this.wager = wager;
    }

    /**
     * Start the game
     */
    public void start() {
        // Send instructions
        MessageUtil.sendMessage(player1, "&6=== Rock-Paper-Scissors Challenge ===");
        MessageUtil.sendMessage(player1, "&eClick one of the following to make your choice:");
        MessageUtil.sendMessage(player1, "&a[Rock] &b[Paper] &d[Scissors]");

        MessageUtil.sendMessage(player2, "&6=== Rock-Paper-Scissors Challenge ===");
        MessageUtil.sendMessage(player2, "&eClick one of the following to make your choice:");
        MessageUtil.sendMessage(player2, "&a[Rock] &b[Paper] &d[Scissors]");

        // For now, use commands
        MessageUtil.sendMessage(player1, "&eOr type: &a/rps rock&e, &b/rps paper&e, or &d/rps scissors");
        MessageUtil.sendMessage(player2, "&eOr type: &a/rps rock&e, &b/rps paper&e, or &d/rps scissors");

        // Start timer
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    /**
     * Handle player choice
     *
     * @param player Player making the choice
     * @param choice Choice (rock, paper, or scissors)
     */
    public void makeChoice(Player player, String choice) {
        if (!player.equals(player1) && !player.equals(player2)) {
            return;
        }

        // Validate choice
        if (!choice.equals("rock") && !choice.equals("paper") && !choice.equals("scissors")) {
            MessageUtil.sendMessage(player, "&cInvalid choice. Please choose rock, paper, or scissors.");
            return;
        }

        // Record choice
        choices.put(player.getUniqueId(), choice);
        MessageUtil.sendMessage(player, "&aYou chose: &e" + choice);

        // Check if both players have made choices
        if (choices.size() == 2) {
            end();
        }
    }

    /**
     * Timer tick
     */
    private void tick() {
        timeLeft--;

        // Update time for players
        if (timeLeft % 5 == 0 || timeLeft <= 5) {
            for (Player player : Arrays.asList(player1, player2)) {
                if (!choices.containsKey(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, "&eTime remaining: &c" + timeLeft + " seconds");
                }
            }
        }

        // Check if time is up
        if (timeLeft <= 0) {
            end();
        }
    }

    /**
     * End the game and determine the winner
     */
    private void end() {
        // Cancel timer
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // Check if both players made choices
        if (choices.size() < 2) {
            // Handle default choices for players who didn't choose
            if (!choices.containsKey(player1.getUniqueId())) {
                choices.put(player1.getUniqueId(), "rock");
                MessageUtil.sendMessage(player1, "&cTime's up! Default choice: &erock");
            }

            if (!choices.containsKey(player2.getUniqueId())) {
                choices.put(player2.getUniqueId(), "rock");
                MessageUtil.sendMessage(player2, "&cTime's up! Default choice: &erock");
            }
        }

        // Get choices
        String choice1 = choices.get(player1.getUniqueId());
        String choice2 = choices.get(player2.getUniqueId());

        // Determine winner
        Player winner = null;
        Player loser = null;

        if (choice1.equals(choice2)) {
            // Tie - random winner
            if (Math.random() < 0.5) {
                winner = player1;
                loser = player2;
            } else {
                winner = player2;
                loser = player1;
            }

            MessageUtil.broadcast("&6" + player1.getName() + " chose " + choice1 + " and " +
                    player2.getName() + " chose " + choice2 + "!");
            MessageUtil.broadcast("&6It's a tie! But in No Game No Life, there are no ties...");
            MessageUtil.broadcast("&6Random winner: &e" + winner.getName() + "&6!");
        } else if ((choice1.equals("rock") && choice2.equals("scissors")) ||
                (choice1.equals("paper") && choice2.equals("rock")) ||
                (choice1.equals("scissors") && choice2.equals("paper"))) {
            // Player 1 wins
            winner = player1;
            loser = player2;

            MessageUtil.broadcast("&6" + player1.getName() + " chose " + choice1 + " and " +
                    player2.getName() + " chose " + choice2 + "!");
            MessageUtil.broadcast("&6Winner: &e" + winner.getName() + "&6!");
        } else {
            // Player 2 wins
            winner = player2;
            loser = player1;

            MessageUtil.broadcast("&6" + player1.getName() + " chose " + choice1 + " and " +
                    player2.getName() + " chose " + choice2 + "!");
            MessageUtil.broadcast("&6Winner: &e" + winner.getName() + "&6!");
        }

        // Apply wager consequences
        applyWagerResult(winner, loser);

        // Unregister game from command
        plugin.getCommandManager().getRpsCommand().unregisterGame(player1.getUniqueId().toString() + "-" + player2.getUniqueId().toString());
    }

    /**
     * Apply the results of the wager
     *
     * @param winner Winner of the challenge
     * @param loser Loser of the challenge
     */
    private void applyWagerResult(Player winner, Player loser) {
        switch (wager) {
            case "diamonds":
                // Implementation would depend on how you want to handle resource transfer
                MessageUtil.sendMessage(winner, "&aYou won the challenge! " + loser.getName() +
                        " owes you 3 diamonds.");
                MessageUtil.sendMessage(loser, "&cYou lost the challenge! You owe " +
                        winner.getName() + " 3 diamonds.");
                break;

            case "levels":
                // Transfer 5 XP levels from loser to winner
                int loserLevels = Math.min(5, loser.getLevel());
                loser.setLevel(loser.getLevel() - loserLevels);
                winner.setLevel(winner.getLevel() + loserLevels);

                MessageUtil.sendMessage(winner, "&aYou won the challenge! You received " +
                        loserLevels + " XP levels from " + loser.getName() + ".");
                MessageUtil.sendMessage(loser, "&cYou lost the challenge! You lost " +
                        loserLevels + " XP levels to " + winner.getName() + ".");
                break;

            case "hearts":
                // Remove 1 heart (2 health points) from loser
                NGNLPlayer loserNGNLPlayer = plugin.getPlayerManager().getNGNLPlayer(loser.getUniqueId());
                if (loserNGNLPlayer != null) {
                    plugin.getGameManager().removePlayerHearts(loser.getUniqueId(), 1);

                    MessageUtil.sendMessage(winner, "&aYou won the challenge! " + loser.getName() +
                            " lost 1 heart.");
                    MessageUtil.sendMessage(loser, "&cYou lost the challenge! You lost 1 heart.");
                }
                break;

            case "information":
                // Winner can ask a question that loser must answer truthfully
                MessageUtil.sendMessage(winner, "&aYou won the challenge! You can ask " +
                        loser.getName() + " one question that they must answer truthfully.");
                MessageUtil.sendMessage(loser, "&cYou lost the challenge! You must answer one question from " +
                        winner.getName() + " truthfully.");
                break;
        }

        // Broadcast result
        MessageUtil.broadcast("&6Challenge between " + winner.getName() + " and " +
                loser.getName() + " has ended!");
        MessageUtil.broadcast("&6" + winner.getName() + " has won the challenge with wager: " + wager + "!");
    }

    @Override
    public boolean hasPlayer(UUID playerId) {
        return player1.getUniqueId().equals(playerId) || player2.getUniqueId().equals(playerId);
    }

    /**
     * Get player 1
     *
     * @return Player 1
     */
    public Player getPlayer1() {
        return player1;
    }

    /**
     * Get player 2
     *
     * @return Player 2
     */
    public Player getPlayer2() {
        return player2;
    }

    /**
     * Get the wager
     *
     * @return Wager
     */
    public String getWager() {
        return wager;
    }
}