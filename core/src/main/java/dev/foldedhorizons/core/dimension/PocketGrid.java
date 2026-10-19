package dev.foldedhorizons.core.dimension;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * Static grid allocator for pocket cells. One custom void dimension hosts
 * everything as cells on a giant grid — no runtime dimension generation.
 *
 * <p>Grid pitch: 2048 blocks between cell centers. The lobby occupies cell
 * (0,0), which is reserved and never allocated. Allocation spirals outward
 * from the origin over a precomputed ring path indexed by the registry's
 * {@code next_free} cursor; occupied cells and the lobby are skipped
 * (amortized O(1) per allocation since the cursor only ever moves forward).
 * Each pocket type uses its own dimension + registry pair (e.g. exploit
 * tiers get {@code exploit_t1}, {@code exploit_t2}, ... with a per-team
 * offset cursor so teams strip different pockets of the same tier).</p>
 *
 * <p>Pure logic: no Minecraft classes are referenced here, so the spiral
 * math can be exercised standalone (see {@link PocketGridCheck#main}).</p>
 */
public final class PocketGrid {

    /** Blocks between cell centers. */
    public static final int PITCH = 2048;

    /** Cell key reserved for the lobby; never allocated to a pocket. */
    public static final String LOBBY_CELL = "0,0";

    /** Fixed Y of a cell's center block in the void dimensions. */
    public static final int CENTER_Y = 64;

    private PocketGrid() {}

    /** Immutable grid cell: integer grid coordinates plus its "x,z" key. */
    public record Cell(int x, int z) {
        public String key() {
            return x + "," + z;
        }
    }

    /**
     * Cell key for grid coordinates ("x,z"), matching the registry's
     * {@code cells} map keys.
     */
    public static String cellKey(int cellX, int cellZ) {
        return cellX + "," + cellZ;
    }

    /**
     * Parse a cell key back to grid coordinates, or null if malformed.
     */
    public static Cell parseKey(String key) {
        if (key == null) {
            return null;
        }
        int comma = key.indexOf(',');
        if (comma <= 0 || comma == key.length() - 1) {
            return null;
        }
        try {
            return new Cell(Integer.parseInt(key.substring(0, comma)),
                    Integer.parseInt(key.substring(comma + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * World position of a cell's center for the given grid coordinates.
     */
    public static BlockPos cellCenter(int cellX, int cellZ) {
        return new BlockPos(cellX * PITCH, CENTER_Y, cellZ * PITCH);
    }

    /**
     * Half-width in blocks of the reserved square around a cell center,
     * i.e. {@code (interiorSize - 1) / 2} for odd sizes.
     */
    public static int interiorHalfSize(int interiorSize) {
        if (interiorSize < 1) {
            throw new IllegalArgumentException("interiorSize must be >= 1");
        }
        return (interiorSize - 1) / 2;
    }

    /**
     * World-space bounds of a pocket's interior, centered on the cell
     * center: {@code minX = centerX - half, maxX = centerX + half} (same
     * for Z). With the default {@code pocket.interiorSize} of 33 the
     * interior is the 33×33 square around the center.
     */
    public static Bounds interiorBounds(int cellX, int cellZ, int interiorSize) {
        int half = interiorHalfSize(interiorSize);
        int cx = cellX * PITCH;
        int cz = cellZ * PITCH;
        return new Bounds(cx - half, cz - half, cx + half, cz + half);
    }

    /** Axis-aligned block bounds for a pocket interior. */
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(int worldX, int worldZ) {
            return worldX >= minX && worldX <= maxX && worldZ >= minZ && worldZ <= maxZ;
        }
    }

    /**
     * Shell-growing expansion (walls-expansion.md "Root space expansion"):
     * grow one cell's interior from its current size to {@code newSize}
     * without touching anything inside the old bounds. The difference ring
     * (old bounds → new bounds) is swept to air except the outermost ring,
     * which is filled with the shell block so the pocket stays sealed. Y is
     * swept over the same window the Materializer uses (center Y − 1 to
     * + 16) so template-placed content and the sweep agree.
     *
     * <p>Callers must check {@code newSize > currentSize}; equal-or-shrink
     * calls return false without touching the world (no shrinking — the
     * registry {@code expansion_stage} only climbs).</p>
     *
     * @return true if the sweep ran
     */
    public static boolean expandInterior(ServerLevel level, int cellX, int cellZ,
            int currentSize, int newSize, Block shellBlock) {
        if (newSize <= currentSize) {
            return false;
        }
        Bounds oldB = interiorBounds(cellX, cellZ, currentSize);
        Bounds newB = interiorBounds(cellX, cellZ, newSize);
        int centerY = CENTER_Y;
        int minY = centerY - 1;
        int maxY = centerY + 16;
        // Fill the outermost ring of the NEW bounds with shell, sweep the
        // rest of the difference region (new minus old) to air.
        for (int x = newB.minX(); x <= newB.maxX(); x++) {
            for (int z = newB.minZ(); z <= newB.maxZ(); z++) {
                boolean onNewEdge = x == newB.minX() || x == newB.maxX()
                        || z == newB.minZ() || z == newB.maxZ();
                boolean insideOld = oldB.contains(x, z);
                if (onNewEdge) {
                    for (int y = minY; y <= maxY; y++) {
                        level.setBlock(new BlockPos(x, y, z),
                                shellBlock.defaultBlockState(), Block.UPDATE_ALL);
                    }
                } else if (!insideOld) {
                    for (int y = minY; y <= maxY; y++) {
                        level.removeBlockEntity(new BlockPos(x, y, z));
                        level.setBlock(new BlockPos(x, y, z),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL);
                    }
                }
            }
        }
        return true;
    }

    /**
     * The spiral allocation path as grid cells, starting after the lobby:
     * index 0 of the returned list is the first allocated cell — the one a
     * {@code next_free} cursor of 0 points at. Rings walk outward from
     * (0,0), counter-clockwise, adjacent indices spatially adjacent; the
     * lobby itself never appears. Precompute once per registry and index by
     * the cursor for amortized O(1) allocation.
     *
     * <p>Ring {@code r} (half-width {@code r}) holds {@code 8r} cells,
     * walked counter-clockwise with corner ownership chosen so edges never
     * overlap:</p>
     * <pre>
     *   right  x == r,  z in [-(r-1), r]   (2r cells)
     *   top    z == r,  x in [-r, r-1]     (2r cells)
     *   left   x == -r, z in [-r, r-1]     (2r cells)
     *   bottom z == -r, x in [-r+1, r]     (2r cells)
     * </pre>
     * The next ring's first cell {@code (r+1, -(r-1))} sits adjacent to the
     * previous ring's last cell {@code (r, -r)}.
     *
     * @param maxCount number of cells to generate (path length)
     */
    public static List<Cell> spiralPath(int maxCount) {
        if (maxCount < 0) {
            throw new IllegalArgumentException("maxCount must be >= 0");
        }
        List<Cell> path = new ArrayList<>(maxCount);
        int r = 1;
        while (path.size() < maxCount) {
            // Right edge: x fixed at r, z walks -(r-1)..r.
            for (int z = -(r - 1); z <= r && path.size() < maxCount; z++) {
                path.add(new Cell(r, z));
            }
            // Top edge: z fixed at r, x walks r-1..-r.
            for (int x = r - 1; x >= -r && path.size() < maxCount; x--) {
                path.add(new Cell(x, r));
            }
            // Left edge: x fixed at -r, z walks r-1..-r.
            for (int z = r - 1; z >= -r && path.size() < maxCount; z--) {
                path.add(new Cell(-r, z));
            }
            // Bottom edge: z fixed at -r, x walks -r+1..r.
            for (int x = -r + 1; x <= r && path.size() < maxCount; x++) {
                path.add(new Cell(x, -r));
            }
            r++;
        }
        return path;
    }
}
