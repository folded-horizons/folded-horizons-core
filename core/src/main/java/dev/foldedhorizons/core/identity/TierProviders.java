package dev.foldedhorizons.core.identity;

/**
 * Holder for the {@link TierProvider} SPI, mirroring
 * {@link TeamIdentityProviders}: the built-in default is in place from
 * server start; an integration module replaces it via
 * {@link #register}. Last registration wins.
 */
public final class TierProviders {

    private static volatile TierProvider provider = BuiltInTierProvider.INSTANCE;

    private TierProviders() {}

    /** The active provider. Never null. */
    public static TierProvider get() {
        return provider;
    }

    /** Replace the active provider (module registration path). */
    public static void register(TierProvider replacement) {
        if (replacement != null) {
            provider = replacement;
        }
    }
}
