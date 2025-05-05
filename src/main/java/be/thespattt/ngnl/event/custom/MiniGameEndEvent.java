package be.thespattt.ngnl.event.custom;

import be.thespattt.ngnl.minigame.MiniGameType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when a mini-game ends
 */
public class MiniGameEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player1Id;
    private final UUID player2Id;
    private final MiniGameType miniGameType;
    private final boolean player1Winner;
    private final boolean player1WonPvP;

    /**
     * Constructor
     *
     * @param player1Id UUID of the first player
     * @param player2Id UUID of the second player
     * @param miniGameType Type of mini-game
     * @param player1Winner Whether player 1 won the mini-game
     * @param player1WonPvP Whether player 1 won the initial PvP
     */
    public MiniGameEndEvent(UUID player1Id, UUID player2Id, MiniGameType miniGameType,
                            boolean player1Winner, boolean player1WonPvP) {
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.miniGameType = miniGameType;
        this.player1Winner = player1Winner;
        this.player1WonPvP = player1WonPvP;
    }

    /**
     * Get the UUID of the first player
     *
     * @return Player 1 UUID
     */
    public UUID getPlayer1Id() {
        return player1Id;
    }

    /**
     * Get the UUID of the second player
     *
     * @return Player 2 UUID
     */
    public UUID getPlayer2Id() {
        return player2Id;
    }

    /**
     * Get the mini-game type
     *
     * @return MiniGameType
     */
    public MiniGameType getMiniGameType() {
        return miniGameType;
    }

    /**
     * Check if player 1 won the mini-game
     *
     * @return True if player 1 won
     */
    public boolean isPlayer1Winner() {
        return player1Winner;
    }

    /**
     * Check if player 1 won the initial PvP
     *
     * @return True if player 1 won PvP
     */
    public boolean isPlayer1WonPvP() {
        return player1WonPvP;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}