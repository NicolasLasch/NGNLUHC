package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Jibril: automatic regeneration all game long; in the finale the Book of 18 Wings triggers one of
 * four random catastrophes (nuclear explosion, lava cascade, crater, radioactive zone).
 * Jibril takes no damage from her own catastrophes; Holou is immune to them and Artosh takes double.
 */
public class JibrilRole extends Role {

    /** Cooldown of the book in seconds. */
    private static final int CATASTROPHE_COOLDOWN = 20 * 60;
    /** Radius (blocks) of the nuclear explosion damage. */
    private static final double NUCLEAR_RADIUS = 30.0;
    /** Radius (blocks) of the lava cascade. */
    private static final int LAVA_RADIUS = 10;
    /** Radius (blocks) of the crater. */
    private static final int CRATER_RADIUS = 6;
    /** Radius (blocks) of the radioactive zone. */
    private static final double RADIATION_RADIUS = 25.0;
    /** Duration in ticks of the lava cascade before the terrain is restored. */
    private static final long LAVA_DURATION_TICKS = 15L * 20L;
    /** Duration in ticks before the crater is refilled. */
    private static final long CRATER_DURATION_TICKS = 60L * 20L;
    /** Duration in ticks of the radioactive zone. */
    private static final long RADIATION_DURATION_TICKS = 30L * 20L;

