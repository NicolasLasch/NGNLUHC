package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Kainas: learns who first gains Regeneration, owns an infinite vegetal inventory (/forest) and,
 * in the finale, a Vegetal Cage (5x5 leaves that buffs him and slows enemies). Fire hurts him twice.
 */
public class KainasRole extends Role {

    /** Cooldown of the cage in seconds. */
    private static final int CAGE_COOLDOWN = 15 * 60;
    /** Cooldown (seconds) that remains when a kill happens in the cage. */
    private static final int CAGE_KILL_COOLDOWN = 10 * 60;
    /** Lifetime of the cage in seconds. */
    private static final int CAGE_SECONDS = 60;
    /** Half-width of the cage (5x5). */
    private static final int CAGE_RADIUS = 2;

    /** Vegetal items offered by /forest. */
    private static final Material[] FOREST_ITEMS = {
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG,
            Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
            Material.AZALEA_LEAVES, Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING, Material.CHERRY_SAPLING,
            Material.VINE, Material.LILY_PAD, Material.SUGAR_CANE, Material.BAMBOO, Material.CACTUS,
            Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
            Material.APPLE, Material.CARROT, Material.POTATO, Material.SWEET_BERRIES, Material.GLOW_BERRIES,
            Material.MELON_SLICE, Material.PUMPKIN, Material.HAY_BLOCK, Material.COCOA_BEANS,
            Material.SHORT_GRASS, Material.FERN, Material.DANDELION, Material.POPPY, Material.MOSS_BLOCK,
            Material.RED_MUSHROOM, Material.BROWN_MUSHROOM, Material.KELP, Material.SEAGRASS, Material.BONE_MEAL
    };

