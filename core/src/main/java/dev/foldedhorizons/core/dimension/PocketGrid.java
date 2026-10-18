package dev.foldedhorizons.core.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Static grid allocator for pocket cells. One custom void dimension hosts
 * everything as cells on a giant grid — no runtime dimension generation.
 *
 * <p>Grid pitch: 2048 blocks between cell centers. The lobby occupies cell
 * (0,0), which is reserved and never allocated. Allocation spirals outward
 * from the origin; each pocket type uses its own dimension + registry pair
 * (e.g. exploit tiers get {@code exploit_t1}, {@code exploit_t2}, ... with a
 * per-team offset cursor so teams strip different pockets of the same tier).</p>
 */
public final class PocketGrid {

    /** Blocks between cell centers. */
    public static final int PITCH = 2048;

    /** Cell key reserved for the lobby; never allocated to a pocket. */
    public static final String LOBBY_CELL = "0,0";

    private PocketGrid() {}

    /**
     * World position of a cell's center for the given grid coordinates.
     */
    public static BlockPos cellCenter(int cellX, int cellZ) {
        return new BlockPos(cellX * PITCH, 64, cellZ * PITCH);
    }

    /**
     * Allocate the next free cell for an owner, spiraling outward.
     * TODO: consult {@link PocketRegistry#byOwner} authority ("does this
     * owner already have a root"), skip reserved cells, persist the cursor.
     */
    public static String allocateNext(ResourceKey<Level> dimension, Object owner) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
