package be.thespattt.ngnl.util;

import be.thespattt.ngnl.NoGameNoLife;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the decoy clones of players (Sora/Shiro/Izuna crowns, Fiel's illusion).
 * Clones are plain armor stands wearing the owner's head and armor, so the plugin
 * does not depend on any packet library (NPCLib / ProtocolLib / PacketEvents).
 */
public class AdvancedCloneManager implements Listener {

    /** How far (in blocks) a wandering clone may walk away from its owner. */
    private static final double WANDER_RADIUS = 6.0;
    /** Distance covered by a clone every movement step (blocks). */
    private static final double WANDER_STEP = 0.25;
    /** Ticks between two choices of a new wander destination. */
    private static final int WANDER_RETARGET_TICKS = 40;
    /** Persistent-data key marking an armor stand as a clone. */
    private static final String CLONE_TAG = "ngnl_clone";

    private final NoGameNoLife plugin;
    private final NamespacedKey cloneKey;
    private final Random random = new Random();
    private final Map<UUID, List<CloneEntry>> playerClones = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cloneTasks = new HashMap<>();

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public AdvancedCloneManager(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.cloneKey = new NamespacedKey(plugin, CLONE_TAG);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------------ public API

    /**
     * Spawn several wandering clones around a player.
     *
     * @param player          Owner of the clones
     * @param amount          Number of clones
     * @param durationSeconds Lifetime of the clones in seconds
     * @return True if at least one clone was created
     */
    public boolean spawnClones(Player player, int amount, int durationSeconds) {
        removeClones(player.getUniqueId());

        List<CloneEntry> clones = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            Location spot = ringLocation(player.getLocation(), i, amount, 2.5);
            ArmorStand stand = createCloneStand(player, spot);
            if (stand != null) {
                clones.add(new CloneEntry(stand, spot.clone()));
            }
        }

        if (clones.isEmpty()) {
            return false;
        }

        playerClones.put(player.getUniqueId(), clones);
        startWanderTask(player, clones, durationSeconds);
        return true;
    }

    /**
     * Place one motionless decoy of a player at a chosen location.
     *
     * @param owner           Owner of the decoy
     * @param location        Where the decoy stands
     * @param durationSeconds Lifetime of the decoy in seconds
     * @return The created decoy or null if it could not be created
     */
    public ArmorStand placeDecoy(Player owner, Location location, int durationSeconds) {
        removeClones(owner.getUniqueId());

        ArmorStand stand = createCloneStand(owner, location);
        if (stand == null) {
            return null;
        }

        List<CloneEntry> clones = new ArrayList<>();
        clones.add(new CloneEntry(stand, location.clone()));
        playerClones.put(owner.getUniqueId(), clones);
        startExpiryTask(owner.getUniqueId(), durationSeconds);
        return stand;
    }

    /**
     * Swap the owner's position with his first living decoy.
     *
     * @param owner Owner of the decoy
     * @return True if the swap happened
     */
    public boolean swapWithDecoy(Player owner) {
        List<CloneEntry> clones = playerClones.get(owner.getUniqueId());
        if (clones == null || clones.isEmpty()) {
            return false;
        }

        ArmorStand decoy = clones.get(0).stand;
        if (decoy.isDead()) {
            return false;
        }

        Location ownerSpot = owner.getLocation().clone();
        Location decoySpot = decoy.getLocation().clone();
        decoySpot.setYaw(owner.getLocation().getYaw());
        decoySpot.setPitch(owner.getLocation().getPitch());
        owner.teleport(decoySpot);
        decoy.teleport(ownerSpot);
        spawnCloneParticles(ownerSpot);
        spawnCloneParticles(decoySpot);
        return true;
    }

    /**
     * Remove every clone owned by a player.
     *
     * @param playerId UUID of the owner
     */
    public void removeClones(UUID playerId) {
        List<CloneEntry> clones = playerClones.remove(playerId);
        if (clones != null) {
            for (CloneEntry clone : clones) {
                discardClone(clone.stand);
            }
        }

        BukkitRunnable task = cloneTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Remove every clone of every player.
     */
    public void removeAllClones() {
        Set<UUID> playerIds = new HashSet<>(playerClones.keySet());
        for (UUID playerId : playerIds) {
            removeClones(playerId);
        }
    }

    /**
     * Check if a player currently owns clones.
     *
     * @param playerId UUID of the owner
     * @return True if he has at least one living clone
     */
    public boolean hasClones(UUID playerId) {
        return getCloneCount(playerId) > 0;
    }

    /**
     * Count the living clones of a player.
     *
     * @param playerId UUID of the owner
     * @return Number of living clones
     */
    public int getCloneCount(UUID playerId) {
        List<CloneEntry> clones = playerClones.get(playerId);
        if (clones == null) {
            return 0;
        }
        int count = 0;
        for (CloneEntry clone : clones) {
            if (!clone.stand.isDead()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Clean everything up (plugin disable / game end).
     */
    public void cleanup() {
        removeAllClones();
    }

    // ------------------------------------------------------------------ listeners

    /**
     * A clone disappears (with particles) as soon as somebody hits it.
     *
     * @param event Damage event
     */
    @EventHandler
    public void onCloneDamaged(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!isClone(entity)) {
            return;
        }
        event.setCancelled(true);
        spawnCloneParticles(entity.getLocation());
        entity.remove();
    }

    // ------------------------------------------------------------------ creation helpers

    /**
     * Compute a spot on a ring around a center.
     *
     * @param center Ring center
     * @param index  Index of the spot
     * @param total  Number of spots on the ring
     * @param radius Radius of the ring
     * @return The spot, oriented toward the center
     */
    private Location ringLocation(Location center, int index, int total, double radius) {
        double angle = (2 * Math.PI * index) / total;
        Location spot = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
        spot.setYaw((float) Math.toDegrees(angle + Math.PI));
        return spot;
    }

    /**
     * Create the armor stand that represents a clone of a player.
     *
     * @param owner    Player to imitate
     * @param location Spawn location
     * @return The armor stand or null on failure
     */
    private ArmorStand createCloneStand(Player owner, Location location) {
        try {
            ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
            configureStand(stand, owner);
            copyAppearance(owner, stand);
            spawnCloneParticles(location);
            return stand;
        } catch (Exception exception) {
            MessageUtil.logError("Failed to create clone for " + owner.getName(), exception);
            return null;
        }
    }

    /**
     * Apply the fixed properties of a clone armor stand.
     *
     * @param stand Armor stand
     * @param owner Owner of the clone
     */
    private void configureStand(ArmorStand stand, Player owner) {
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(true);
        stand.setCanPickupItems(false);
        stand.setRemoveWhenFarAway(false);
        stand.setPersistent(false);
        stand.setCustomName(owner.getName());
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(cloneKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }
    }

    /**
     * Copy the visible equipment of a player on a clone (head, armor, hands).
     *
     * @param owner Player to imitate
     * @param stand Clone armor stand
     */
    private void copyAppearance(Player owner, ArmorStand stand) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setHelmet(buildHead(owner, owner.getInventory().getHelmet()));
        equipment.setChestplate(copyOrNull(owner.getInventory().getChestplate()));
        equipment.setLeggings(copyOrNull(owner.getInventory().getLeggings()));
        equipment.setBoots(copyOrNull(owner.getInventory().getBoots()));
        equipment.setItemInMainHand(copyOrNull(owner.getInventory().getItemInMainHand()));
        equipment.setItemInOffHand(copyOrNull(owner.getInventory().getItemInOffHand()));
    }

    /**
     * Build the head item of a clone: the owner's helmet if he wears one, else his skull.
     *
     * @param owner  Player to imitate
     * @param helmet Helmet worn by the player (may be null)
     * @return Item placed on the clone head
     */
    private ItemStack buildHead(Player owner, ItemStack helmet) {
        if (helmet != null && helmet.getType() != Material.AIR) {
            return helmet.clone();
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(owner);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Clone an item, returning null for empty slots.
     *
     * @param item Item to copy
     * @return A copy of the item or null
     */
    private ItemStack copyOrNull(ItemStack item) {
        return item != null && item.getType() != Material.AIR ? item.clone() : null;
    }

    // ------------------------------------------------------------------ movement / lifetime

    /**
     * Start the task that makes clones wander around and expires them.
     *
     * @param owner           Owner of the clones
     * @param clones          The clones
     * @param durationSeconds Lifetime in seconds
     */
    private void startWanderTask(Player owner, List<CloneEntry> clones, int durationSeconds) {
        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                boolean expired = ticks >= durationSeconds * 20;
                if (expired || !owner.isOnline() || getCloneCount(owner.getUniqueId()) == 0) {
                    removeClones(owner.getUniqueId());
                    return;
                }

                if (ticks % WANDER_RETARGET_TICKS == 0) {
                    clones.forEach(clone -> chooseNewTarget(owner, clone));
                }
                clones.forEach(AdvancedCloneManager.this::stepTowardTarget);
                ticks++;
            }
        };

        task.runTaskTimer(plugin, 1L, 1L);
        cloneTasks.put(owner.getUniqueId(), task);
    }

    /**
     * Start the task that only expires clones after a delay.
     *
     * @param ownerId         Owner of the clones
     * @param durationSeconds Lifetime in seconds
     */
    private void startExpiryTask(UUID ownerId, int durationSeconds) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                removeClones(ownerId);
            }
        };
        task.runTaskLater(plugin, durationSeconds * 20L);
        cloneTasks.put(ownerId, task);
    }

    /**
     * Choose a new random destination near the owner for a clone.
     *
     * @param owner Owner of the clone
     * @param clone Clone to move
     */
    private void chooseNewTarget(Player owner, CloneEntry clone) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 1.5 + random.nextDouble() * WANDER_RADIUS;
        Location base = owner.getLocation();
        Location target = base.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        target.setY(findGroundY(target));
        clone.target = target;
    }

    /**
     * Move a clone one small step toward its destination.
     *
     * @param clone Clone to move
     */
    private void stepTowardTarget(CloneEntry clone) {
        if (clone.stand.isDead() || clone.target == null) {
            return;
        }
        Location current = clone.stand.getLocation();
        if (current.getWorld() != clone.target.getWorld()) {
            clone.target = null;
            return;
        }

        double dx = clone.target.getX() - current.getX();
        double dz = clone.target.getZ() - current.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < WANDER_STEP) {
            return;
        }

        Location next = current.clone().add(dx / distance * WANDER_STEP, 0, dz / distance * WANDER_STEP);
        next.setY(findGroundY(next));
        next.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        clone.stand.teleport(next);
    }

    /**
     * Find the Y coordinate of the ground under a location.
     *
     * @param location Location to inspect
     * @return Y coordinate a clone should stand at
     */
    private double findGroundY(Location location) {
        int y = location.getWorld().getHighestBlockYAt(location);
        double reference = location.getY();
        // Stay on the owner's floor level when the highest block is a far away roof.
        return Math.abs(y + 1 - reference) > 6 ? reference : y + 1;
    }

    // ------------------------------------------------------------------ misc helpers

    /**
     * Check whether an entity is a clone armor stand.
     *
     * @param entity Entity to check
     * @return True if the entity is a clone
     */
    public boolean isClone(Entity entity) {
        return entity instanceof ArmorStand
                && entity.getPersistentDataContainer().has(cloneKey, PersistentDataType.STRING);
    }

    /**
     * Remove a clone armor stand with a small particle effect.
     *
     * @param stand Armor stand to remove
     */
    private void discardClone(ArmorStand stand) {
        if (stand != null && !stand.isDead()) {
            spawnCloneParticles(stand.getLocation());
            stand.remove();
        }
    }

    /**
     * Spawn the particles displayed when a clone appears or vanishes.
     *
     * @param location Where the effect is displayed
     */
    private void spawnCloneParticles(Location location) {
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(Particle.ENCHANT, location.clone().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /**
     * A clone and the destination it is walking to.
     */
    private static final class CloneEntry {
        private final ArmorStand stand;
        private Location target;

        private CloneEntry(ArmorStand stand, Location target) {
            this.stand = stand;
            this.target = target;
        }
    }
}