    private boolean regenInfoKnown = false;
    private Location cageCenter;
    private long cageUntil = 0L;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (KAINAS)
     */
    public KainasRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player != null) {
            MessageUtil.sendMessage(player, "&eUtilise &a/forest &epour ouvrir ton inventaire végétal infini.");
        }
        runRepeating(this::scanForRegeneration, 20L, 20L * 5);
    }

    /**
     * Learn the role of the first player who gets the Regeneration effect.
     */
    private void scanForRegeneration() {
        Player kainas = getPlayer();
        if (regenInfoKnown || kainas == null) {
            return;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            var data = plugin.getPlayerManager().getNGNLPlayer(other.getUniqueId());
            if (other.hasPotionEffect(PotionEffectType.REGENERATION) && data != null && data.getRole() != null) {
                regenInfoKnown = true;
                MessageUtil.sendMessage(kainas, "&aLe premier joueur à avoir obtenu la régénération a le rôle : &f"
                        + data.getRole().getDisplayName());
                return;
            }
        }
    }

    // ------------------------------------------------------------------ /forest

    /**
     * Open the infinite vegetal inventory: clicking an item gives a full stack.
     */
    public void openForestSupply() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        ForestHolder holder = new ForestHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, "Forêt de Kainas");
        holder.inventory = inventory;
        for (Material material : FOREST_ITEMS) {
            inventory.addItem(new ItemStack(material));
        }
        player.openInventory(inventory);
    }

    /**
     * Inventory holder identifying the forest inventory.
     */
    public static final class ForestHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * Give a full stack of a vegetal item (called when he clicks it in the forest inventory).
     *
     * @param material Clicked material
     */
    public void giveForestStack(Material material) {
        Player player = getPlayer();
        if (player != null) {
            giveItem(player, new ItemStack(material, material.getMaxStackSize()));
        }
    }

    // ------------------------------------------------------------------ vegetal cage

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("vegetal_cage");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.OAK_LEAVES, "&a&lCage végétale",
                "&7Crée une cage de feuilles 5x5 : régénération et vitesse pour toi,", "&7Lenteur II pour tes adversaires.",
                "&7La cage disparaît après 1 minute.", "",
                "&eClic droit pour activer", "&cRecharge : 15 minutes (10 minutes si kill)"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.OAK_LEAVES && useCage();
    }

    /**
     * Build the cage around Kainas and start its effects.
     *
     * @return True if the cage was created
     */
    private boolean useCage() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("vegetal_cage", CAGE_COOLDOWN)) {
            return false;
        }
        Location center = player.getLocation().getBlock().getLocation();
        cageCenter = center.clone().add(0.5, 0, 0.5);
        cageUntil = System.currentTimeMillis() + CAGE_SECONDS * 1000L;

        buildCage(center);
        var effects = runRepeating(this::applyCageEffects, 0L, 20L);
        runLater(() -> {
            effects.cancel();
            clearCage(center);
        }, CAGE_SECONDS * 20L);
        MessageUtil.broadcast("&aKainas a créé une cage végétale !");
        return true;
    }

    /**
     * Place the leaves: floor under the feet, four walls, and a ceiling.
     *
     * @param center Block where Kainas stands
     */
    private void buildCage(Location center) {
        forEachCageBlock(center, block -> {
            if (block.getType().isAir()) {
                block.setType(Material.OAK_LEAVES);
            }
        });
    }

    /**
     * Remove the leaves of the cage.
     *
     * @param center Block where Kainas stood
     */
    private void clearCage(Location center) {
        forEachCageBlock(center, block -> {
            if (block.getType() == Material.OAK_LEAVES) {
                block.setType(Material.AIR);
            }
        });
    }

    /**
     * Visit the shell blocks of the cage (floor y=-1, walls y=0..2, ceiling y=3).
     *
     * @param center Block where Kainas stands
     * @param action Action applied to every shell block
     */
    private void forEachCageBlock(Location center, java.util.function.Consumer<Block> action) {
        for (int x = -CAGE_RADIUS; x <= CAGE_RADIUS; x++) {
            for (int z = -CAGE_RADIUS; z <= CAGE_RADIUS; z++) {
                for (int y = -1; y <= 3; y++) {
                    boolean floorOrCeiling = y == -1 || y == 3;
                    boolean wall = (Math.abs(x) == CAGE_RADIUS || Math.abs(z) == CAGE_RADIUS) && y >= 0 && y <= 2;
                    if (floorOrCeiling || wall) {
                        action.accept(center.clone().add(x, y, z).getBlock());
                    }
                }
            }
        }
    }

    /**
     * Buff Kainas and slow the other players while they are inside the cage.
     */
    private void applyCageEffects() {
        if (cageCenter == null) {
            return;
        }
        for (Player other : cageCenter.getWorld().getPlayers()) {
            if (other.getLocation().distanceSquared(cageCenter) > (CAGE_RADIUS + 1.5) * (CAGE_RADIUS + 1.5)) {
                continue;
            }
            if (other.getUniqueId().equals(playerId)) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false));
                other.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
            } else if (!isFriendly(other.getUniqueId())) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
            }
        }
    }

    @Override
    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        Player victim = Bukkit.getPlayer(victimId);
        boolean inCage = cageCenter != null && System.currentTimeMillis() < cageUntil && victim != null
                && victim.getWorld().equals(cageCenter.getWorld())
                && victim.getLocation().distanceSquared(cageCenter) <= (CAGE_RADIUS + 1.5) * (CAGE_RADIUS + 1.5);
        if (inCage && playerId.equals(killerId) && getNGNLPlayer() != null) {
            getNGNLPlayer().reduceCooldownTo("vegetal_cage", CAGE_COOLDOWN, CAGE_KILL_COOLDOWN);
            MessageUtil.sendMessage(getPlayer(), "&aKill dans la cage : elle se recharge en 10 minutes.");
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Kainas.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You learn the role of the first player who gets Regeneration.",
                "/forest gives you endless vegetal items. Fire hurts you twice as much."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Vegetal Cage: a 5x5 leaf cage for 1 minute. You get Regeneration and Speed inside,",
                "your enemies get Slowness II (every 15 minutes, 10 minutes if you kill inside)."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
