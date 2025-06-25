package be.thespattt.ngnl.minigame.games.addons;

/**
 * Result of resolving a card combination
 */
public class CombinationResult {
    public String name;
    public int damage;
    public EffectType effectType;
    public String description;

    public CombinationResult(String name, int damage, EffectType effectType, String description) {
        this.name = name;
        this.damage = damage;
        this.effectType = effectType;
        this.description = description;
    }

    public String getName() { return name; }
    public int getDamage() { return damage; }
    public EffectType getEffectType() { return effectType; }
    public String getDescription() { return description; }
}