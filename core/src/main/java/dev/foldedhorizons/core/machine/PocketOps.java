package dev.foldedhorizons.core.machine;

import java.util.UUID;
import dev.foldedhorizons.core.dimension.PocketDimensions;
import dev.foldedhorizons.core.dimension.PocketGrid;
import dev.foldedhorizons.core.dimension.PocketInstance;
import dev.foldedhorizons.core.dimension.PocketRegistry;
import dev.foldedhorizons.core.dimension.PocketType;
import dev.foldedhorizons.core.identity.TeamIdentityProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Shared lookup helpers for the core machines (breacher + root binder).
 * Keeps the "find the owner's ROOT cell in void_space" and "which pocket
 * is this position inside" questions in one place so both machines and the
 * conversion ticker agree on the answers.
 */
/**
 * Shared pocket operations used by machines, items and tickers across the
 * core mod. Package-private helpers live here as statics; this class is the
 * one stop for "find the owner's cell", "where do I stand after a breach"
 * and "tell the player".
 */
public final class PocketOps {

    /** Allocation scan guard, matching the registry's contract. */
    public static final int ALLOC_SCAN = 4096;

    private PocketOps() {}

    /**
     * The ROOT pocket owned by {@code owner} in the void grid, or null.
     * An owner holds exactly one ROOT per the by_owner contract, so the
     * first match is authoritative.
     */
    public static PocketInstance findRoot(PocketRegistry registry, UUID owner) {
        for (String cellKey : registry.ownedCells(owner)) {
            PocketInstance instance = registry.get(PocketDimensions.VOID_SPACE, cellKey);
            if (instance != null && instance.type() == PocketType.ROOT) {
                return instance;
            }
        }
        return null;
    }

    /**
     * The pocket whose interior contains {@code pos} in {@code level}'s
     * dimension (the "is this breacher a return pad inside a pocket?"
     * test), or null. Checks the default 33-wide interior bounds.
     */
    public static PocketInstance pocketContaining(PocketRegistry registry, Level level, BlockPos pos) {
        if (!level.dimension().equals(PocketDimensions.VOID_SPACE)) {
            return null;
        }
        for (PocketInstance instance : registry.cellsIn(PocketDimensions.VOID_SPACE).values()) {
            PocketGrid.Cell cell = PocketGrid.parseKey(instance.cellKey());
            if (cell == null) {
                continue;
            }
            PocketGrid.Bounds bounds = PocketGrid.interiorBounds(cell.x(), cell.z(), 33);
            if (bounds.contains(pos.getX(), pos.getZ())) {
                return instance;
            }
        }
        return null;
    }

    /**
     * Seconds remaining on the pending conversion queued for
     * {@code cellKey}, or -1 when none is pending (or already expired but
     * not yet applied — the ticker applies it within its 10-tick cadence).
     */
    public static int pendingSecondsRemaining(PocketRegistry registry, String cellKey, long currentTick) {
        PocketRegistry.PendingConversion pending = registry.pendingConversionFor(cellKey);
        if (pending == null) {
            return -1;
        }
        return (int) Math.max(0, (pending.atExpiry() - currentTick) / 20);
    }

    /** The server's void grid level, or null if a datapack removed it. */
    static ServerLevel voidSpace(MinecraftServer server) {
        return server.getLevel(PocketDimensions.VOID_SPACE);
    }

    /** Action-bar feedback helper (server side only). */
    public static void say(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    /**
     * The world-space entry point of {@code pocket}: the cell center raised
     * above the interior floor (starter-cell floor sits at CENTER_Y+1).
     */
    public static BlockPos entryPoint(PocketInstance pocket) {
        PocketGrid.Cell cell = PocketGrid.parseKey(pocket.cellKey());
        if (cell == null) {
            return PocketGrid.cellCenter(0, 0);
        }
        return PocketGrid.cellCenter(cell.x(), cell.z()).above(2);
    }
}
