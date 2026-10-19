package dev.foldedhorizons.core.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Game-event stub for the core mod's event emission surface.
 *
 * <p><strong>Step 12 wires the real game events here.</strong> Every hook
 * below is currently a no-op stub with the exact signature the event pass
 * will implement: it fires at the right place, with the right server
 * context, so the later pass only fills in the dispatch (custom
 * {@code GameEvent}/datapack event emission per
 * {@code docs/companion-mod/architecture.md} — core guarantees event
 * emission of {@code cell_allocated}, {@code conversion_merged},
 * {@code unstable_pocket_collapsed}, {@code trade_executed},
 * {@code shell_broken}).</p>
 */
public final class ModEvents {

    private ModEvents() {}

    /**
     * A Trade Post executed a barter trade (crate-based input→output leg,
     * build-order step 10).
     *
     * <p><strong>Step 12 wires the {@code trade_executed} game event
     * here.</strong> Called once per committed trade, server side only,
     * with the post's level and position as context.</p>
     */
    public static void tradeExecuted(ServerLevel level, BlockPos pos) {
        // no-op stub — step 12 wires the trade_executed game event here
    }
}
