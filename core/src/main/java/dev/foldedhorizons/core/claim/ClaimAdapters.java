package dev.foldedhorizons.core.claim;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;

/**
 * Holder for the process-wide {@link ClaimAdapter}. Core code goes through
 * {@link #get()}, which never returns null: before any module registers, the
 * {@link NoopClaimAdapter} answers.
 */
public final class ClaimAdapters {

    private static volatile ClaimAdapter adapter = NoopClaimAdapter.INSTANCE;

    private ClaimAdapters() {}

    /** The active adapter; never null. */
    public static ClaimAdapter get() {
        return adapter;
    }

    /** Install an adapter; null falls back to the no-op default. */
    public static void register(ClaimAdapter replacement) {
        adapter = replacement == null ? NoopClaimAdapter.INSTANCE
                : Objects.requireNonNull(replacement);
    }
}
