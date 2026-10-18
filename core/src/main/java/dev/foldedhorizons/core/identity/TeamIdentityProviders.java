package dev.foldedhorizons.core.identity;

import java.util.Objects;

/**
 * Holder for the process-wide {@link TeamIdentityProvider}. Before any module
 * registers, the {@link BuiltInPartyRegistry} fallback answers.
 */
public final class TeamIdentityProviders {

    private static volatile TeamIdentityProvider provider =
            BuiltInPartyRegistry.INSTANCE;

    private TeamIdentityProviders() {}

    /** The active provider; never null. */
    public static TeamIdentityProvider get() {
        return provider;
    }

    /** Install a provider; null restores the built-in fallback. */
    public static void register(TeamIdentityProvider replacement) {
        provider = replacement == null ? BuiltInPartyRegistry.INSTANCE
                : Objects.requireNonNull(replacement);
    }
}
