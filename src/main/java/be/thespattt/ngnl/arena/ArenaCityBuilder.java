package be.thespattt.ngnl.arena;

import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Generates a simple "Love Fight" city (hollow towers of various heights) in the fallback arena
 * world used when the server owner did not provide a custom map. It is built only once per world.
 */
public class ArenaCityBuilder {

    private static final String MARKER_FILE = "ngnl_city.built";
    /** Size of a city cell in blocks. */
    private static final int CELL_SIZE = 24;
    /** Distance from the map center kept free (central plaza). */
    private static final int PLAZA_RADIUS = 14;
    /** Distance kept free around every player spawn point. */
    private static final int SPAWN_CLEARANCE = 16;
    /** Chance (percent) that a cell holds a tower. */
    private static final int TOWER_CHANCE = 60;

    private static final Material[] WALLS = {
            Material.STONE_BRICKS, Material.DEEPSLATE_BRICKS, Material.QUARTZ_BRICKS, Material.BRICKS, Material.POLISHED_ANDESITE
    };

    private final Random random = new Random();

    /**
     * Build the city once (a marker file in the world folder remembers it).
     *
     * @param world        Arena world
     * @param cityRadius   Radius of the area covered by the city
     * @param groundY      Y coordinate of the ground surface
     * @param spawnPoints  X/Z of the player spawn points (kept clear of towers)
     */
    public void buildIfMissing(World world, int cityRadius, int groundY, List<int[]> spawnPoints) {
        File marker = new File(world.getWorldFolder(), MARKER_FILE);
        if (marker.exists()) {
            return;
        }
        MessageUtil.logInfo("Generating the fallback arena city (first start only)...");
        for (int cellX = -cityRadius; cellX < cityRadius; cellX += CELL_SIZE) {
            for (int cellZ = -cityRadius; cellZ < cityRadius; cellZ += CELL_SIZE) {
                buildCell(world, cellX, cellZ, groundY, spawnPoints);
            }
        }
        try {
            marker.createNewFile();
        } catch (IOException exception) {
            MessageUtil.logWarning("Could not write the city marker file: " + exception.getMessage());
        }
    }

    /**
     * Possibly place one tower inside a city cell.
     *
     * @param world       Arena world
     * @param cellX       Cell minimum X
     * @param cellZ       Cell minimum Z
     * @param groundY     Ground level
     * @param spawnPoints Spawn points to keep clear
     */
    private void buildCell(World world, int cellX, int cellZ, int groundY, List<int[]> spawnPoints) {
        if (random.nextInt(100) >= TOWER_CHANCE) {
            return;
        }
        int width = 6 + random.nextInt(7);
        int depth = 6 + random.nextInt(7);
        int originX = cellX + 2 + random.nextInt(CELL_SIZE - width - 3);
        int originZ = cellZ + 2 + random.nextInt(CELL_SIZE - depth - 3);
        int centerX = originX + width / 2;
        int centerZ = originZ + depth / 2;

        if (Math.abs(centerX) < PLAZA_RADIUS && Math.abs(centerZ) < PLAZA_RADIUS) {
            return;
        }
        for (int[] spawn : spawnPoints) {
            if (Math.abs(spawn[0] - centerX) < SPAWN_CLEARANCE + width && Math.abs(spawn[1] - centerZ) < SPAWN_CLEARANCE + depth) {
                return;
            }
        }
        buildTower(world, originX, originZ, width, depth, groundY, 8 + random.nextInt(29));
    }

    /**
     * Build a hollow tower with floors every 6 blocks, windows and a door.
     *
     * @param world   Arena world
     * @param originX Minimum X
     * @param originZ Minimum Z
     * @param width   Size along X
     * @param depth   Size along Z
     * @param groundY Ground level (first floor is at groundY)
     * @param height  Height of the walls
     */
    private void buildTower(World world, int originX, int originZ, int width, int depth, int groundY, int height) {
        Material wall = WALLS[random.nextInt(WALLS.length)];
        for (int y = 0; y <= height; y++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < depth; dz++) {
                    Material material = pickBlock(wall, dx, dz, y, width, depth, height);
                    world.getBlockAt(originX + dx, groundY + y, originZ + dz).setType(material, false);
                }
            }
        }
        carveDoor(world, originX + width / 2, originZ, groundY);
        addLadder(world, originX + 1, originZ + 1, groundY, height);
    }

    /**
     * Choose the block for a position of the tower.
     *
     * @param wall   Wall material of the tower
     * @param dx     X offset inside the tower
     * @param dz     Z offset inside the tower
     * @param y      Height offset
     * @param width  Tower width
     * @param depth  Tower depth
     * @param height Tower height
     * @return Material to place
     */
    private Material pickBlock(Material wall, int dx, int dz, int y, int width, int depth, int height) {
        boolean edge = dx == 0 || dz == 0 || dx == width - 1 || dz == depth - 1;
        if (y == 0 || y == height || y % 6 == 0) {
            return edge || y != height ? (y == height && !edge ? wall : (edge ? wall : Material.OAK_PLANKS)) : wall;
        }
        if (!edge) {
            return Material.AIR;
        }
        boolean window = y % 6 >= 2 && y % 6 <= 3 && (dx + dz) % 3 == 1;
        return window ? Material.GLASS_PANE : wall;
    }

    /**
     * Open a 2x3 doorway in the wall facing -Z.
     *
     * @param world   Arena world
     * @param doorX   X of the door
     * @param wallZ   Z of the wall
     * @param groundY Ground level
     */
    private void carveDoor(World world, int doorX, int wallZ, int groundY) {
        for (int dx = 0; dx < 2; dx++) {
            for (int y = 1; y <= 2; y++) {
                world.getBlockAt(doorX + dx, groundY + y, wallZ).setType(Material.AIR, false);
            }
        }
    }

    /**
     * Place a ladder column and open the floors above it so the tower can be climbed.
     *
     * @param world   Arena world
     * @param x       Ladder X
     * @param z       Ladder Z
     * @param groundY Ground level
     * @param height  Tower height
     */
    private void addLadder(World world, int x, int z, int groundY, int height) {
        for (int y = 1; y < height; y++) {
            Location spot = new Location(world, x, groundY + y, z);
            spot.getBlock().setType(Material.LADDER, false);
            if (spot.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional directional) {
                directional.setFacing(org.bukkit.block.BlockFace.EAST);
                spot.getBlock().setBlockData(directional, false);
            }
        }
        world.getBlockAt(x - 1, groundY + height, z).setType(Material.AIR, false);
    }
}
