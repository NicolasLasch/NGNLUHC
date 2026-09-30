package be.thespattt.ngnl.item.structure;

import be.thespattt.ngnl.NoGameNoLife;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * The cloud near the spawn that holds the Suniaster: a flat blob of white wool high in the sky
 * with the item floating at its center.
 */
public class SuniasterCloud {

    /** Height (above the highest block) of the cloud. */
    private static final int CLOUD_ALTITUDE = 70;
    private static final int CLOUD_RADIUS = 7;

    private final NoGameNoLife plugin;
    private Location center;
    private Item floatingItem;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public SuniasterCloud(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Build the cloud above a surface location and place the item on it.
     *
     * @param surface  Surface location under the cloud
     * @param suniaster The Suniaster item
     */
    public void build(Location surface, ItemStack suniaster) {
        World world = surface.getWorld();
        int y = Math.min(world.getMaxHeight() - 10, surface.getBlockY() + CLOUD_ALTITUDE);
        center = new Location(world, surface.getBlockX() + 0.5, y, surface.getBlockZ() + 0.5);

        for (int x = -CLOUD_RADIUS; x <= CLOUD_RADIUS; x++) {
            for (int z = -CLOUD_RADIUS; z <= CLOUD_RADIUS; z++) {
                placeCloudColumn(world, x, z);
            }
        }

        floatingItem = world.dropItem(center.clone().add(0, 1.2, 0), suniaster);
        floatingItem.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        floatingItem.setGravity(false);
        floatingItem.setUnlimitedLifetime(true);
        floatingItem.setGlowing(true);
    }

    /**
     * Place one column of the cloud (thicker in the middle).
     *
     * @param world World
     * @param dx    X offset from the center
     * @param dz    Z offset from the center
     */
    private void placeCloudColumn(World world, int dx, int dz) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > CLOUD_RADIUS) {
            return;
        }
        int thickness = distance < CLOUD_RADIUS * 0.5 ? 3 : distance < CLOUD_RADIUS * 0.8 ? 2 : 1;
        for (int dy = 0; dy < thickness; dy++) {
            world.getBlockAt(center.getBlockX() + dx, center.getBlockY() - dy, center.getBlockZ() + dz)
                    .setType(Material.WHITE_WOOL, false);
        }
    }

    /**
     * Remove the floating item if it is still there (game end).
     */
    public void remove() {
        if (floatingItem != null && !floatingItem.isDead()) {
            floatingItem.remove();
        }
        floatingItem = null;
    }

    /**
     * Exact center of the cloud.
     *
     * @return Cloud center, or null if not built
     */
    public Location getCenter() {
        return center;
    }
}
