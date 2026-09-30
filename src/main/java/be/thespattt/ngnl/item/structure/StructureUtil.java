package be.thespattt.ngnl.item.structure;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Small helpers shared by the structure builders (dungeon, cloud).
 */
public final class StructureUtil {

    private StructureUtil() {
    }

    /**
     * Pick a random surface location inside a ring around a center.
     *
     * @param world       World to look in
     * @param centerX     Center X
     * @param centerZ     Center Z
     * @param minDistance Minimum distance from the center
     * @param maxDistance Maximum distance from the center
     * @return A surface location (block above the highest solid block)
     */
    public static Location randomSurfaceLocation(World world, double centerX, double centerZ, double minDistance, double maxDistance) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double distance = ThreadLocalRandom.current().nextDouble(minDistance, maxDistance);
            int x = (int) (centerX + Math.cos(angle) * distance);
            int z = (int) (centerZ + Math.sin(angle) * distance);
            Block top = world.getHighestBlockAt(x, z);
            if (!top.isLiquid() && top.getType() != Material.WATER && top.getType() != Material.LAVA) {
                return top.getLocation().add(0.5, 1, 0.5);
            }
        }
        int x = (int) centerX;
        int z = (int) centerZ;
        return world.getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
    }

    /**
     * Fill a box with a material.
     *
     * @param origin   Minimum corner
     * @param sizeX    Size along X
     * @param sizeY    Size along Y
     * @param sizeZ    Size along Z
     * @param material Material to place
     */
    public static void fillBox(Location origin, int sizeX, int sizeY, int sizeZ, Material material) {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    origin.getWorld().getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z).setType(material, false);
                }
            }
        }
    }
}
