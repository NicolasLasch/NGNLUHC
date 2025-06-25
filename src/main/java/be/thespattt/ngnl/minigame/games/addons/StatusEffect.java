package be.thespattt.ngnl.minigame.games.addons;

/**
 * Status effects that can be applied to players
 */
public class StatusEffect {
    public String name;
    public int duration;
    public int damagePerTurn;

    public StatusEffect(String name, int duration, int damagePerTurn) {
        this.name = name;
        this.duration = duration;
        this.damagePerTurn = damagePerTurn;
    }

    public String getName() { return name; }
    public int getDuration() { return duration; }
    public int getDamagePerTurn() { return damagePerTurn; }

    public void decreaseDuration() {
        this.duration = Math.max(0, this.duration - 1);
    }

    public boolean isExpired() {
        return duration <= 0;
    }
}