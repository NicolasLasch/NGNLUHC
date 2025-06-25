// File: OracleCardClasses.java
package be.thespattt.ngnl.minigame.games.addons;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Oracle Card Game - All supporting classes and enums
 * Contains: OracleCard, CombinationResult, ScrollCombination, StatusEffect,
 * EffectType enum, Element enum, CardType enum, and ScrollCombinations database
 */

/**
 * Represents an Oracle Card in the Oracle Card Mini-Game
 * Based on the 22 Major Arcana with Element, Type, Power, and Effect attributes
 */
public class OracleCard {
    private final String name;
    private final Element element;
    private final CardType type;
    private final int power;
    private final String effect;

    public OracleCard(String name, Element element, CardType type, int power, String effect) {
        this.name = name;
        this.element = element;
        this.type = type;
        this.power = power;
        this.effect = effect;
    }

    // Getters
    public String getName() { return name; }
    public Element getElement() { return element; }
    public CardType getType() { return type; }
    public int getPower() { return power; }
    public String getEffect() { return effect; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        OracleCard that = (OracleCard) obj;
        return power == that.power &&
                Objects.equals(name, that.name) &&
                element == that.element &&
                type == that.type &&
                Objects.equals(effect, that.effect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, element, type, power, effect);
    }

    @Override
    public String toString() {
        return name + " (" + element + ", Power: " + power + ", Effect: " + effect + ")";
    }
}