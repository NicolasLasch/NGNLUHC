package be.thespattt.ngnl.item.structure;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The special library of the Flügel faction: a small room with an enchanting table surrounded by
 * bookshelves (maximum enchanting power). Only Flügel may use the table, once per game each.
 */
public class FlugelLibrary {

    private static final int ROOM_SIZE = 9;
    private static final int ROOM_HEIGHT = 4;

    private Location tableLocation;
    private final Set<UUID> usedBy = new HashSet<>();

    /**
     * Build the library at a surface location.
     *
     * @param ground Surface location (center of the room)
     */
    public void build(Location ground) {
        usedBy.clear();
        Location corner = ground.clone().add(-ROOM_SIZE / 2, -1, -ROOM_SIZE / 2);
        StructureUtil.fillBox(corner, ROOM_SIZE, ROOM_HEIGHT + 2, ROOM_SIZE, Material.STONE_BRICKS);
        StructureUtil.fillBox(corner.clone().add(1, 1, 1), ROOM_SIZE - 2, ROOM_HEIGHT, ROOM_SIZE - 2, Material.AIR);
        openDoorway(ground);
        placeBookshelves(ground);
        tableLocation = ground.getBlock().getLocation();
        tableLocation.getBlock().setType(Material.ENCHANTING_TABLE, false);
        ground.getWorld().getBlockAt(ground.getBlockX(), ground.getBlockY() + ROOM_HEIGHT - 1, ground.getBlockZ())
                .setType(Material.LANTERN, false);
    }

    /**
     * Open a 3x3 entrance in the south wall.
     *
     * @param ground Center of the room
     */
    private void openDoorway(Location ground) {
        World world = ground.getWorld();
        int wallZ = ground.getBlockZ() + ROOM_SIZE / 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                world.getBlockAt(ground.getBlockX() + dx, ground.getBlockY() + dy, wallZ).setType(Material.AIR, false);
            }
        }
    }

    /**
     * Ring of bookshelves at distance 2 from the table (two layers), leaving the sides open.
     *
     * @param ground Center of the room
     */
    private void placeBookshelves(Location ground) {
        World world = ground.getWorld();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean gap = dx == 0 || dz == 0;
                if (ring && !gap) {
                    for (int dy = 0; dy <= 1; dy++) {
                        world.getBlockAt(ground.getBlockX() + dx, ground.getBlockY() + dy, ground.getBlockZ() + dz)
                                .setType(Material.BOOKSHELF, false);
                    }
                }
            }
        }
    }

    /**
     * Check whether a block is the library's enchanting table.
     *
     * @param block Block to test
     * @return True for the library table
     */
    public boolean isTable(Block block) {
        return tableLocation != null && block != null && block.getLocation().equals(tableLocation);
    }

    /**
     * Check whether a player already used the table.
     *
     * @param playerId UUID of the player
     * @return True if he used it
     */
    public boolean hasUsed(UUID playerId) {
        return usedBy.contains(playerId);
    }

    /**
     * Remember that a player used the table.
     *
     * @param playerId UUID of the player
     */
    public void markUsed(UUID playerId) {
        usedBy.add(playerId);
    }

    /**
     * Location of the enchanting table.
     *
     * @return Table location, or null if not built
     */
    public Location getLocation() {
        return tableLocation;
    }
}
