package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Okein: learns the role of the first player to take damage, starts with an anvil, 200 levels,
 * Fire Resistance and a Flame book (he is the only role allowed to use fire), takes 1 heart per
 * second in water and owns the Hammer of Destruction (anvil rain) in the finale.
 */
public class OkeinRole extends Role {

    /** Persistent-data key marking an anvil created by the hammer. */
    public static final String ANVIL_TAG = "okein_anvil";

    /** Cooldown of the hammer in seconds. */
    private static final int HAMMER_COOLDOWN = 15 * 60;
    /** Cooldown (seconds) that remains when the anvils killed somebody. */
    private static final int HAMMER_KILL_COOLDOWN = 5 * 60;
    /** Radius of the anvil rain (5x5 area). */
    private static final int HAMMER_RADIUS = 2;
    /** Damage (HP) of one anvil (4 hearts). */
    private static final int ANVIL_DAMAGE = 8;
    /** Time (ms) during which a victim hit by an anvil counts as killed by the hammer. */
    private static final long KILL_ATTRIBUTION_MS = 10_000L;
    /** Levels granted at the start. */
    private static final int STARTING_LEVELS = 200;

    private boolean firstDamageKnown = false;
    private final Map<UUID, Long> anvilVictims = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (OKEIN)
     */
    public OkeinRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        giveItem(player, new ItemStack(Material.ANVIL));
        giveItem(player, buildFlameBook());
        player.setLevel(player.getLevel() + STARTING_LEVELS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        runRepeating(this::applyWaterDamage, 20L, 20L);
        MessageUtil.sendMessage(player, "&eAttention : l'eau (et la pluie) te brûle de 1 cœur par seconde. Tu es le seul à pouvoir utiliser le feu.");
    }

    /**
     * Build the book holding the Flame enchantment.
     *
     * @return Enchanted book with Flame
     */
    private ItemStack buildFlameBook() {
        ItemStack book = new ItemBuilder(Material.ENCHANTED_BOOK).name("&6&lLivre Flamme").build();
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(Enchantment.FLAME, 1, true);
            book.setItemMeta(meta);
        }
        return book;
    }

    /**
     * Learn the role of the first player (possibly Okein himself) who takes damage.
     *
     * @param targetId UUID of the damaged player
     */
    public void registerFirstDamagedPlayer(UUID targetId) {
        if (firstDamageKnown) {
            return;
        }
        firstDamageKnown = true;
        Player player = getPlayer();
        var target = plugin.getPlayerManager().getNGNLPlayer(targetId);
        if (player != null && target != null && target.getRole() != null) {
            MessageUtil.sendMessage(player, "&eLe premier joueur blessé a le rôle : &f" + target.getRole().getDisplayName());
        }
    }

    /**
     * Hurt Okein by 1 heart when he is in water or under the rain.
     */
    private void applyWaterDamage() {
        Player player = getPlayer();
        if (player == null || !isAlive()) {
            return;
        }
        Block feet = player.getLocation().getBlock();
        boolean inWater = feet.getType() == Material.WATER || player.isInWater();
        boolean underRain = player.getWorld().hasStorm() && player.getLocation().getBlock().getLightFromSky() == 15
                && player.getWorld().getHighestBlockYAt(player.getLocation()) <= player.getLocation().getBlockY();
        if (inWater || underRain) {
            player.damage(2.0);
            MessageUtil.sendMessage(player, "&cL'eau te brûle !");
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("hammer");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.IRON_AXE, "&8&lMarteau de destruction",
                "&7Fait tomber des enclumes sur une zone de 5x5.", "&7Chaque enclume retire 4 cœurs.", "",
                "&eClic droit pour activer", "&cRecharge : 15 minutes (5 minutes si kill)"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.IRON_AXE && useHammer();
    }

    /**
     * Drop anvils on a 5x5 area where Okein looks.
     *
     * @return True if the hammer was used
     */
    private boolean useHammer() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("hammer", HAMMER_COOLDOWN)) {
            return false;
        }
        Block targetBlock = player.getTargetBlockExact(30);
        Location center = targetBlock != null ? targetBlock.getLocation() : player.getLocation().getBlock().getLocation();
        for (int x = -HAMMER_RADIUS; x <= HAMMER_RADIUS; x++) {
            for (int z = -HAMMER_RADIUS; z <= HAMMER_RADIUS; z++) {
                dropAnvil(center.clone().add(x, 0, z));
            }
        }
        MessageUtil.broadcast("&8Ōkein a déclenché une pluie d'enclumes !");
        return true;
    }

    /**
     * Drop one marked anvil above a column.
     *
     * @param column Location in the column where the anvil lands
     */
    private void dropAnvil(Location column) {
        int topY = column.getWorld().getHighestBlockYAt(column.getBlockX(), column.getBlockZ());
        double spawnY = Math.max(column.getY(), topY) + 10;
        Location spawn = new Location(column.getWorld(), column.getBlockX() + 0.5, spawnY, column.getBlockZ() + 0.5);
        FallingBlock anvil = column.getWorld().spawnFallingBlock(spawn, Material.ANVIL.createBlockData());
        anvil.setDropItem(false);
        anvil.setHurtEntities(true);
        anvil.setDamagePerBlock(2.0f);
        anvil.setMaxDamage(ANVIL_DAMAGE);
        anvil.getPersistentDataContainer().set(new NamespacedKey(plugin, ANVIL_TAG), PersistentDataType.STRING, playerId.toString());
    }

    /**
     * Remember that a player was hit by one of Okein's anvils.
     *
     * @param victimId UUID of the player hit
     */
    public void registerAnvilHit(UUID victimId) {
        anvilVictims.put(victimId, System.currentTimeMillis());
        plugin.getCombatTracker().setLastDamager(victimId, playerId);
    }

    @Override
    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        Long hitTime = anvilVictims.get(victimId);
        if (hitTime == null || System.currentTimeMillis() - hitTime > KILL_ATTRIBUTION_MS) {
            return;
        }
        var data = getNGNLPlayer();
        if (data != null) {
            data.reduceCooldownTo("hammer", HAMMER_COOLDOWN, HAMMER_KILL_COOLDOWN);
            Player player = getPlayer();
            if (player != null) {
                MessageUtil.sendMessage(player, "&aKill à l'enclume : ton marteau se recharge en 5 minutes.");
            }
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Okein.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You learn the role of the first player to take damage (maybe yourself).",
                "You start with an anvil, 200 levels, Fire Resistance and a Flame book.",
                "You are the only role allowed to use fire. Water hurts you (1 heart/second)."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Hammer of Destruction: anvil rain on a 5x5 area, each anvil removes 4 hearts",
                "(every 15 minutes, 5 minutes if it kills)."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
