package dev.foldedhorizons.core.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Resource keys for the mod's static dimensions. No dimension is created or
 * deleted at runtime — every pocket lives in one of these datapack-shipped
 * dimensions ({@code data/foldedhorizons/dimension/*.json}), so the mod only
 * ever holds keys and looks levels up on the server.
 *
 * <p>Exploit tiers are data-defined (config list {@code pocket.exploitDimensions},
 * default {@code exploit_t1..t3}); the key's tier number maps onto the
 * {@code exploit_t<tier>} member of that list. A pack may ship fewer (or no)
 * tier dimensions — a temp key for a missing tier refuses instead of crashing.</p>
 */
public final class PocketDimensions {

    /** The shared void grid dimension hosting the lobby, root and repeatable cells. */
    public static final ResourceKey<Level> VOID_SPACE = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("foldedhorizons", "void_space"));

    /**
     * The exploit-tier dimension for {@code tier} ({@code foldedhorizons:exploit_t<tier>}).
     * Datapack-shipped like void_space; a missing level (key resolves, level absent)
     * is a runtime refusal, not a crash.
     */
    public static ResourceKey<Level> exploitTier(int tier) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("foldedhorizons", "exploit_t" + tier));
    }

    private PocketDimensions() {}
}
