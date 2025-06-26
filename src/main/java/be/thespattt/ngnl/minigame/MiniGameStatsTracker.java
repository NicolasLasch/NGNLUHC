package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;

import java.util.*;
import java.util.stream.Collectors;

public class MiniGameStatsTracker {

    private final NoGameNoLife plugin;
    private final Map<UUID, Integer> playerWins = new HashMap<>();
    private final Map<UUID, Integer> playerLosses = new HashMap<>();
    private final Map<UUID, List<MiniGameType>> playerWonGames = new HashMap<>();
    private final Map<UUID, List<MiniGameType>> playerLostGames = new HashMap<>();

    public MiniGameStatsTracker(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    public void recordWin(UUID playerId, MiniGameType gameType) {
        playerWins.put(playerId, playerWins.getOrDefault(playerId, 0) + 1);
        playerWonGames.computeIfAbsent(playerId, k -> new ArrayList<>()).add(gameType);

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (ngnlPlayer != null) {
            ngnlPlayer.incrementMiniGamesWon();
        }
    }

    public void recordLoss(UUID playerId, MiniGameType gameType) {
        playerLosses.put(playerId, playerLosses.getOrDefault(playerId, 0) + 1);
        playerLostGames.computeIfAbsent(playerId, k -> new ArrayList<>()).add(gameType);

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (ngnlPlayer != null) {
            ngnlPlayer.incrementMiniGamesLost();
        }
    }

    public void cancelLastLoss(UUID playerId) {
        int losses = playerLosses.getOrDefault(playerId, 0);
        if (losses > 0) {
            playerLosses.put(playerId, losses - 1);

            List<MiniGameType> lostGames = playerLostGames.get(playerId);
            if (lostGames != null && !lostGames.isEmpty()) {
                lostGames.remove(lostGames.size() - 1);
            }

            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
            if (ngnlPlayer != null) {
                ngnlPlayer.decrementMiniGamesLost();
            }
        }
    }

    public int getWins(UUID playerId) {
        return playerWins.getOrDefault(playerId, 0);
    }

    public int getLosses(UUID playerId) {
        return playerLosses.getOrDefault(playerId, 0);
    }

    public List<MiniGameType> getWonGames(UUID playerId) {
        return new ArrayList<>(playerWonGames.getOrDefault(playerId, new ArrayList<>()));
    }

    public List<MiniGameType> getLostGames(UUID playerId) {
        return new ArrayList<>(playerLostGames.getOrDefault(playerId, new ArrayList<>()));
    }

    public UUID getTopWinner() {
        return playerWins.entrySet().stream()
                .filter(entry -> plugin.getGameManager().isPlayerAlive(entry.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public List<UUID> getTopWinners(int count) {
        return playerWins.entrySet().stream()
                .filter(entry -> plugin.getGameManager().isPlayerAlive(entry.getKey()))
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public Map<UUID, Integer> getAllWins() {
        return new HashMap<>(playerWins);
    }

    public void clearStats() {
        playerWins.clear();
        playerLosses.clear();
        playerWonGames.clear();
        playerLostGames.clear();
    }

    public double getWinRate(UUID playerId) {
        int wins = getWins(playerId);
        int losses = getLosses(playerId);
        int total = wins + losses;

        if (total == 0) return 0.0;
        return (double) wins / total;
    }
}