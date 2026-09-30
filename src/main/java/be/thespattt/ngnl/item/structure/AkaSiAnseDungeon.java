package be.thespattt.ngnl.item.structure;

import be.thespattt.ngnl.NoGameNoLife;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * The hidden dungeon of the Aka Si Anse and its guardian, Nina Clive: a fragile elf (low health)
 * with a huge power (spells and fireballs). She drops the Aka Si Anse when defeated.
 */
public class AkaSiAnseDungeon {

    /** Persistent-data key marking Nina Clive. */
    public static final String NINA_TAG = "nina_clive";

    private static final int ROOM_SIZE = 15;
    private static final int ROOM_HEIGHT = 8;
    /** Health (HP) of Nina Clive: fragile. */
    private static final double NINA_HEALTH = 30.0;
    /** Ticks between two fireballs thrown by Nina. */
    private static final long FIREBALL_PERIOD = 100L;
    /** Range (blocks) in which Nina attacks. */
    private static final double FIREBALL_RANGE = 25.0;

    private final NoGameNoLife plugin;
    private Location center;
    private Evoker nina;
    private BukkitTask attackTask;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public AkaSiAnseDungeon(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Build the dungeon at a location and spawn Nina Clive inside.
     *
     * @param groundLocation Surface location where the dungeon stands
     */
    public void build(Location groundLocation) {
        this.center = groundLocation.clone();
        Location corner = groundLocation.clone().add(-ROOM_SIZE / 2, -1, -ROOM_SIZE / 2);
        StructureUtil.fillBox(corner, ROOM_SIZE, ROOM_HEIGHT + 2, ROOM_SIZE, Material.STONE_BRICKS);
        StructureUtil.fillBox(corner.clone().add(1, 1, 1), ROOM_SIZE - 2, ROOM_HEIGHT, ROOM_SIZE - 2, Material.AIR);
        addDoorway(groundLocation);
        addTorches(groundLocation);
        spawnNina();
    }

    /**
     * Open a 3x3 entrance on the south wall.
     *
     * @param ground Surface location of the dungeon center
     */
    private void addDoorway(Location ground) {
        int doorZ = ground.getBlockZ() + ROOM_SIZE / 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                ground.getWorld().getBlockAt(ground.getBlockX() + dx, ground.getBlockY() + dy, doorZ).setType(Material.AIR, false);
            }
        }
    }

    /**
     * Light the room with lanterns on the ceiling.
     *
     * @param ground Surface location of the dungeon center
     */
    private void addTorches(Location ground) {
        int y = ground.getBlockY() + ROOM_HEIGHT - 1;
        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dz = -4; dz <= 4; dz += 4) {
                ground.getWorld().getBlockAt(ground.getBlockX() + dx, y, ground.getBlockZ() + dz).setType(Material.SOUL_LANTERN, false);
            }
        }
    }

    /**
     * Spawn the guardian: an evoker called Nina Clive with low health.
     */
    private void spawnNina() {
        World world = center.getWorld();
        nina = (Evoker) world.spawnEntity(center.clone().add(0, 0.5, 0), org.bukkit.entity.EntityType.EVOKER);
        nina.setCustomName("§5§lNina Clive");
        nina.setCustomNameVisible(true);
        nina.setRemoveWhenFarAway(false);
        nina.setPersistent(true);
        nina.setCanJoinRaid(false);
        nina.getAttribute(Attribute.MAX_HEALTH).setBaseValue(NINA_HEALTH);
        nina.setHealth(NINA_HEALTH);
        nina.getPersistentDataContainer().set(new NamespacedKey(plugin, NINA_TAG), PersistentDataType.BYTE, (byte) 1);
        attackTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::throwFireball, FIREBALL_PERIOD, FIREBALL_PERIOD);
    }

    /**
     * Throw a fireball at the nearest player within range.
     */
    private void throwFireball() {
        if (nina == null || nina.isDead()) {
            stop();
            return;
        }
        Player target = null;
        double best = FIREBALL_RANGE * FIREBALL_RANGE;
        for (Player candidate : nina.getWorld().getPlayers()) {
            boolean valid = plugin.getGameManager().isPlayerAlive(candidate.getUniqueId());
            double distance = candidate.getLocation().distanceSquared(nina.getLocation());
            if (valid && distance < best) {
                best = distance;
                target = candidate;
            }
        }
        if (target == null) {
            return;
        }
        Vector direction = target.getEyeLocation().toVector().subtract(nina.getEyeLocation().toVector()).normalize();
        SmallFireball fireball = nina.getWorld().spawn(nina.getEyeLocation().add(direction), SmallFireball.class);
        fireball.setShooter(nina);
        fireball.setDirection(direction);
        fireball.setIsIncendiary(false);
    }

    /**
     * Check whether an entity is Nina Clive.
     *
     * @param entity Entity to check
     * @return True for Nina Clive
     */
    public boolean isNina(Entity entity) {
        return entity.getPersistentDataContainer().has(new NamespacedKey(plugin, NINA_TAG), PersistentDataType.BYTE);
    }

    /**
     * Stop Nina's attacks.
     */
    public void stop() {
        if (attackTask != null) {
            attackTask.cancel();
            attackTask = null;
        }
    }

    /**
     * Remove Nina from the world (game end).
     */
    public void remove() {
        stop();
        if (nina != null && !nina.isDead()) {
            nina.remove();
        }
        nina = null;
    }

    /**
     * Exact center of the dungeon.
     *
     * @return Dungeon center, or null if not built
     */
    public Location getCenter() {
        return center;
    }
}
