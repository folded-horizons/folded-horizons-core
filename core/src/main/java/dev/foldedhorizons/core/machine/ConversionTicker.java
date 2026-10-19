package dev.foldedhorizons.core.machine;

import java.util.Map;
import java.util.UUID;
import dev.foldedhorizons.core.claim.ClaimAdapters;
import dev.foldedhorizons.core.dimension.Materializer;
import dev.foldedhorizons.core.dimension.PocketDimensions;
import dev.foldedhorizons.core.dimension.PocketInstance;
import dev.foldedhorizons.core.dimension.PocketRegistry;
import dev.foldedhorizons.core.dimension.PocketType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Applies expired Root Binder conversions (core step 6). Ticks on the
 * server thread every 10 ticks; pulls due entries with
 * {@link PocketRegistry#expiredConversions(long)} and mutates the registry,
 * then hands the transferred space to the claim adapter.
 *
 * <p>Action semantics (docs/machines.md):
 * <ul>
 *   <li><b>MERGE</b> — personal → team: the actor's ROOT re-keys to the
 *       target team UUID; interior and materialization are preserved; the
 *       claim transfers to the team. One-way.</li>
 *   <li><b>PURGE</b> — personal → team's space: the actor's personal cell
 *       is demolished (unregistered + unclaimed) and their ROOT address is
 *       re-pointed at the team's space, so the breacher lands them in the
 *       shared interior. Registry keeps one ROOT per owner.</li>
 *   <li><b>SPLIT</b> — team → personal: the leaving member (actor) gets a
 *       fresh personal ROOT allocated and materialized; the team root is
 *       untouched. The pending entry's target cell is the team root, so
 *       the breacher refused entry to it during the window.</li>
 * </ul></p>
 *
 * <p>Ordering is transactional-ish: registry mutations first (authoritative
 * index), claim transfer second — a claim failure leaves the registry
 * correct and the NoopClaimAdapter default keeps core-alone consistent.</p>
 */
@EventBusSubscriber(modid = dev.foldedhorizons.core.FoldedHorizons.MOD_ID)
public final class ConversionTicker {

    /** Tick cadence: apply due conversions every 10 ticks (0.5 s). */
    static final int CADENCE_TICKS = 10;

    private ConversionTicker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CADENCE_TICKS != 0) {
            return;
        }
        ServerLevel overworld = server.overworld();
        PocketRegistry registry = PocketRegistry.getOrCreate(server);
        long tick = overworld.getGameTime();
        ServerLevel voidLevel = PocketOps.voidSpace(server);
        if (voidLevel == null) {
            return; // datapack removed the grid dimension; nothing to mutate
        }
        for (Map.Entry<String, PocketRegistry.PendingConversion> entry
                : registry.expiredConversions(tick)) {
            apply(registry, voidLevel, entry.getKey(), entry.getValue());
        }
    }

    /** Apply one expired conversion. Registry first, claims second. */
    private static void apply(PocketRegistry registry, ServerLevel voidLevel,
            String cellKey, PocketRegistry.PendingConversion pending) {
        UUID actor = pending.actor();
        UUID target = pending.target();
        switch (pending.action()) {
            case MERGE -> applyMerge(registry, voidLevel, cellKey, actor, target);
            case PURGE -> applyPurge(registry, voidLevel, cellKey, actor, target);
            case SPLIT -> applySplit(registry, voidLevel, actor, target);
        }
    }

    /** MERGE: the actor's ROOT cell re-keys to the team owner. */
    private static void applyMerge(PocketRegistry registry, ServerLevel voidLevel,
            String cellKey, UUID actor, UUID team) {
        PocketInstance pocket = registry.get(PocketDimensions.VOID_SPACE, cellKey);
        if (pocket == null || !pocket.owner().equals(actor)) {
            return; // cell vanished mid-window; nothing to convert
        }
        PocketInstance merged = pocket.withOwner(team, PocketInstance.OwnerKind.TEAM);
        registry.put(voidLevel.dimension(), merged);
        registry.transferOwnership(actor, team, cellKey);
        ClaimAdapters.get().transfer(voidLevel, merged, actor);
    }

    /**
     * PURGE: demolish the actor's personal cell and re-address the actor
     * to the team's space. "Re-address" is achieved by deleting the actor's
     * ROOT record — the actor's entry then resolves through their team
     * identity (the breacher's team destination finds the team root), and
     * the registry keeps exactly one ROOT per owner.
     */
    private static void applyPurge(PocketRegistry registry, ServerLevel voidLevel,
            String cellKey, UUID actor, UUID team) {
        PocketInstance personal = registry.get(PocketDimensions.VOID_SPACE, cellKey);
        if (personal == null || !personal.owner().equals(actor)) {
            return;
        }
        if (PocketOps.findRoot(registry, team) == null) {
            return; // team root vanished mid-window; refuse to orphan the actor
        }
        // Demolish: drop the personal cell (interior blocks are left as
        // rubble in the void grid; the address no longer resolves) and
        // release its claim.
        registry.remove(voidLevel.dimension(), cellKey);
        ClaimAdapters.get().unclaim(voidLevel, personal);
    }

    /**
     * SPLIT: allocate + materialize a fresh personal ROOT for the leaving
     * member; the team root is untouched.
     */
    private static void applySplit(PocketRegistry registry, ServerLevel voidLevel,
            UUID actor, UUID team) {
        // The actor must still resolve to a team identity for the claim to
        // register; otherwise the fresh personal stays registry-only.
        PocketInstance fresh = registry.allocate(PocketDimensions.VOID_SPACE, actor,
                PocketInstance.OwnerKind.PLAYER, PocketType.ROOT, "",
                PocketOps.ALLOC_SCAN);
        if (fresh == null) {
            return; // grid exhausted; the member leaves with nothing
        }
        registry.markMaterialized(voidLevel.dimension(), fresh.cellKey());
        Materializer.materialize(registry, voidLevel, fresh);
    }
}
