package be.thespattt.ngnl.minigame.games.addons;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Database of all scroll combinations
 */
public class ScrollCombinations {
    private static final Map<String, ScrollCombination> combinations = new HashMap<>();

    static {
        // OFFENSIVE SCROLLS - Fire + Fire combinations
        combinations.put("The Emperor+The Magician", new ScrollCombination("Flame Strike", 15, EffectType.DAMAGE, "Fire magic burns bright"));
        combinations.put("The Chariot+The Devil", new ScrollCombination("Flame Strike", 16, EffectType.DAMAGE, "Corrupt charge"));
        combinations.put("The Emperor+The Sun", new ScrollCombination("Flame Strike", 17, EffectType.DAMAGE, "Imperial radiance"));
        combinations.put("The Sun+The Tower", new ScrollCombination("Flame Strike", 18, EffectType.DAMAGE, "Solar destruction"));

        // Earth + Earth combinations
        combinations.put("Strength+The Empress", new ScrollCombination("Earth Crush", 12, EffectType.HEAL, "Nature's strength heals 3 HP"));
        combinations.put("The Hierophant+The World", new ScrollCombination("Earth Crush", 10, EffectType.SHIELD, "Wisdom protects next turn"));
        combinations.put("Strength+The World", new ScrollCombination("Earth Crush", 13, EffectType.DAMAGE, "Raw earthen power"));

        // Air + Air combinations
        combinations.put("The Fool+The Lovers", new ScrollCombination("Storm Bolt", 8, EffectType.DAMAGE, "Chaotic emotions"));
        combinations.put("Judgement+Justice", new ScrollCombination("Storm Bolt", 14, EffectType.DAMAGE, "Divine justice pierces all shields"));
        combinations.put("Judgement+Wheel of Fortune", new ScrollCombination("Storm Bolt", 13, EffectType.DAMAGE, "Divine chance with random bonus"));

        // Water + Water combinations
        combinations.put("Death+High Priestess", new ScrollCombination("Tidal Wave", 16, EffectType.REFLECT, "Death reflects 50% damage back"));
        combinations.put("The Moon+The Star", new ScrollCombination("Tidal Wave", 10, EffectType.DAMAGE, "Celestial confusion"));
        combinations.put("The Moon+Temperance", new ScrollCombination("Tidal Wave", 9, EffectType.DAMAGE, "Opponent skips next turn"));

        // DEFENSIVE SCROLLS - Mixed element shields
        combinations.put("High Priestess+The Empress", new ScrollCombination("Elemental Shield", 0, EffectType.HEAL, "Earth and water block 10 damage + heal 5 HP"));
        combinations.put("The Hierophant+Temperance", new ScrollCombination("Elemental Shield", 5, EffectType.SHIELD, "Wisdom and balance counter 5 damage"));

        // Mirror Force combinations
        combinations.put("High Priestess+The Moon", new ScrollCombination("Mirror Force", 0, EffectType.REFLECT, "Perfect reflection of next attack"));
        combinations.put("High Priestess+Justice", new ScrollCombination("Mirror Force", 5, EffectType.REFLECT, "Justice reflects + deals 5 damage"));
        combinations.put("Hanged Man+The Moon", new ScrollCombination("Mirror Force", 0, EffectType.REFLECT, "Sacrifice and illusion reflect + stall"));

        // Sacred Barrier combinations
        combinations.put("Judgement+The Hierophant", new ScrollCombination("Sacred Barrier", 0, EffectType.SHIELD, "Immune to next 2 attacks"));
        combinations.put("The Star+The Sun", new ScrollCombination("Sacred Barrier", 8, EffectType.HEAL, "Heal 8 HP + block 5 damage"));
        combinations.put("Temperance+The World", new ScrollCombination("Sacred Barrier", 0, EffectType.SHIELD, "Stabilize HP at current level"));

        // UTILITY SCROLLS
        combinations.put("The Magician+Wheel of Fortune", new ScrollCombination("Card Manipulation", 5, EffectType.DAMAGE, "Draw 2 cards, opponent discards 1"));
        combinations.put("The Fool+Wheel of Fortune", new ScrollCombination("Card Manipulation", 3, EffectType.DAMAGE, "Randomize both hands"));
        combinations.put("Hermit+The World", new ScrollCombination("Card Manipulation", 4, EffectType.DAMAGE, "Look at opponent's hand"));

        // Status Effect combinations
        combinations.put("The Devil+The Tower", new ScrollCombination("Corruption", 15, EffectType.STATUS, "Opponent takes 3 damage per turn for 2 turns"));
        combinations.put("The Devil+The Moon", new ScrollCombination("Confusion", 8, EffectType.DAMAGE, "Opponent's next combo deals half damage"));
        combinations.put("Death+The Tower", new ScrollCombination("Destruction", 17, EffectType.DAMAGE, "Destroy opponent's next defensive combo"));

        // Healing/Recovery combinations
        combinations.put("The Empress+The Star", new ScrollCombination("Nature's Blessing", 0, EffectType.HEAL, "Heal 10 HP"));
        combinations.put("The Star+The Sun", new ScrollCombination("Celestial Renewal", 8, EffectType.HEAL, "Heal 8 HP + draw 1 card"));
        combinations.put("The Empress+Temperance", new ScrollCombination("Balanced Growth", 6, EffectType.HEAL, "Heal 6 HP + cleanse debuffs"));

        // SPECIAL/ULTIMATE COMBINATIONS
        combinations.put("Judgement+The World", new ScrollCombination("Divine Judgment", 20, EffectType.DAMAGE, "Ultimate divine power - cannot be blocked or reflected"));
        combinations.put("The Fool+The Tower", new ScrollCombination("Chaos Storm", 15, EffectType.DAMAGE, "Random damage 5-25 to both players"));
        combinations.put("Justice+Temperance", new ScrollCombination("Perfect Balance", 0, EffectType.HEAL, "Both players set to same HP"));

        // Additional cross-element combinations
        combinations.put("The Magician+The Empress", new ScrollCombination("Elemental Fusion", 12, EffectType.DAMAGE, "Fire meets earth"));
        combinations.put("The Fool+High Priestess", new ScrollCombination("Mystical Chaos", 9, EffectType.REFLECT, "Air meets water"));
        combinations.put("The Emperor+Strength", new ScrollCombination("Imperial Might", 14, EffectType.DAMAGE, "Authority and physical strength"));
        combinations.put("The Chariot+Justice", new ScrollCombination("Righteous Charge", 13, EffectType.DAMAGE, "War meets divine balance"));
        combinations.put("The Sun+The Star", new ScrollCombination("Celestial Power", 13, EffectType.HEAL, "Solar and stellar energy"));
        combinations.put("The Moon+Death", new ScrollCombination("Dark Transformation", 14, EffectType.DAMAGE, "Illusion and change"));
    }

    public static ScrollCombination getCombination(String key) {
        return combinations.get(key);
    }

    public static Set<String> getAllCombinations() {
        return combinations.keySet();
    }

    public static boolean hasCombination(String key) {
        return combinations.containsKey(key);
    }

    public static int getCombinationCount() {
        return combinations.size();
    }
}