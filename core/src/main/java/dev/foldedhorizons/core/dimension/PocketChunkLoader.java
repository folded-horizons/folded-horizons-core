package dev.foldedhorizons.core.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Permanent chunk loading for materialized pockets (docs
 * {@code pocket-system.md} "Claiming" paragraph: all permanent pockets —
 * ROOT, REPEATABLE, SPECIAL — are auto-claimed at allocation with no claim
 * limit and permanently chunk-loaded).
 *
 * <p>Mechanism (core-side choice): a vanilla {@link TicketType#PORTAL}
 * ticket with the maximum radius is re-added every second by a repeating
 * {@code ServerTickEvent.Post} — tickets are the simplest persistent-load
 * mechanism core can own without an FTB dependency. The foldedhorizons-ftb
 * module may swap in FTB forced-chunk saving later; the ticket approach
 * needs no extra saved data and self-heals each tick.</p>
 */
public final class PocketChunkLoader {

    /** Re-arm cadence in ticks; well inside any unload grace period. */
    private static final int REARM_CADENCE_TICKS = 20;

    /** Ticket radius (in chunks) around a pocket cell that stays loaded. */
    private static final int TICKET_RADIUS = 2;

    /** Ticket type used for permanent pocket loads; BlockPos-level ticket. */
    private static final TicketType<BlockPos> POCKET_TICKET =
            TicketType.create("foldedhorizons:pocket", java.util.Comparator.comparingLong(pos -> pos.asLong()));

    private PocketChunkLoader() {}

    /**
     * Register the repeating re-arm hook. Called once from the core mod
     * constructor alongside the other {@code @EventBusSubscriber} wiring.
     */
    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new RearmHook());
    }

    /** Whether {@code pos} in {@code level} is inside a pocket cell region. */
    private static boolean inPocketDimension(ServerLevel level) {
        // Only the void grid dimension hosts pockets for now.
        return level.dimension().equals(PocketDimensions.VOID_SPACE);
    }

    /**
     * Re-arm the permanent ticket for every registered pocket cell in
     * {@code level}. Cheap: the registry is in-memory and the ticket call
     * is idempotent for already-armed positions.
     */
    public static void rearm(ServerLevel level) {
        if (!inPocketDimension(level)) {
            return;
        }
        PocketRegistry registry = PocketRegistry.getOrCreate(level.getServer());
        for (PocketInstance pocket : registry.cellsIn(PocketDimensions.VOID_SPACE).values()) {
            if (!pocket.materialized()) {
                continue;
            }
            PocketGrid.Cell cell = PocketGrid.parseKey(pocket.cellKey());
            if (cell == null) {
                continue;
            }
            net.minecraft.core.BlockPos center = PocketGrid.cellCenter(cell.x(), cell.z());
            level.getChunkSource().addRegionTicket(POCKET_TICKET, new ChunkPos(center),
                    TICKET_RADIUS, center);
        }
    }

    /**
     * Tick hook: re-arms tickets for the void grid every
     * {@link #REARM_CADENCE_TICKS} ticks.
     */
    public static final class RearmHook {
        private long nextRun = 0;

        @net.neoforged.bus.api.SubscribeEvent
        public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            long now = event.getServer().overworld().getGameTime();
            if (now < nextRun) {
                return;
            }
            nextRun = now + REARM_CADENCE_TICKS;
            ServerLevel voidSpace = event.getServer().getLevel(PocketDimensions.VOID_SPACE);
            if (voidSpace != null) {
                rearm(voidSpace);
            }
        }
    }
}