    private final Random random = new Random();

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (JIBRIL)
     */
    public JibrilRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        startNaturalRegeneration();
        MessageUtil.sendMessage(player, "&eTu as une régénération automatique pendant toute la partie.");
        MessageUtil.sendMessage(player, "&eTu peux t'allier avec un autre joueur solo : &6/alliance <joueur>&e.");
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("catastrophe");
    }

    // ------------------------------------------------------------------ book of 18 wings

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.ENCHANTED_BOOK, "&4&lLivre des 18 Ailes",
                "&7Déclenche une catastrophe aléatoire :",
                "&71. Explosion nucléaire", "&72. Cascade de lave", "&73. Création d'un cratère", "&74. Zone radioactive", "",
                "&aTu es immunisée contre tes catastrophes.", "&eClic droit pour activer", "&cRecharge : 20 minutes"));
        MessageUtil.sendMessage(player, "&aTu as reçu le &4Livre des 18 Ailes&a !");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.ENCHANTED_BOOK && useCatastropheAbility();
    }

    /**
     * Trigger a random catastrophe around Jibril.
     *
     * @return True if a catastrophe was unleashed
     */
    public boolean useCatastropheAbility() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("catastrophe", CATASTROPHE_COOLDOWN)) {
            return false;
        }

        Location center = player.getLocation().clone();
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0, false, false));
        String name = launchRandomCatastrophe(center);
        MessageUtil.broadcast("&4&l" + player.getName() + " a déclenché : " + name + " !");
        MessageUtil.sendMessage(player, "&6Tu es immunisée contre les effets de ta catastrophe.");
        return true;
    }

    /**
     * Pick and start one of the four catastrophes.
     *
     * @param center Center of the catastrophe
     * @return Name of the catastrophe
     */
    private String launchRandomCatastrophe(Location center) {
        switch (random.nextInt(4)) {
            case 0:
                createNuclearExplosion(center);
                return "Explosion nucléaire";
            case 1:
                createLavaCascade(center);
                return "Cascade de lave";
            case 2:
                createCrater(center);
                return "Création d'un cratère";
            default:
                createRadiationZone(center);
                return "Zone radioactive";
        }
    }

    /**
     * Deal catastrophe damage to a player, honouring the role exceptions
     * (Holou takes nothing, Artosh takes double, Jibril is immune).
     *
     * @param target Damaged player
     * @param damage Damage in HP
     */
    private void hurt(Player target, double damage) {
        if (target.getUniqueId().equals(playerId) || !plugin.getGameManager().isPlayerAlive(target.getUniqueId())) {
            return;
        }
        var targetData = plugin.getPlayerManager().getNGNLPlayer(target.getUniqueId());
        RoleType targetRole = targetData != null && targetData.getRole() != null ? targetData.getRole().getRoleType() : null;
        if (targetRole == RoleType.HOLOU) {
            return;
        }
        double finalDamage = targetRole == RoleType.ARTOSH ? damage * 2 : damage;
        target.damage(finalDamage, getPlayer());
    }

    // ------------------------------------------------------------------ 1. nuclear explosion

    /**
     * Big visual explosion that hurts everybody around.
     *
     * @param center Explosion center
     */
    private void createNuclearExplosion(Location center) {
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 6, 4, 2, 4, 0);
        center.getWorld().playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        for (Player target : nearbyAlivePlayers(center, NUCLEAR_RADIUS)) {
            hurt(target, 6.0);
            target.setFireTicks(100);
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 1));
        }
    }

    // ------------------------------------------------------------------ 2. lava cascade

    /**
     * Rain lava sources from the sky; the terrain is restored afterwards.
     *
     * @param center Cascade center
     */
    private void createLavaCascade(Location center) {
        Map<Location, BlockData> replaced = new LinkedHashMap<>();
        for (int dx = -LAVA_RADIUS; dx <= LAVA_RADIUS; dx += 4) {
            for (int dz = -LAVA_RADIUS; dz <= LAVA_RADIUS; dz += 4) {
                Block block = center.getWorld().getBlockAt(center.getBlockX() + dx, center.getBlockY() + 7, center.getBlockZ() + dz);
                if (block.getType().isAir()) {
                    replaced.put(block.getLocation(), block.getBlockData());
                    block.setType(Material.LAVA);
                }
            }
        }
        for (Player target : nearbyAlivePlayers(center, LAVA_RADIUS + 2)) {
            hurt(target, 4.0);
            target.setFireTicks(200);
        }
        runLater(() -> removeLavaAround(center, replaced), LAVA_DURATION_TICKS);
    }

    /**
     * Remove every lava block that flowed where there was air.
     *
     * @param center   Cascade center
     * @param replaced Sources placed at the start
     */
    private void removeLavaAround(Location center, Map<Location, BlockData> replaced) {
        replaced.forEach((location, data) -> location.getBlock().setBlockData(data));
        int radius = LAVA_RADIUS + 6;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 9; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getWorld().getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (block.getType() == Material.LAVA) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 3. crater

    /**
     * Dig a crater under Jibril, throw players away and refill the hole later.
     *
     * @param center Crater center
     */
    private void createCrater(Location center) {
        Map<Location, BlockData> removed = digCrater(center.clone().subtract(0, 2, 0));
        for (Player target : nearbyAlivePlayers(center, CRATER_RADIUS * 2.0)) {
            Vector push = target.getLocation().toVector().subtract(center.toVector()).setY(0.4);
            if (push.lengthSquared() > 0) {
                target.setVelocity(push.normalize().multiply(1.6).setY(0.6));
            }
            hurt(target, 2.0);
        }
        runLater(() -> removed.forEach((location, data) -> location.getBlock().setBlockData(data)), CRATER_DURATION_TICKS);
    }

    /**
     * Remove a sphere of blocks, remembering what was there.
     *
     * @param center Sphere center
     * @return Removed blocks with their original data
     */
    private Map<Location, BlockData> digCrater(Location center) {
        Map<Location, BlockData> removed = new LinkedHashMap<>();
        for (int x = -CRATER_RADIUS; x <= CRATER_RADIUS; x++) {
            for (int y = -CRATER_RADIUS; y <= CRATER_RADIUS; y++) {
                for (int z = -CRATER_RADIUS; z <= CRATER_RADIUS; z++) {
                    if (x * x + y * y + z * z > CRATER_RADIUS * CRATER_RADIUS) {
                        continue;
                    }
                    Block block = center.getWorld().getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (isBreakable(block)) {
                        removed.put(block.getLocation(), block.getBlockData());
                        block.setType(Material.AIR);
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Check whether the crater may remove a block.
     *
     * @param block Block to test
     * @return True for ordinary solid blocks
     */
    private boolean isBreakable(Block block) {
        Material type = block.getType();
        return !type.isAir() && type != Material.BEDROCK && type != Material.BARRIER && type != Material.LAVA && type != Material.WATER;
    }

    // ------------------------------------------------------------------ 4. radioactive zone

    /**
     * Poison and confuse players who stay in the zone for 30 seconds.
     *
     * @param center Zone center
     */
    private void createRadiationZone(Location center) {
        var task = runRepeating(() -> {
            center.getWorld().spawnParticle(Particle.ITEM_SLIME, center, 60, RADIATION_RADIUS / 2, 2, RADIATION_RADIUS / 2, 0);
            for (Player target : nearbyAlivePlayers(center, RADIATION_RADIUS)) {
                if (!isImmuneToRadiation(target)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
                }
            }
        }, 20L, 40L);
        runLater(task::cancel, RADIATION_DURATION_TICKS);
    }

    /**
     * Holou is immune to every catastrophe.
     *
     * @param target Player to test
     * @return True if the player is immune
     */
    private boolean isImmuneToRadiation(Player target) {
        var data = plugin.getPlayerManager().getNGNLPlayer(target.getUniqueId());
        return data != null && data.getRole() != null && data.getRole().getRoleType() == RoleType.HOLOU;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Jibril, a powerful Flugel.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You have no advantage in mini-games but you regenerate automatically."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        List<String> lines = new ArrayList<>();
        lines.add("Book of 18 Wings: triggers one random catastrophe (every 20 minutes):");
        lines.add("1. Nuclear Explosion  2. Lava Cascade  3. Crater  4. Radioactive Zone");
        lines.add("You take no damage from your catastrophes. Holou is immune, Artosh takes double.");
        return lines;
    }

    @Override
    public String getObjective() {
        return "Win the game alone or with an alliance formed using /alliance.";
    }
}
