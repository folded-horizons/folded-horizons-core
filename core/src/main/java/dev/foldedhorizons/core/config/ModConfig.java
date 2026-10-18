package dev.foldedhorizons.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side config for the companion mod's tuning knobs. Values here are
 * the single source of truth for pocket sizing and progression economy so
 * pack authors can rebalance without touching code.
 *
 * <p>TODO: load via modContainer.registerConfig(ModConfig.Type.SERVER, SPEC)
 * in the main {@link dev.foldedhorizons.core.FoldedHorizons}
 * constructor once numbers are settled.</p>
 */
public final class ModConfig {

    public static final ModConfigSpec SPEC;

    /** Interior size of a freshly allocated root cell (blocks per side). */
    public static final ModConfigSpec.IntValue POCKET_BASE_SIZE;

    /** Energy (FE) burned per breach into an UNSTABLE pocket. */
    public static final ModConfigSpec.LongValue BREACH_ENERGY_COST;

    /** Seconds in the Root Binder's grace window before a conversion applies. */
    public static final ModConfigSpec.IntValue GRACE_WINDOW_SECONDS;

    /**
     * Progression thresholds at which a root space expands: GT tier -> new
     * interior size. Compact-space unlocks key off the same ladder.
     */
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> EXPANSION_THRESHOLDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        POCKET_BASE_SIZE = builder
                .comment("Interior size (blocks per side) of a newly allocated root cell")
                .defineInRange("pocket.baseSize", 33, 9, 256);

        BREACH_ENERGY_COST = builder
                .comment("Energy burned when breaching into an unstable pocket")
                .defineInRange("pocket.breachEnergyCost", 10_000_000L, 0L, Long.MAX_VALUE);

        GRACE_WINDOW_SECONDS = builder
                .comment("Seconds of the Root Binder grace window before a conversion applies")
                .defineInRange("pocket.graceWindowSeconds", 30, 0, 600);

        EXPANSION_THRESHOLDS = builder
                .comment("Expansion ladder: 'gtTier:interiorSize' entries, ascending",
                        "e.g. \"EV:33\", \"IV:49\", \"LuV:65\"")
                .defineListAllowEmpty("pocket.expansionThresholds",
                        java.util.List.of("EV:33", "IV:49", "LuV:65"),
                        () -> true);

        SPEC = builder.build();
    }

    private ModConfig() {}
}
