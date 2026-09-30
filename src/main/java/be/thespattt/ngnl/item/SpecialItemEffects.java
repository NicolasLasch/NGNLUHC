package be.thespattt.ngnl.item;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Behaviour of the special items found or bought during the game:
 * Aka Si Anse, Blood Destruction Bomb, Elf Runes, Imanity Crown, Ex-Machina Core, Old Deus Fragment.
 * (The Suniaster is handled by {@link SpecialItemManager} because it is claimed by picking it up.)
 */
public class SpecialItemEffects {

    /** Health (HP) the Aka Si Anse leaves to its victim (2 hearts). */
    private static final double AKA_VICTIM_HEALTH = 4.0;
    /** Radius (blocks) around the victim in which others are hurt by the Aka Si Anse. */
    private static final double AKA_SPLASH_RADIUS = 30.0;
    /** Damage (HP) received by players close to the Aka Si Anse victim. */
    private static final double AKA_SPLASH_DAMAGE = 4.0;
    /** Radius (blocks) of the Blood Destruction Bomb. */
    private static final double BOMB_RADIUS = 20.0;
    /** Damage (HP) of the bomb; the user only takes half. */
    private static final double BOMB_DAMAGE = 8.0;
    /** Radius (blocks) of the Elf Runes. */
    private static final double RUNE_RADIUS = 15.0;
    /** Lifetime of the Elf Runes in ticks (3 minutes). */
    private static final int RUNE_TICKS = 3 * 60 * 20;
    /** Imanity Crown effect duration in seconds. */
    private static final int CROWN_SECONDS = 45;
    /** Imanity Crown cooldown in seconds. */
    private static final int CROWN_COOLDOWN = 5 * 60;
    /** Hearts paid to use the Old Deus Fragment. */
    private static final double FRAGMENT_COST = 3.0;
    /** Range (blocks) of an Ex-Machina Core replay. */
    private static final double CORE_RANGE = 30.0;

    private final NoGameNoLife plugin;
    private final Map<UUID, RecordedAbility> recordedAbilities = new HashMap<>();
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public SpecialItemEffects(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Dispatch the right-click on a special item to its behaviour.
     *
     * @param player Player using the item
     * @param itemId Identifier of the item (PDC value)
     * @param item   Item used
     */
    public void use(Player player, String itemId, ItemStack item) {
        switch (itemId) {
            case "aka_si_anse":
                useAkaSiAnse(player, item);
                break;
            case "blood_bomb":
                useBloodBomb(player, item);
                break;
            case "elf_runes":
                useElfRunes(player, item);
                break;
            case "imanity_crown":
                useImanityCrown(player);
                break;
            case "ex_machina_core":
                useExMachinaCore(player, item);
                break;
            case "old_deus_fragment":
                useOldDeusFragment(player, item);
                break;
            case "suniaster":
                plugin.getSpecialItemManager().claimSuniaster(player, item);
                break;
            default:
                MessageUtil.sendMessage(player, "&cObjet spécial inconnu : " + itemId);
                break;
        }
    }

    // ------------------------------------------------------------------ Aka Si Anse

    /**
     * Open the target menu of the Aka Si Anse (single use).
     *
     * @param player Player holding the weapon
     * @param item   The weapon
     */
    private void useAkaSiAnse(Player player, ItemStack item) {
        plugin.getPlayerPicker().open(player, "Aka Si Anse - choisis ta cible",
                plugin.getPlayerPicker().aliveCandidates(player), targetId -> fireAkaSiAnse(player, targetId));
    }

    /**
     * Strike the chosen player: he falls to 2 hearts and nearby players are hurt too.
     *
     * @param user     Player using the weapon
     * @param targetId UUID of the victim
     */
    private void fireAkaSiAnse(Player user, UUID targetId) {
        Player victim = Bukkit.getPlayer(targetId);
        if (victim == null || !plugin.getGameManager().isPlayerAlive(targetId) || !consumeOne(user, "aka_si_anse")) {
            return;
        }
        Location impact = victim.getLocation();
        impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 3, 2, 1, 2, 0);
        impact.getWorld().spawnParticle(Particle.FLAME, impact.clone().add(0, 1, 0), 120, 2, 2, 2, 0.05);
        impact.getWorld().playSound(impact, Sound.ENTITY_GHAST_SHOOT, 3f, 0.6f);

        victim.setHealth(Math.min(victim.getHealth(), AKA_VICTIM_HEALTH));
        MessageUtil.sendMessage(victim, "&4Une boule de feu de l'Aka Si Anse s'abat sur toi !");
        for (Player nearby : impact.getWorld().getPlayers()) {
            boolean close = nearby.getLocation().distanceSquared(impact) <= AKA_SPLASH_RADIUS * AKA_SPLASH_RADIUS;
            if (close && !nearby.equals(victim) && plugin.getGameManager().isPlayerAlive(nearby.getUniqueId())) {
                nearby.setHealth(Math.max(1.0, nearby.getHealth() - AKA_SPLASH_DAMAGE));
            }
        }
        MessageUtil.broadcast("&c&lL'Aka Si Anse a été utilisé !");
    }

