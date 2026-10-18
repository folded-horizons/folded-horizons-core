package dev.foldedhorizons.core.claim;

/**
 * The core default {@link ClaimAdapter}: nothing. A core-alone pack has no
 * claim system, so pocket interiors are protected only by the wall shell
 * blocks. The foldedhorizons-ftb module replaces this instance at mod
 * construction via {@link ClaimAdapter#register}.
 */
public final class NoopClaimAdapter implements ClaimAdapter {

    /** Shared instance; stateless, so one is enough. */
    public static final NoopClaimAdapter INSTANCE = new NoopClaimAdapter();

    @Override
    public void claim(net.minecraft.server.level.ServerLevel level,
            dev.foldedhorizons.core.dimension.PocketInstance pocket) {
        // no claim system in core
    }

    @Override
    public void unclaim(net.minecraft.server.level.ServerLevel level,
            dev.foldedhorizons.core.dimension.PocketInstance pocket) {
        // no claim system in core
    }
}
