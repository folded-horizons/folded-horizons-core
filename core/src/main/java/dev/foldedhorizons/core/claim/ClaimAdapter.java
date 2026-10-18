package dev.foldedhorizons.core.claim;

import java.util.UUID;

/**
 * SPI: claim adapter over an external claim system. Core never imports a
 * claim mod — the foldedhorizons-ftb module plugs in an implementation backed
 * by FTB Chunks (register on materialization, re-register on Root Binder
 * ownership transfer, drop on purge/collapse). Core ships this interface plus
 * a {@link NoopClaimAdapter no-op default}, so a core-alone pack runs with
 * claims simply absent.
 *
 * <p>Modules register their adapter via {@link #register} during mod
 * construction. Exactly the adapter semantics the docs name: claim on
 * materialization, re-register on conversion (merge/purge/split re-registers
 * around the surviving owner), unclaim on collapse/purge.</p>
 */
public interface ClaimAdapter {

    /**
     * Register the claim covering {@code pocket}'s interior for
     * {@code pocket.owner()}. Called after materialization and re-called after
     * an ownership transfer; implementations must treat a re-register of an
     * already-claimed cell as an update, not an error.
     */
    void claim(net.minecraft.server.level.ServerLevel level,
            dev.foldedhorizons.core.dimension.PocketInstance pocket);

    /**
     * Release the claim covering {@code pocket}'s interior (collapse, purge,
     * grid teardown). Called with the owner recorded on the instance; a
     * no-claim / already-released cell must be tolerated.
     */
    void unclaim(net.minecraft.server.level.ServerLevel level,
            dev.foldedhorizons.core.dimension.PocketInstance pocket);

    /**
     * Re-point an existing claim after a Root Binder ownership transfer:
     * implementations should move the claim on {@code cellKey} from
     * {@code oldOwner} to {@code newOwner} without dropping protection in
     * between. Default: unclaim + claim (safe for stateless adapters).
     */
    default void transfer(net.minecraft.server.level.ServerLevel level,
            dev.foldedhorizons.core.dimension.PocketInstance pocket,
            UUID oldOwner) {
        unclaim(level, pocket);
        claim(level, pocket);
    }

    /** Accessor for the currently registered adapter (never null). */
    static ClaimAdapter get() {
        return ClaimAdapters.get();
    }

    /**
     * Install an adapter (foldedhorizons-ftb does this at construction).
     * Registering a second adapter replaces the first — one claim system per
     * pack. Null is rejected silently in favor of the no-op default.
     */
    static void register(ClaimAdapter adapter) {
        ClaimAdapters.register(adapter);
    }
}
