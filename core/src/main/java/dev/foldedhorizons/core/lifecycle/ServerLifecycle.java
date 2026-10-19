package dev.foldedhorizons.core.lifecycle;

import dev.foldedhorizons.core.claim.ClaimAdapters;
import dev.foldedhorizons.core.dimension.Materializer;
import dev.foldedhorizons.core.dimension.PocketDimensions;
import dev.foldedhorizons.core.dimension.PocketInstance;
import dev.foldedhorizons.core.dimension.PocketRegistry;
import dev.foldedhorizons.core.dimension.PocketType;
import dev.foldedhorizons.core.dimension.UnstablePocketWatcher;
import dev.foldedhorizons.core.identity.BuiltInPartyRegistry;
import dev.foldedhorizons.core.identity.TeamIdentityProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Server lifecycle wiring for the materialization path.
 *
 * <p>On {@code ServerStarted}: materialize the lobby cell (once — the
 * region-is-empty guard makes this idempotent across restarts), then
 * materialize every allocated-but-unmaterialized ROOT pocket in
 * {@code foldedhorizons:void_space} (startup recovery: an allocation
 * recorded before a crash re-places its template here).</p>
 */
@EventBusSubscriber(modid = dev.foldedhorizons.core.FoldedHorizons.MOD_ID)
public final class ServerLifecycle {

    private ServerLifecycle() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel voidSpace = server.getLevel(PocketDimensions.VOID_SPACE);
        if (voidSpace == null) {
            return; // dimension removed by a datapack — nothing to materialize
        }
        PocketRegistry registry = PocketRegistry.getOrCreate(server);
        // Activate the built-in party fallback against its persisted SavedData
        // (solo players resolve without it; party members need the loaded map).
        TeamIdentityProvider.register(BuiltInPartyRegistry.getOrCreate(server));
        Materializer.materializeLobby(voidSpace);
        UnstablePocketWatcher.sweepDyingCells(server, registry);
        for (PocketInstance pocket : registry.cellsIn(PocketDimensions.VOID_SPACE).values()) {
            if (pocket.type() == PocketType.ROOT && !pocket.materialized()) {
                Materializer.materialize(registry, voidSpace, pocket);
            }
        }
        // Flush any claim registrations the adapter queued during startup.
        ClaimAdapters.get();
    }
}
