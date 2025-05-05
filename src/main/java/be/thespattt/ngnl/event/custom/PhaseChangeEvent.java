package be.thespattt.ngnl.event.custom;

import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.minigame.MiniGameType;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when the game phase changes
 */
public class PhaseChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameState oldState;
    private final GameState newState;
    private boolean cancelled = false;

    /**
     * Constructor
     *
     * @param oldState Previous game state
     * @param newState New game state
     */
    public PhaseChangeEvent(GameState oldState, GameState newState) {
        this.oldState = oldState;
        this.newState = newState;
    }

    /**
     * Get the old game state
     *
     * @return Previous game state
     */
    public GameState getOldState() {
        return oldState;
    }

    /**
     * Get the new game state
     *
     * @return New game state
     */
    public GameState getNewState() {
        return newState;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