    // ------------------------------------------------------------------ Blood Destruction Bomb

    /**
     * Hurt every player within 20 blocks; the user only takes half the damage (single use).
     *
     * @param player Player using the bomb
     * @param item   The bomb
     */
    private void useBloodBomb(Player player, ItemStack item) {
        if (!consumeOne(player, "blood_bomb")) {
            return;
        }
        Location center = player.getLocation();
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 4, 3, 1, 3, 0);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.7f);
        for (Player target : center.getWorld().getPlayers()) {
            boolean close = target.getLocation().distanceSquared(center) <= BOMB_RADIUS * BOMB_RADIUS;
            if (!close || !plugin.getGameManager().isPlayerAlive(target.getUniqueId())) {
                continue;
            }
            double damage = target.equals(player) ? BOMB_DAMAGE / 2 : BOMB_DAMAGE;
            target.setHealth(Math.max(1.0, target.getHealth() - damage));
        }
        MessageUtil.broadcast("&4Une Blood Destruction Bomb a explosé !");
    }

    // ------------------------------------------------------------------ Elf Runes

    /**
     * Place runes at the player's position: for 3 minutes the movements of everybody inside are
     * revealed to all players with particles.
     *
     * @param player Player placing the runes
     * @param item   The runes
     */
    private void useElfRunes(Player player, ItemStack item) {
        if (!consumeOne(player, "elf_runes")) {
            return;
        }
        Location center = player.getLocation().clone();
        BukkitRunnable runnable = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= RUNE_TICKS) {
                    cancel();
                    return;
                }
                drawRuneCircle(center);
                revealPlayersInRunes(center);
                ticks += 5;
            }
        };
        runningTasks.add(runnable.runTaskTimer(plugin, 0L, 5L));
        MessageUtil.broadcast("&aDes runes elfiques ont été tracées en " + center.getBlockX() + ", " + center.getBlockZ() + " !");
    }

    /**
     * Draw the ring of the runes.
     *
     * @param center Center of the runes
     */
    private void drawRuneCircle(Location center) {
        for (int i = 0; i < 40; i++) {
            double angle = 2 * Math.PI * i / 40;
            Location point = center.clone().add(Math.cos(angle) * RUNE_RADIUS, 0.2, Math.sin(angle) * RUNE_RADIUS);
            center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Mark every alive player standing in the runes with visible particles.
     *
     * @param center Center of the runes
     */
    private void revealPlayersInRunes(Location center) {
        for (Player target : center.getWorld().getPlayers()) {
            boolean inside = target.getLocation().distanceSquared(center) <= RUNE_RADIUS * RUNE_RADIUS;
            if (inside && plugin.getGameManager().isPlayerAlive(target.getUniqueId())) {
                center.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 3, 0.2, 0.4, 0.2, 0.01);
            }
        }
    }

    // ------------------------------------------------------------------ Imanity Crown

    /**
     * Temporary Resistance but the position of the wearer is revealed (Glowing).
     *
     * @param player Player using the crown
     */
    private void useImanityCrown(Player player) {
        NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (data.isAbilityOnCooldown("imanity_crown", CROWN_COOLDOWN)) {
            MessageUtil.sendMessage(player, "&cCooldown : " + data.getRemainingCooldown("imanity_crown", CROWN_COOLDOWN) + "s");
            return;
        }
        data.useAbility("imanity_crown");
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, CROWN_SECONDS * 20, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, CROWN_SECONDS * 20, 0, false, false));
        MessageUtil.broadcast("&6Quelqu'un porte la Couronne d'Imanity ! Sa position est visible de tous.");
    }

    // ------------------------------------------------------------------ Ex-Machina Core

    /**
     * Remember an effect-based ability that was used against a player holding an Ex-Machina Core.
     * Called by the roles whose abilities apply potion effects.
     *
     * @param target  Player who suffered the ability
     * @param label   Name of the ability
     * @param effects Effects the ability applied
     */
    public void recordAbilityUsedAgainst(Player target, String label, PotionEffect... effects) {
        if (!carriesItem(target, "ex_machina_core")) {
            return;
        }
        recordedAbilities.put(target.getUniqueId(), new RecordedAbility(label, effects));
        MessageUtil.sendMessage(target, "&bTon Ex-Machina Core a enregistré : &f" + label);
    }

    /**
     * Use the recorded ability against the nearest enemy (single use).
     *
     * @param player Player holding the core
     * @param item   The core
     */
    private void useExMachinaCore(Player player, ItemStack item) {
        RecordedAbility recorded = recordedAbilities.get(player.getUniqueId());
        if (recorded == null) {
            MessageUtil.sendMessage(player, "&cLe noyau n'a encore rien enregistré.");
            return;
        }
        Player target = findNearestEnemy(player, CORE_RANGE);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cAucune cible à portée.");
            return;
        }
        if (!consumeOne(player, "ex_machina_core")) {
            return;
        }
        recordedAbilities.remove(player.getUniqueId());
        for (PotionEffect effect : recorded.effects) {
            target.addPotionEffect(effect);
        }
        MessageUtil.sendMessage(player, "&bTu as rejoué « " + recorded.label + " » sur " + target.getName() + ".");
        MessageUtil.sendMessage(target, "&cUne capacité t'est renvoyée : " + recorded.label);
    }

    /**
     * Find the nearest alive player (not the user nor his partner) in range.
     *
     * @param player User
     * @param range  Range in blocks
     * @return Nearest enemy or null
     */
    private Player findNearestEnemy(Player player, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || !plugin.getGameManager().isPlayerAlive(other.getUniqueId())) {
                continue;
            }
            var data = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
            boolean ally = data != null && data.getRole() != null && other.getUniqueId().equals(data.getRole().getPartnerUUID());
            double distance = other.getLocation().distanceSquared(player.getLocation());
            if (!ally && distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Old Deus Fragment

    /**
     * Reverse the last mini-game defeat at the cost of 3 hearts (single use).
     *
     * @param player Player using the fragment
     * @param item   The fragment
     */
    private void useOldDeusFragment(Player player, ItemStack item) {
        if (!plugin.getMiniGameEngine().restoreLastLoss(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cAucune défaite récente à annuler.");
            return;
        }
        consumeOne(player, "old_deus_fragment");
        plugin.getGameManager().removePlayerHearts(player.getUniqueId(), FRAGMENT_COST);
        MessageUtil.sendMessage(player, "&5Ta défaite est annulée, au prix de 3 cœurs.");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Remove one copy of a special item from a player's inventory.
     *
     * @param player Player holding the item
     * @param itemId Identifier of the special item
     * @return True if one copy was removed
     */
    public boolean consumeOne(Player player, String itemId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isSpecialItem(contents[slot], itemId)) {
                ItemStack stack = contents[slot];
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    player.getInventory().setItem(slot, null);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a player carries a special item.
     *
     * @param player Player to inspect
     * @param itemId Identifier of the special item
     * @return True if he carries it
     */
    private boolean carriesItem(Player player, String itemId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isSpecialItem(stack, itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether an item stack is the given special item.
     *
     * @param stack  Item stack
     * @param itemId Identifier of the special item
     * @return True if it matches
     */
    public boolean isSpecialItem(ItemStack stack, String itemId) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String value = stack.getItemMeta().getPersistentDataContainer()
                .get(plugin.getNamespacedKey("special_item"), org.bukkit.persistence.PersistentDataType.STRING);
        return itemId.equals(value);
    }

    /**
     * Stop every running effect (game end / plugin disable).
     */
    public void cleanup() {
        runningTasks.forEach(BukkitTask::cancel);
        runningTasks.clear();
        recordedAbilities.clear();
    }

    /**
     * An ability recorded by the Ex-Machina Core.
     */
    private static final class RecordedAbility {
        private final String label;
        private final PotionEffect[] effects;

        private RecordedAbility(String label, PotionEffect[] effects) {
            this.label = label;
            this.effects = effects;
        }
    }
}
