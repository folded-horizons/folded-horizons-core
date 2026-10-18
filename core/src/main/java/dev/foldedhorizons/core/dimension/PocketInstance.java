package dev.foldedhorizons.core.dimension;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One live (or historically recorded) pocket: an address in the grid
 * architecture. Immutable snapshot — mutations (ownership transfer via the
 * Root Binder, exhaustion of an unstable pocket, expansion of a root space)
 * produce a new record and are written through {@link PocketRegistry}.
 *
 * @param type        which pocket classification this instance belongs to
 * @param dimension   the static dimension hosting the grid (void_space, exploit_t1, ...)
 * @param cellKey     "x,z" grid key; cell centers sit 2048 blocks apart
 * @param owner       player or team UUID — the registry key for ownership
 * @param ownerKind   whether {@code owner} is a player UUID or an FTB team UUID
 * @param materialized whether the structure template has been placed yet
 * @param returnPoint the overworld (or source) position the breach frame
 *                    teleports back to; null while unset
 * @param createdAt   epoch millis when the pocket was allocated
 */
public record PocketInstance(
        PocketType type,
        ResourceKey<Level> dimension,
        String cellKey,
        UUID owner,
        OwnerKind ownerKind,
        boolean materialized,
        BlockPos returnPoint,
        long createdAt
) {

    /** Whether the owner UUID denotes a player or an FTB team. */
    public enum OwnerKind {
        PLAYER,
        TEAM
    }
}
