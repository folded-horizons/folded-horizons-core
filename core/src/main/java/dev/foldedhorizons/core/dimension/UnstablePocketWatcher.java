package dev.foldedhorizons.core.dimension;

import java.util.ArrayList;
import java.util.List;
import dev.foldedhorizons.core.claim.ClaimAdapters;
import dev.foldedhorizons.core.event.ModEvents;
import dev.foldedhorizons.core.machine.PocketOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Collapse-on-leave for UNSTABLE pockets (core step 9).
 *
 * <p>Every 20 ticks: for each materialized UNSTABLE cell in each registry
 * dimension, count players standing inside the cell's interior bounds
 * (33-wide square, full scan height — see {@link Materializer}). A cell
 * whose count reaches 0 is marked <em>dying</em> (persisted, crash-safety
 * point), then swept to air except the sky-block shell, its registry entry
 * deleted, and its claim released. The spent cell is never re-entered — the
 * team's per-tier cursor already points past it.</p>
 *
 * <p>Sweep exceptions (what survives the air-fill):
 * <ul>
 *   <li>{@code foldedhorizons:wall_shell} — the unbreakable sky-cube shell
 *     (walls-expansion.md); other wall blocks are swept, matching the docs'
 *     "leaving the sky-block shell".</li>
 *   <li>Any block still holding a block entity (chests etc.) — emptied
 *     inventories must not void their contents.</li>
 * </ul></p>
 *
 * <p>Crash-safe ordering: {@code dying} is set on the registry record
 * <em>before</em> the sweep and the entry is deleted only after the sweep
 * completes. If the server dies mid-sweep, the record survives with the
 * flag set and the sweep re-runs on startup ({@link #sweepDyingCells},
 * called from {@code dev.foldedhorizons.core.lifecycle.ServerLifecycle}).</p>
 */
@EventBusSubscriber(modid = dev.foldedhorizons.core.FoldedHorizons.MOD_ID)
public final class UnstablePocketWatcher {

    /** Tick cadence: occupancy check every 20 ticks (1 s). */
    static final int CADENCE_TICKS = 20;

    /** Default interior size, matching config {@code pocket.baseSize} (33); same lead as Materializer. */
    private static final int INTERIOR_SIZE = 33;

    /** Vertical extent of the sweep: matches Materializer's scan window. */
    private static final int SWEEP_MIN_Y = PocketGrid.CENTER_Y - 1;
    private static final int SWEEP_MAX_Y = PocketGrid.CENTER_Y + 16;

    /** The unbreakable shell block id (walls-expansion.md); kept through the sweep. */
    private static final String SHELL_BLOCK = "foldedhorizons:wall_shell";

    private UnstablePocketWatcher() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CADENCE_TICKS != 0) {
            return;
        }
        PocketRegistry registry = PocketRegistry.getOrCreate(server);
        for (ServerLevel level : server.getAllLevels()) {
            collapseEmptyCells(registry, level);
        }
    }

    /**
     * Collapse every materialized UNSTABLE cell of {@code level}'s dimension
     * whose interior holds no player. Cells already marked dying are swept
     * unconditionally (a previous sweep crashed mid-way).
     */
    private static void collapseEmptyCells(PocketRegistry registry, ServerLevel level) {
        for (PocketInstance pocket : List.copyOf(
                registry.unstableCells(level.dimension()).values())) {
            if (pocket.dying() || occupantsIn(registry, level, pocket) == 0) {
                collapse(registry, level, pocket);
            }
        }
    }

    /** Players inside the cell's interior bounds, server side only. */
    private static int occupantsIn(PocketRegistry registry, ServerLevel level,
            PocketInstance pocket) {
        PocketGrid.Cell cell = PocketGrid.parseKey(pocket.cellKey());
        if (cell == null) {
            return 1; // malformed cell key: never collapse on unreadable data
        }
        PocketGrid.Bounds bounds = PocketGrid.interiorBounds(
                cell.x(), cell.z(), INTERIOR_SIZE);
        int occupants = 0;
        for (ServerPlayer player : level.players()) {
            if (bounds.contains(player.getBlockX(), player.getBlockZ())) {
                occupants++;
            }
        }
        return occupants;
    }

    /**
     * One collapse: mark dying (persist), sweep interior to air except the
     * shell, delete the registry entry, release the claim. The dying flag
     * is written first and the entry removal last so a crash anywhere in
     * between leaves repairable state.
     */
    private static void collapse(PocketRegistry registry, ServerLevel level,
            PocketInstance pocket) {
        registry.markDying(pocket.dimension(), pocket.cellKey());
        sweepInterior(level, pocket);
        registry.remove(pocket.dimension(), pocket.cellKey());
        ClaimAdapters.get().unclaim(level, pocket);
        // unstable_pocket_collapsed: sweep + removal complete (build-order 12).
        ModEvents.unstablePocketCollapsed(level, pocket.cellKey(), pocket.owner());
    }

    /** Air-fill of the interior bounds except shell blocks and container blocks. */
    private static void sweepInterior(ServerLevel level, PocketInstance pocket) {
        PocketGrid.Cell cell = PocketGrid.parseKey(pocket.cellKey());
        if (cell == null) {
            return;
        }
        PocketGrid.Bounds bounds = PocketGrid.interiorBounds(
                cell.x(), cell.z(), INTERIOR_SIZE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = SWEEP_MIN_Y; y <= SWEEP_MAX_Y; y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || isShell(state) || hasBlockEntity(level, pos, state)) {
                        continue;
                    }
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static boolean isShell(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return SHELL_BLOCK.equals(id.toString());
    }

    private static boolean hasBlockEntity(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null;
    }

    /**
     * Startup recovery: re-sweep every UNSTABLE cell still marked dying
     * (a crash interrupted its sweep before the registry entry was deleted).
     * Called from {@code ServerLifecycle#onServerStarted}.
     */
    public static void sweepDyingCells(MinecraftServer server, PocketRegistry registry) {
        for (ServerLevel level : server.getAllLevels()) {
            for (PocketInstance pocket : List.copyOf(
                    registry.unstableCells(level.dimension()).values())) {
                if (pocket.dying()) {
                    collapse(registry, level, pocket);
                }
            }
        }
    }
}
