package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Artosh: learns Schwi's identity (40% chance of a wrong name), gets Strength after every kill and,
 * in the finale, the 18 Wings (elytra) and the Blade of Infinity. Takes double damage from Jibril.
 */
public class ArtoshRole extends Role {

    /** Cooldown of the blade in seconds. */
    private static final int BLADE_COOLDOWN = 10 * 60;
    /** Percent chance that the Schwi information is wrong. */
    private static final int WRONG_INFO_CHANCE = 40;
    /** Radius (blocks) of the blade. */
    private static final double BLADE_RADIUS = 5.0;
    /** Health (HP) under which an enemy can be executed (2 hearts). */
    private static final double EXECUTION_HEALTH = 4.0;
    /** Duration of the Strength effect after a kill, in seconds. */
    private static final int STRENGTH_SECONDS = 5 * 60;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (ARTOSH)
     */
    public ArtoshRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        UUID schwiId = plugin.getRoleManager().getPlayerByRole(RoleType.SCHWI);
        if (player == null || schwiId == null) {
            return;
        }
        Player revealed = Bukkit.getPlayer(schwiId);
        if (ThreadLocalRandom.current().nextInt(100) < WRONG_INFO_CHANCE) {
            Player wrong = pickWrongPlayer(schwiId);
            revealed = wrong != null ? wrong : revealed;
        }
        if (revealed != null) {
            MessageUtil.sendMessage(player, "&eOn t'a dit que Schwi est : &a" + revealed.getName());
        }
    }

    /**
     * Pick a random player who is neither Artosh nor Schwi.
     *
     * @param schwiId UUID of the real Schwi
     * @return A wrong player or null
     */
    private Player pickWrongPlayer(UUID schwiId) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(playerId) && !online.getUniqueId().equals(schwiId)) {
                candidates.add(online);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    @Override
    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || killerId == null || !killerId.equals(playerId)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, STRENGTH_SECONDS * 20, 0, false, false));
        MessageUtil.sendMessage(player, "&cTon kill éveille la force d'Artosh pendant 5 minutes.");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.ELYTRA, "&5&l18 Ailes",
                "&7Permet de planer entre les bâtiments pour trouver tes ennemis."));
        giveItem(player, buildRoleItem(Material.NETHERITE_SWORD, "&4&lLame de l'Infini",
                "&7Tue et fait exploser les ennemis à moins de 5 blocs ayant moins de 2 cœurs.",
                "&7Chaque kill te donne 1 cœur de plus.", "",
                "&eClic droit pour activer", "&cRecharge : 10 minutes (instantanée si kill)"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.NETHERITE_SWORD && useBlade();
    }

    /**
     * Execute every weak enemy around Artosh.
     *
     * @return True if the blade was used
     */
    private boolean useBlade() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("blade", BLADE_COOLDOWN)) {
            return false;
        }
        boolean killed = false;
        for (Player target : nearbyAlivePlayers(player.getLocation(), BLADE_RADIUS)) {
            if (!isFriendly(target.getUniqueId()) && target.getHealth() <= EXECUTION_HEALTH) {
                executeTarget(player, target);
                killed = true;
            }
        }
        if (killed) {
            resetCooldown("blade");
            MessageUtil.sendMessage(player, "&aKill confirmé : la Lame est immédiatement rechargée.");
        } else {
            MessageUtil.sendMessage(player, "&eAucun ennemi assez faible à portée.");
        }
        return true;
    }

    /**
     * Blow up a weak enemy and reward Artosh with a permanent extra heart.
     *
     * @param artosh Artosh
     * @param target Executed enemy
     */
    private void executeTarget(Player artosh, Player target) {
        target.getWorld().createExplosion(target.getLocation(), 0F, false);
        plugin.getCombatTracker().setLastDamager(target.getUniqueId(), playerId);
        target.damage(9999.0, artosh);
        addMaxHearts(1);
        artosh.setHealth(Math.min(artosh.getMaxHealth(), artosh.getHealth() + 2.0));
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Artosh.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You learn Schwi's identity (40% chance the information is wrong).",
                "Each kill gives you Strength for 5 minutes."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "18 Wings (elytra) to glide between buildings.",
                "Blade of Infinity: executes enemies under 2 hearts within 5 blocks (+1 heart per kill).",
                "Damage dealt to you by Jibril counts double."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
